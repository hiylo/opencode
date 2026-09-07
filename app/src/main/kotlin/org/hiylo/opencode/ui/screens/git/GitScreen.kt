/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : GitScreen.kt
 * Date : 2026/09/07 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.screens.git

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.opencode.R
import org.hiylo.opencode.ui.components.AppDialog
import org.hiylo.opencode.ui.components.AppPrimaryButton
import org.hiylo.opencode.ui.components.AppSecondaryButton
import org.hiylo.opencode.ui.theme.StatusConnected
import org.hiylo.opencode.ui.theme.StatusError
import org.hiylo.opencode.ui.theme.StatusWarning

/**
 * Git 页面：查看仓库状态、变更、差异与提交历史，并执行提交/推送/拉取/分支操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    onNavigateBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val generatingMessage by viewModel.generatingMessage.collectAsState()
    val generatedMessage by viewModel.generatedMessage.collectAsState()
    val generateError by viewModel.generateError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(R.string.git_operation_success)
    val failedMessage = stringResource(R.string.git_operation_failed)
    var showCommitDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showPushDialog by remember { mutableStateOf(false) }
    var showPullDialog by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(state.operationMessage) {
        val message = state.operationMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(if (message == "success") successMessage else failedMessage)
        viewModel.clearOperationMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.git_loading))
                    }
                    Box {
                        IconButton(onClick = { moreExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_commit)) },
                                onClick = { moreExpanded = false; showCommitDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_push)) },
                                onClick = { moreExpanded = false; showPushDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_pull)) },
                                onClick = { moreExpanded = false; showPullDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_checkout)) },
                                onClick = { moreExpanded = false; showCheckoutDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_new_branch)) },
                                onClick = { moreExpanded = false; showNewBranchDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.notRepository -> NotRepositoryView()
                state.error != null && !state.isLoading -> ErrorView(state.error!!, viewModel::refresh)
                else -> GitContent(
                    state = state,
                    onSelectRepo = viewModel::selectRepo,
                    onLoadDiff = viewModel::loadDiff,
                    onLoadCommitDetail = viewModel::loadCommitDetail,
                )
            }
        }
    }

    if (showCommitDialog) {
        CommitDialog(
            onDismiss = { showCommitDialog = false },
            onConfirm = { message ->
                showCommitDialog = false
                viewModel.commit(message)
            },
            generating = generatingMessage,
            generatedMessage = generatedMessage,
            generateError = generateError,
            onGenerate = viewModel::generateCommitMessage,
            onClearGenerated = viewModel::clearGeneratedMessage,
        )
    }
    if (showNewBranchDialog) {
        NewBranchDialog(
            onDismiss = { showNewBranchDialog = false },
            onConfirm = { name ->
                showNewBranchDialog = false
                viewModel.createBranch(name)
            },
        )
    }
    if (showCheckoutDialog) {
        CheckoutDialog(
            branches = state.branches,
            currentBranch = state.branch,
            onDismiss = { showCheckoutDialog = false },
            onSelect = { branch ->
                showCheckoutDialog = false
                viewModel.checkout(branch)
            },
        )
    }
    if (showPushDialog) {
        RemoteDialog(
            remotes = state.remotes,
            currentBranch = state.branch,
            actionLabel = stringResource(R.string.git_push),
            onDismiss = { showPushDialog = false },
            onSelect = { remote ->
                showPushDialog = false
                viewModel.push(remote)
            },
        )
    }
    if (showPullDialog) {
        RemoteDialog(
            remotes = state.remotes,
            currentBranch = state.branch,
            actionLabel = stringResource(R.string.git_pull),
            onDismiss = { showPullDialog = false },
            onSelect = { remote ->
                showPullDialog = false
                viewModel.pull(remote)
            },
        )
    }
}

@Composable
private fun NotRepositoryView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.git_not_repository),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.git_not_repository_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun GitContent(
    state: GitUiState,
    onSelectRepo: (String) -> Unit,
    onLoadDiff: (String) -> Unit,
    onLoadCommitDetail: (GitCommit) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RepoSelector(state.repos, state.selectedRepo, onSelectRepo)

        BranchStatusRow(state)

        ChangesSection(state.changes, state.selectedDiff, onLoadDiff)

        CommitsSection(
            commits = state.commits,
            selectedCommit = state.selectedCommit,
            commitChanges = state.commitChanges,
            commitDiff = state.commitDiff,
            isLoadingCommit = state.isLoadingCommit,
            onLoadCommitDetail = onLoadCommitDetail,
        )
    }
}

@Composable
private fun RepoSelector(
    repos: List<GitRepo>,
    selectedRepo: String,
    onSelectRepo: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = repos.firstOrNull { it.path == selectedRepo }
    Column {
        Text(
            stringResource(R.string.git_repository),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selected?.label ?: selectedRepo,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                repos.forEach { repo ->
                    DropdownMenuItem(
                        text = { Text(repo.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelectRepo(repo.path)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchStatusRow(state: GitUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        state.branch?.let { branch ->
            Text(
                text = stringResource(R.string.git_branch, branch),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.isClean) {
            Text(
                text = stringResource(R.string.git_clean),
                style = MaterialTheme.typography.bodyMedium,
                color = StatusConnected,
            )
        } else {
            Text(
                text = stringResource(R.string.git_changes_count, state.changes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = StatusWarning,
            )
        }
    }
}

@Composable
private fun ChangesSection(
    changes: List<GitChange>,
    selectedDiff: GitFileDiff?,
    onLoadDiff: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.git_changes),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.git_no_changes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            changes.forEach { change ->
                ChangeRow(change = change, selected = selectedDiff?.path == change.path, onClick = { onLoadDiff(change.path) })
                if (selectedDiff?.path == change.path) {
                    DiffView(selectedDiff)
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(change: GitChange, selected: Boolean, onClick: () -> Unit) {
    val (color, label) = when (change.status) {
        "added", "untracked" -> StatusConnected to stringResource(R.string.git_status_added)
        "deleted" -> StatusError to stringResource(R.string.git_status_deleted)
        else -> StatusWarning to stringResource(R.string.git_status_modified)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = change.path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (change.additions > 0) {
                Text(
                    text = "+${change.additions}",
                    color = StatusConnected,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (change.deletions > 0) {
                Text(
                    text = "-${change.deletions}",
                    color = StatusError,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DiffView(diff: GitFileDiff) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.git_diff),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (diff.content.isBlank()) {
                Text(
                    stringResource(R.string.git_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                diff.content.lines().forEach { line ->
                    val color = when {
                        line.startsWith("+") -> StatusConnected
                        line.startsWith("-") -> StatusError
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitsSection(
    commits: List<GitCommit>,
    selectedCommit: GitCommit?,
    commitChanges: List<GitChange>,
    commitDiff: String,
    isLoadingCommit: Boolean,
    onLoadCommitDetail: (GitCommit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.git_commits),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        if (commits.isEmpty()) {
            Text(
                stringResource(R.string.git_no_commits),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            commits.forEach { commit ->
                val expanded = selectedCommit?.hash == commit.hash
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onLoadCommitDetail(commit) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (expanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = commit.message,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${commit.hash.take(8)} · ${commit.author} · ${commit.date}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            CommitDetail(commitChanges, commitDiff, isLoadingCommit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitDetail(changes: List<GitChange>, diff: String, loading: Boolean) {
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.git_loading), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    if (changes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            changes.forEach { c ->
                val (color, label) = when (c.status) {
                    "added" -> StatusConnected to stringResource(R.string.git_status_added)
                    "deleted" -> StatusError to stringResource(R.string.git_status_deleted)
                    else -> StatusWarning to stringResource(R.string.git_status_modified)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = color, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        c.path,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (diff.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                diff.lines().take(200).forEach { line ->
                    val color = when {
                        line.startsWith("+") -> StatusConnected
                        line.startsWith("-") -> StatusError
                        line.startsWith("@@") || line.startsWith("diff") || line.startsWith("index") -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    generating: Boolean,
    generatedMessage: String?,
    generateError: String?,
    onGenerate: () -> Unit,
    onClearGenerated: () -> Unit,
) {
    var message by remember { mutableStateOf("") }
    LaunchedEffect(generatedMessage) {
        generatedMessage?.let { message = it }
    }
    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.git_commit_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.git_commit_message_hint)) },
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (!generating) {
                            onClearGenerated()
                            onGenerate()
                        }
                    },
                    enabled = !generating,
                ) {
                    if (generating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.git_generate_message))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                AppPrimaryButton(onClick = { onConfirm(message) }, enabled = message.isNotBlank()) {
                    Text(stringResource(R.string.git_confirm_commit))
                }
            }
        }
    }
}

@Composable
private fun NewBranchDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                stringResource(R.string.git_new_branch_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.git_branch_name_hint)) },
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                AppPrimaryButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.git_confirm_create))
                }
            }
        }
    }
}

@Composable
private fun CheckoutDialog(
    branches: List<String>,
    currentBranch: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.git_branches),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(branch, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (branch == currentBranch) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = StatusConnected,
                                )
                            }
                        }
                    },
                    onClick = { onSelect(branch) },
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun RemoteDialog(
    remotes: List<String>,
    currentBranch: String?,
    actionLabel: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            if (remotes.isEmpty()) {
                Text(
                    stringResource(R.string.git_no_remotes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                remotes.forEach { remote ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(remote, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!currentBranch.isNullOrBlank()) {
                                    Text(
                                        currentBranch,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(remote) },
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
