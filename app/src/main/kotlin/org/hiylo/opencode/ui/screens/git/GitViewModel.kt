/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : GitViewModel.kt
 * Date : 2026/09/07 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.screens.git

import android.content.Context
import org.hiylo.opencode.logging.AppLogger as Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.opencode.R
import org.hiylo.opencode.data.api.OpenCodeApi
import org.hiylo.opencode.data.api.PtySocket
import org.hiylo.opencode.data.api.ServerConnection
import org.hiylo.opencode.data.api.SuggestionProvider
import org.hiylo.opencode.data.repository.SettingsRepository
import org.hiylo.opencode.data.sync.LocalSyncSecretStore
import org.hiylo.opencode.ml.MnnLlm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject

private const val TAG = "GitViewModel"

/** Git 仓库在仓库选择器中的种类。 */
enum class GitRepoKind { ROOT, SUBMODULE, NESTED }

/** 一个可操作的 Git 仓库（顶层 worktree 或嵌套/子模块仓库）。 */
data class GitRepo(
    val path: String,
    val label: String,
    val kind: GitRepoKind,
)

/** 单个文件变更：路径、增删行数与状态。 */
data class GitChange(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val status: String, // added / modified / deleted / untracked
)

/** 一条提交记录。 */
data class GitCommit(
    val hash: String,
    val author: String,
    val date: String,
    val message: String,
)

/** 单个文件的 diff 视图内容（原始 unified diff 文本）。 */
data class GitFileDiff(
    val path: String,
    val content: String,
    val additions: Int,
    val deletions: Int,
)

/** Git 页面的 UI 状态。 */
data class GitUiState(
    val directory: String = "",
    val repos: List<GitRepo> = emptyList(),
    val selectedRepo: String = "",
    val branch: String? = null,
    val branches: List<String> = emptyList(),
    val isClean: Boolean = true,
    val changes: List<GitChange> = emptyList(),
    val commits: List<GitCommit> = emptyList(),
    val selectedDiff: GitFileDiff? = null,
    val selectedCommit: GitCommit? = null,
    val commitChanges: List<GitChange> = emptyList(),
    val commitDiff: String = "",
    val isLoadingCommit: Boolean = false,
    val remotes: List<String> = emptyList(),
    val notRepository: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRunning: Boolean = false,
    val operationMessage: String? = null,
)

/**
 * Git 页面 ViewModel。
 *
 * 复用终端 PTY 机制：为每条 git 命令创建一个临时 PTY，发送非交互命令并通过
 * begin/end 标记收集输出，随后关闭并移除该 PTY。所有命令显式 `-C <repoRoot>`，
 * 关闭分页与交互提示，避免阻塞。
 */
@HiltViewModel
class GitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    @ApplicationContext private val context: Context,
    private val suggestionProvider: SuggestionProvider,
    private val settingsRepository: SettingsRepository,
    private val secretStore: LocalSyncSecretStore,
) : ViewModel() {

    private val conn = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifEmpty { null },
    )
    private val directory = savedStateHandle.get<String>("directory").orEmpty()

    private val _uiState = MutableStateFlow(GitUiState(directory = directory))
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    private val _generatingMessage = MutableStateFlow(false)
    val generatingMessage: StateFlow<Boolean> = _generatingMessage.asStateFlow()

    private val _generatedMessage = MutableStateFlow<String?>(null)
    val generatedMessage: StateFlow<String?> = _generatedMessage.asStateFlow()

    private val _generateError = MutableStateFlow<String?>(null)
    val generateError: StateFlow<String?> = _generateError.asStateFlow()

    /** 常驻 PTY 会话：复用单一终端连接执行所有 git 命令，避免每次新建 PTY 的 shell 启动开销。 */
    private val ptySession by lazy { GitPtySession(api, conn, directory, viewModelScope) }

    override fun onCleared() {
        ptySession.close()
        super.onCleared()
    }

    init {
        refresh()
    }

    /** 重新加载当前选中仓库的完整 Git 状态。 */
    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { refreshInternal() }
        }
    }

    private suspend fun refreshInternal() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val root = resolveDirectory()
            if (root.isBlank()) {
                _uiState.update { it.copy(notRepository = true, isLoading = false) }
                return
            }

            val data = loadReadData(root)
            if (data == null) {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.git_load_failed)) }
                return
            }

            if (data.topLevel.isNotBlank()) {
                // 目录本身是 git 仓库：正常展示，并后台补充子仓库。
                val repos = detectReposFromData(root, data.submodules)
                val effectiveRepo = _uiState.value.selectedRepo.ifBlank { root }
                    .let { sel -> repos.firstOrNull { it.path == sel }?.path ?: root }
                _uiState.update {
                    it.copy(
                        repos = repos,
                        selectedRepo = effectiveRepo,
                        directory = data.topLevel,
                        branch = data.branch,
                        branches = data.branches,
                        remotes = data.remotes,
                        isClean = data.changes.isEmpty(),
                        changes = data.changes,
                        commits = data.commits,
                        selectedDiff = null,
                        notRepository = false,
                        isLoading = false,
                        error = null,
                    )
                }
                detectNestedReposAsync(root)
            } else {
                // 目录本身不是 git 仓库，但可能包含多个仓库：查找并让用户选择。
                val nested = findNestedRepos(root)
                if (nested.isEmpty()) {
                    _uiState.update { it.copy(notRepository = true, isLoading = false, error = null) }
                    return
                }
                val repos = nested.map { GitRepo(it, labelFor(it, root.trimEnd('/')), GitRepoKind.NESTED) }
                val first = repos.first().path
                val firstData = loadReadData(first)
                if (firstData == null) {
                    _uiState.update {
                        it.copy(repos = repos, selectedRepo = first, isLoading = false, error = context.getString(R.string.git_load_failed))
                    }
                    return
                }
                _uiState.update {
                    it.copy(
                        repos = repos,
                        selectedRepo = first,
                        directory = firstData.topLevel.ifBlank { first },
                        branch = firstData.branch,
                        branches = firstData.branches,
                        remotes = firstData.remotes,
                        isClean = firstData.changes.isEmpty(),
                        changes = firstData.changes,
                        commits = firstData.commits,
                        selectedDiff = null,
                        notRepository = false,
                        isLoading = false,
                        error = null,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh git state", e)
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private suspend fun resolveDirectory(): String {
        val configured = directory.trim().trimEnd('/')
        if (configured.isNotBlank()) return configured
        return runCatching { api.getCurrentProject(conn).worktree.ifBlank { null } }.getOrNull()
            ?: runCatching { api.listProjects(conn).firstOrNull()?.worktree.orEmpty() }.getOrNull()
            ?: ""
    }

    /** 批量只读数据的解析结果。 */
    private data class GitReadData(
        val topLevel: String,
        val branch: String?,
        val branches: List<String>,
        val remotes: List<String>,
        val changes: List<GitChange>,
        val commits: List<GitCommit>,
        val submodules: String,
    )

    /** 在一个临时 PTY 中一次性执行所有只读 git 命令并解析，返回结构化数据；失败返回 null。 */
    private suspend fun loadReadData(root: String): GitReadData? {
        val id = UUID.randomUUID().toString().replace("-", "")
        val end = "__OPENGIT_END_${id}__"
        val raw = executeScript(gitReadScript(root) + "printf '\\n$end\\n'\n", end)
        if (raw.isBlank()) return null
        val topLevel = section(raw, "TOPLEVEL")
        val branch = section(raw, "BRANCH").trim().ifBlank { null }
        val branches = parseBranches(section(raw, "BRANCHES"))
        val remotes = section(raw, "REMOTES").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val changes = parseChanges(section(raw, "STATUS"))
        val numstat = parseNumstat(section(raw, "NUMSTAT"))
        val mergedChanges = changes.map { change ->
            val counts = numstat[change.path]
            change.copy(
                additions = counts?.first ?: change.additions,
                deletions = counts?.second ?: change.deletions,
            )
        }
        val commits = parseCommits(section(raw, "LOG"))
        val submodules = section(raw, "SUBMODULES")
        return GitReadData(topLevel, branch, branches, remotes, mergedChanges, commits, submodules)
    }

    /** 选择仓库选择器中的另一个仓库。 */
    fun selectRepo(path: String) {
        if (path == _uiState.value.selectedRepo) return
        _uiState.update { it.copy(selectedRepo = path) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val data = loadReadData(path) ?: return@runCatching
                    _uiState.update {
                        it.copy(
                            branch = data.branch,
                            branches = data.branches,
                            remotes = data.remotes,
                            isClean = data.changes.isEmpty(),
                            changes = data.changes,
                            commits = data.commits,
                            selectedDiff = null,
                            isLoading = false,
                            error = null,
                        )
                    }
                }.onFailure { e ->
                    Log.e(TAG, "Failed to load repo $path", e)
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    /** 加载单个文件的 diff；未跟踪文件展示其全部内容（作为新增）。 */
    fun loadDiff(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.update { it.copy(isLoading = true, selectedCommit = null, commitDiff = "") }
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                val change = _uiState.value.changes.firstOrNull { it.path == path }
                val content = runCatching {
                    if (change?.status == "untracked") {
                        runCommand(gitCmd(root, "diff --no-index -- /dev/null ${shQuote(path)}"))
                    } else {
                        val head = runCommand(gitCmd(root, "diff HEAD -- ${shQuote(path)}"))
                        if (head.isNotBlank()) head
                        else runCommand(gitCmd(root, "diff --cached -- ${shQuote(path)}"))
                    }
                }.getOrElse { "" }
                _uiState.update {
                    it.copy(
                        selectedDiff = GitFileDiff(
                            path = path,
                            content = content,
                            additions = change?.additions ?: 0,
                            deletions = change?.deletions ?: 0,
                        ),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** 加载某次提交涉及的文件变更与完整 diff；再次点击同一提交则折叠。 */
    fun loadCommitDetail(commit: GitCommit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val root = _uiState.value.selectedRepo.ifBlank { return@withContext }
                if (_uiState.value.selectedCommit?.hash == commit.hash) {
                    _uiState.update { it.copy(selectedCommit = null, commitChanges = emptyList(), commitDiff = "", isLoadingCommit = false) }
                    return@withContext
                }
                _uiState.update { it.copy(selectedCommit = commit, isLoadingCommit = true, commitChanges = emptyList(), commitDiff = "", selectedDiff = null) }

                // 单 PTY 批量执行 diff-tree + show，避免两次 shell 启动开销。
                val id = UUID.randomUUID().toString().replace("-", "")
                val end = "__OPENGIT_END_${id}__"
                val g = "git -c color.ui=false --no-pager -C ${shQuote(root)}"
                val script = buildString {
                    append("printf '\\n__GIT_NAMESTATUS__\\n'\n")
                    append("$g diff-tree --no-commit-id --name-status -r ${commit.hash}\n")
                    append("printf '\\n__GIT_SHOW__\\n'\n")
                    append("$g show --stat --format= ${shQuote(commit.hash)}\n")
                    append("printf '\\n$end\\n'\n")
                }
                val raw = runCatching { executeScript(script, end) }.getOrDefault("")
                val changes = parseNameStatus(section(raw, "NAMESTATUS"))
                val diff = section(raw, "SHOW")
                _uiState.update { it.copy(commitChanges = changes, commitDiff = diff, isLoadingCommit = false) }
            }
        }
    }

    /** 提交全部变更（`git add -A` + `git commit -m`）。 */
    fun commit(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        runOperation("commit") {
            val root = _uiState.value.selectedRepo
            runCommand(gitCmd(root, "add -A"))
            runCommand(gitCmd(root, "-c core.editor=true commit -m ${shQuote(trimmed)}"))
        }
    }

    /** 推送到指定 remote 的当前分支（`git push <remote> <branch>`）。 */
    fun push(remote: String) = runOperation("push") {
        val root = _uiState.value.selectedRepo
        val target = _uiState.value.branch?.takeIf { it.isNotBlank() } ?: "HEAD"
        runCommand(gitCmd(root, "push ${shQuote(remote)} ${shQuote(target)}"))
    }

    /** 从指定 remote 拉取当前分支（`git pull --rebase <remote> <branch>`）。 */
    fun pull(remote: String) = runOperation("pull") {
        val root = _uiState.value.selectedRepo
        val target = _uiState.value.branch?.takeIf { it.isNotBlank() } ?: "HEAD"
        runCommand(gitCmd(root, "pull --rebase ${shQuote(remote)} ${shQuote(target)}"))
    }

    /** 切换到已有分支（`git checkout <branch>`）。 */
    fun checkout(branch: String) = runOperation("checkout") {
        runCommand(gitCmd(_uiState.value.selectedRepo, "checkout ${shQuote(branch)}"))
    }

    /** 创建并切换到新分支（`git checkout -b <branch>`）。 */
    fun createBranch(branch: String) {
        val name = branch.trim()
        if (name.isEmpty()) return
        runOperation("createBranch") {
            runCommand(gitCmd(_uiState.value.selectedRepo, "checkout -b ${shQuote(name)}"))
        }
    }

    /** 清空操作结果提示。 */
    fun clearOperationMessage() {
        _uiState.update { it.copy(operationMessage = null) }
    }

    /** 清空已生成的提交信息。 */
    fun clearGeneratedMessage() {
        _generatedMessage.value = null
        _generateError.value = null
    }

    /**
     * 根据本次变更与最近提交的风格生成 commit message。
     * 优先使用「生成建议」里配置的云端 LLM，失败或未配置时回退端侧模型。
     */
    fun generateCommitMessage() {
        if (_generatingMessage.value) return
        viewModelScope.launch {
            _generatingMessage.value = true
            _generatedMessage.value = null
            _generateError.value = null
            try {
                val changes = _uiState.value.changes
                val recent = _uiState.value.commits.take(10)
                val prompt = buildCommitMessagePrompt(changes, recent)

                var message: String? = null
                var onDeviceAvailable = false
                val baseUrl = settingsRepository.llmProviderBaseUrl.first()
                val model = settingsRepository.llmProviderModel.first()
                if (baseUrl.isNotBlank() && model.isNotBlank()) {
                    val apiKey = secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).orEmpty()
                    message = runCatching {
                        suggestionProvider.chat(
                            SuggestionProvider.Config(baseUrl = baseUrl, apiKey = apiKey, model = model),
                            prompt,
                        ).trim().takeIf { it.isNotBlank() }
                    }.getOrNull()
                }

                if (message == null) {
                    onDeviceAvailable = MnnLlm.ensureLoaded(context)
                    if (onDeviceAvailable) {
                        MnnLlm.reset()
                        message = MnnLlm.generate(prompt, maxTokens = 128).trim().takeIf { it.isNotBlank() }
                    }
                }
                _generatedMessage.value = message
                _generateError.value = when {
                    message != null -> null
                    !onDeviceAvailable && (baseUrl.isBlank() || model.isBlank()) ->
                        context.getString(R.string.git_generate_no_model)
                    else -> context.getString(R.string.git_generate_failed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "commit message generation failed", e)
                _generateError.value = context.getString(R.string.git_generate_failed)
            } finally {
                _generatingMessage.value = false
            }
        }
    }

    /** 构造 commit message 生成的提示词：包含最近提交风格与本次变更摘要。 */
    private fun buildCommitMessagePrompt(changes: List<GitChange>, recent: List<GitCommit>): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        val changeDesc = changes.joinToString("\n") { c ->
            "${c.status} ${c.path} (+${c.additions}/-${c.deletions})"
        }.ifBlank { "(no changes)" }
        val recentDesc = recent.joinToString("\n") { it.message }.ifBlank { "(none)" }
        return if (isZh) {
            "根据下面的代码变更和最近提交的风格，生成一条简洁的 commit message（遵循最近提交的格式习惯）。\n" +
                "只输出 commit message 本身，不要解释、不要加引号、不要 markdown。\n\n" +
                "最近提交风格：\n$recentDesc\n\n" +
                "本次变更：\n$changeDesc"
        } else {
            "Generate a concise commit message based on the changes below and the style of recent commits.\n" +
                "Output ONLY the commit message itself — no explanation, no quotes, no markdown.\n\n" +
                "Recent commit style:\n$recentDesc\n\n" +
                "Changes:\n$changeDesc"
        }
    }

    private fun runOperation(name: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.update { it.copy(isRunning = true, error = null) }
                try {
                    block()
                    _uiState.update { it.copy(isRunning = false, operationMessage = "success") }
                    refreshInternal()
                } catch (e: Exception) {
                    Log.e(TAG, "Git operation $name failed", e)
                    _uiState.update { it.copy(isRunning = false, operationMessage = "failed", error = e.message) }
                }
            }
        }
    }

    // ============ 仓库检测 ============

    /** 从批量读取的 submodule 输出构建仓库选择器（根 + 子模块）。 */
    private fun detectReposFromData(root: String, submodulesOutput: String): List<GitRepo> {
        val result = linkedMapOf<String, GitRepo>()
        val normalizedRoot = root.trimEnd('/')
        result[normalizedRoot] = GitRepo(normalizedRoot, labelFor(normalizedRoot, normalizedRoot), GitRepoKind.ROOT)

        submodulesOutput.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val relPath = parts[1]
                    val abs = (normalizedRoot + "/" + relPath.trimStart('/')).trimEnd('/')
                    result.getOrPut(abs) {
                        GitRepo(abs, labelFor(abs, normalizedRoot), GitRepoKind.SUBMODULE)
                    }
                }
            }
        return result.values.toList()
    }

    /** 查找 [root] 目录下包含的 git 仓库（非子模块），返回绝对路径列表。 */
    private suspend fun findNestedRepos(root: String): List<String> {
        return runCatching {
            runCommand("find ${shQuote(root)} -mindepth 2 -name .git -not -path '*/.git/*'")
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { gitPath ->
                    val abs = gitPath.trimEnd('/').substringBeforeLast('/').trimEnd('/')
                    abs.takeIf { it.isNotBlank() && it != root.trimEnd('/') }
                }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 后台查找嵌套仓库（非子模块），不阻塞首屏展示。 */
    private fun detectNestedReposAsync(root: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                findNestedRepos(root).forEach { abs ->
                    if (_uiState.value.repos.none { it.path == abs }) {
                        _uiState.update { st ->
                            st.copy(repos = st.repos + GitRepo(abs, labelFor(abs, root.trimEnd('/')), GitRepoKind.NESTED))
                        }
                    }
                }
            }
        }
    }

    private fun labelFor(path: String, root: String): String {
        val normalized = path.trimEnd('/')
        if (normalized == root.trimEnd('/')) {
            return normalized.substringAfterLast('/').ifBlank { normalized }
        }
        val relative = normalized.removePrefix(root.trimEnd('/')).trimStart('/')
        return relative.ifBlank { normalized.substringAfterLast('/') }
    }

    // ============ 输出解析 ============

    private fun parseBranches(output: String): List<String> = output
        .lineSequence()
        .map { it.trim().removePrefix("*").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    private fun parseChanges(output: String): List<GitChange> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            if (line.length < 2) return@mapNotNull null
            val xy = line.substring(0, 2)
            var path = line.substring(2).trim()
            if (path.contains(" -> ")) path = path.substringAfterLast(" -> ")
            val status = when {
                xy == "??" -> "untracked"
                xy[0] == 'D' || xy[1] == 'D' -> "deleted"
                xy[0] == 'A' || xy[1] == 'A' -> "added"
                else -> "modified"
            }
            GitChange(path = path, additions = 0, deletions = 0, status = status)
        }
        .toList()

    private fun parseNameStatus(output: String): List<GitChange> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val status = when (parts[0].trim().firstOrNull()) {
                'A' -> "added"
                'D' -> "deleted"
                'R' -> "modified"
                'T' -> "modified"
                else -> "modified"
            }
            val path = if (parts.size >= 3) parts[2] else parts[1]
            GitChange(path = path, additions = 0, deletions = 0, status = status)
        }
        .toList()

    private fun parseNumstat(output: String): Map<String, Pair<Int, Int>> = output
        .lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val added = parts[0].toIntOrNull() ?: 0
            val deleted = parts[1].toIntOrNull() ?: 0
            parts[2] to (added to deleted)
        }
        .toMap()

    private fun parseCommits(output: String): List<GitCommit> = output
        .split('\n')
        .mapNotNull { line ->
            val parts = line.split('\u001f')
            if (parts.size < 4) return@mapNotNull null
            GitCommit(
                hash = parts[0],
                author = parts[1],
                date = parts[2],
                message = parts[3],
            )
        }
        .toList()

    // ============ PTY 命令执行 ============

    private fun gitCmd(root: String, args: String): String =
        "git -c color.ui=false --no-pager -C ${shQuote(root)} $args"

    /**
     * 生成单次批量读取仓库只读数据的 shell 脚本，各段用 `__GIT_<NAME>__` 标记分隔，
     * 一次性在单个 PTY 中执行，避免逐命令新建 PTY 带来的慢与不稳定。
     */
    private fun gitReadScript(root: String): String {
        val q = shQuote(root)
        val g = "git -c color.ui=false --no-pager -C $q"
        return buildString {
            append("printf '\\n__GIT_TOPLEVEL__\\n'\n"); append("$g rev-parse --show-toplevel\n")
            append("printf '\\n__GIT_BRANCH__\\n'\n"); append("$g rev-parse --abbrev-ref HEAD\n")
            append("printf '\\n__GIT_STATUS__\\n'\n"); append("$g status --porcelain=v1\n")
            append("printf '\\n__GIT_NUMSTAT__\\n'\n"); append("$g diff --numstat\n")
            append("printf '\\n__GIT_LOG__\\n'\n"); append("$g log -20 --pretty=format:%H%x1f%an%x1f%ad%x1f%s --date=short\n")
            append("printf '\\n__GIT_BRANCHES__\\n'\n"); append("$g branch\n")
            append("printf '\\n__GIT_REMOTES__\\n'\n"); append("$g remote\n")
            append("printf '\\n__GIT_SUBMODULES__\\n'\n"); append("$g submodule status --recursive\n")
        }
    }

    /** 从批量输出中提取指定段落（`__GIT_<NAME>__` 与其后一个标记之间）。 */
    private fun section(raw: String, name: String): String {
        val marker = "__GIT_${name}__"
        val lines = cleanTerminal(raw).lines()
        val start = lines.indexOf(marker)
        if (start == -1) return ""
        val endIdx = lines.subList(start + 1, lines.size).indexOfFirst { it.startsWith("__GIT_") }
        val end = if (endIdx == -1) lines.size else start + 1 + endIdx
        return lines.subList(start + 1, end).joinToString("\n").trim()
    }

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun cleanTerminal(raw: String): String = raw
        .replace(ANSI_OSC, "")
        .replace(ANSI_CSI, "")
        .replace("\u001B", "")
        .replace("\r", "")

    private fun extract(begin: String, end: String, raw: String): String {
        val lines = cleanTerminal(raw).lines()
        val beginIdx = lines.indexOf(begin)
        val endIdx = lines.indexOf(end)
        if (beginIdx == -1 || endIdx == -1 || endIdx <= beginIdx) return ""
        return lines.subList(beginIdx + 1, endIdx).joinToString("\n").trim()
    }

    private suspend fun runCommand(command: String, timeoutMs: Long = 30_000): String {
        var result = runCommandOnce(command, timeoutMs)
        if (result.isBlank()) {
            // PTY 偶发空输出（shell 尚未就绪/标记丢失），重试一次提升成功率。
            result = runCommandOnce(command, timeoutMs)
        }
        return result
    }

    /** 通过常驻 PTY 执行一条 shell 命令，返回 begin/end 标记之间的输出。 */
    private suspend fun runCommandOnce(command: String, timeoutMs: Long = 30_000): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val begin = "OPENGIT_B_$id"
        val end = "OPENGIT_E_$id"
        val script = "printf '$begin\\n'\n$command 2>&1\nprintf '\\n$end\\n'\n"
        val raw = ptySession.run(script, end, timeoutMs)
        return extract(begin, end, raw)
    }

    /** 通过常驻 PTY 执行多行脚本，读取到 [endMarker] 后返回原始输出。 */
    private suspend fun executeScript(script: String, endMarker: String, timeoutMs: Long = 60_000): String {
        return ptySession.run(script, endMarker, timeoutMs)
    }

    private companion object {
        val ANSI_CSI = Regex("\u001B\\[[0-9;?]*[A-Za-z]")
        val ANSI_OSC = Regex("\u001B\\][^\u001B\u0007]*(?:\u0007|\u001B\\\\)")
    }
}

/**
 * 常驻 PTY 会话：为 Git 页复用单个终端连接，所有命令串行发送并读取到唯一标记，
 * 避免每条命令都新建 PTY（每次都要等远端 shell 启动，开销大）。
 */
private class GitPtySession(
    private val api: OpenCodeApi,
    private val conn: ServerConnection,
    private val directory: String,
    private val scope: CoroutineScope,
) {
    private var socket: PtySocket? = null
    private var ptyId: String? = null
    private var readerJob: Job? = null
    private val lock = Any()
    private val mutex = Mutex()
    private val buffer = StringBuilder()
    private val tail = StringBuilder()
    private var activeMarker: String? = null
    private var activeDeferred: CompletableDeferred<Unit>? = null
    private var connected = false

    private suspend fun connect() {
        if (connected) return
        val dirArg = directory.takeIf { it.isNotBlank() }
        val pty = api.createPty(conn, title = "git", cwd = dirArg, directory = dirArg)
        ptyId = pty.id
        val sock = api.openPtySocket(conn, pty.id, cursor = 0, directory = dirArg)
        socket = sock
        runCatching { api.updatePtySize(conn, pty.id, cols = 240, rows = 40, directory = dirArg) }

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                sock.readLoop { chunk ->
                    synchronized(lock) {
                        buffer.append(chunk)
                        tail.append(chunk)
                        if (tail.length > 4000) tail.delete(0, tail.length - 2000)
                        val m = activeMarker
                        if (m != null && tail.contains(m)) {
                            activeDeferred?.complete(Unit)
                        }
                    }
                }
            } catch (_: Exception) {
                connected = false
            }
        }

        // 等远端 shell 起来，再换成轻量 sh 并关闭输入回显（保证标记只在真实输出里出现）。
        delay(800)
        sock.send("exec sh 2>/dev/null\n")
        delay(300)
        sock.send("stty -echo 2>/dev/null\n")
        delay(200)

        val ready = "OPENGIT_READY_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Unit>()
        synchronized(lock) {
            buffer.setLength(0)
            tail.setLength(0)
            activeMarker = ready
            activeDeferred = deferred
        }
        sock.send("printf '$ready\\n'\n")
        withTimeoutOrNull(10_000L) { deferred.await() }
        synchronized(lock) {
            activeMarker = null
            activeDeferred = null
            buffer.setLength(0)
            tail.setLength(0)
        }
        connected = true
    }

    /** 串行执行 [script]，读取到 [endMarker] 后返回自本命令开始累积的原始输出。 */
    suspend fun run(script: String, endMarker: String, timeoutMs: Long): String {
        // 串行化：同一 PTY 同一时刻只能执行一条命令，避免并发命令互相覆盖 marker。
        return mutex.withLock {
            if (!connected) connect()
            val sock = socket ?: return@withLock ""
            val deferred = CompletableDeferred<Unit>()
            synchronized(lock) {
                buffer.setLength(0)
                tail.setLength(0)
                activeMarker = endMarker
                activeDeferred = deferred
            }
            sock.send(script)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
            synchronized(lock) {
                activeMarker = null
                activeDeferred = null
            }
            synchronized(lock) { buffer.toString() }
        }
    }

    fun close() {
        readerJob?.cancel()
        val sock = socket
        val id = ptyId
        scope.launch(Dispatchers.IO) {
            runCatching { sock?.close() }
            if (id != null) runCatching { api.removePty(conn, id) }
        }
    }
}
