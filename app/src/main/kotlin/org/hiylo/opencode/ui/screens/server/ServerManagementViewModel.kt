/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerManagementViewModel.kt
 * Date : 2026/09/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.screens.server

import org.hiylo.opencode.logging.AppLogger as Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.hiylo.opencode.data.api.OpenCodeApi
import org.hiylo.opencode.data.api.ServerConfigPatch
import org.hiylo.opencode.data.api.ServerConfigResponse
import org.hiylo.opencode.data.api.ServerConnection
import org.hiylo.opencode.data.shell.ServerShellRegistry
import org.hiylo.opencode.domain.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ServerManagementViewModel"

/** 服务器管理页的 UI 状态。 */
data class ServerManagementUiState(
    val serverName: String = "",
    val version: String? = null,
    val activeSessions: Int? = null,
    val busySessions: Int? = null,
    val memory: String? = null,
    val disk: String? = null,
    val loadAverage: String? = null,
    val config: ServerConfigResponse = ServerConfigResponse(),
    val isLoading: Boolean = true,
    val isRestarting: Boolean = false,
    val isSavingConfig: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * 服务器管理页 ViewModel。
 *
 * 展示服务基本信息（版本号、活跃会话数）、服务器资源（CPU/内存/磁盘，通过共享 PTY 执行
 * shell 命令获取），支持查看/修改服务配置（默认模型、默认 Agent），以及二次确认后重启服务。
 */
@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val shellRegistry: ServerShellRegistry,
) : ViewModel() {

    private val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val username: String = savedStateHandle.get<String>("username").orEmpty()
    private val password: String = savedStateHandle.get<String>("password").orEmpty()
    private val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    private val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
    private val directory: String = savedStateHandle.get<String>("directory").orEmpty()

    private val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    /** 连接级共享 PTY 会话：与 Git 页按 server 复用同一条 PTY。 */
    private var shellAcquired = false
    private val shell by lazy {
        shellAcquired = true
        shellRegistry.acquire(serverId.ifBlank { conn.baseUrl }, api, conn, directory)
    }

    private val _uiState = MutableStateFlow(ServerManagementUiState(serverName = serverName, isLoading = true))
    val uiState: StateFlow<ServerManagementUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    override fun onCleared() {
        if (shellAcquired) {
            shellRegistry.release(serverId.ifBlank { conn.baseUrl })
        }
        super.onCleared()
    }

    /** 重新加载服务信息、活跃会话数与服务器资源。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loadServiceInfo()
            loadSystemInfo()
            loadConfig()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** 加载服务版本号与活跃会话数。 */
    private suspend fun loadServiceInfo() {
        runCatching { api.getHealth(conn) }
            .onSuccess { health ->
                _uiState.update { it.copy(version = health.version) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to load health", e) }

        runCatching { api.listSessionStatuses(conn, directory.takeIf { it.isNotBlank() }) }
            .onSuccess { statuses ->
                val busy = statuses.values.count { it !is SessionStatus.Idle }
                _uiState.update { it.copy(activeSessions = statuses.size, busySessions = busy) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to load session statuses", e) }
    }

    /** 通过共享 PTY 执行 shell 命令获取内存、磁盘与负载信息。 */
    private suspend fun loadSystemInfo() {
        runCatching { shell.runCommand("free -m", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(memory = parseMemory(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read memory", e) }

        runCatching { shell.runCommand("df -h /", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(disk = parseDisk(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read disk", e) }

        runCatching { shell.runCommand("cat /proc/loadavg", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(loadAverage = parseLoadAverage(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read load average", e) }
    }

    /** 加载服务配置（GET /config）。 */
    private suspend fun loadConfig() {
        runCatching { api.getConfig(conn) }
            .onSuccess { config -> _uiState.update { it.copy(config = config) } }
            .onFailure { e -> Log.w(TAG, "Failed to load config", e) }
    }

    /** 修改默认模型并 PATCH /config。 */
    fun updateDefaultModel(model: String?) {
        updateConfigPatch(ServerConfigPatch(model = model?.trim()?.ifBlank { null }))
    }

    /** 修改默认 Agent 并 PATCH /config。 */
    fun updateDefaultAgent(agent: String?) {
        updateConfigPatch(ServerConfigPatch(defaultAgent = agent?.trim()?.ifBlank { null }))
    }

    private fun updateConfigPatch(patch: ServerConfigPatch) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingConfig = true, message = null, error = null) }
            val before = _uiState.value.config
            try {
                api.updateConfig(conn, patch)
                _uiState.update { it.copy(config = api.getConfig(conn)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update config", e)
                _uiState.update { it.copy(config = before, error = e.message) }
            } finally {
                _uiState.update { it.copy(isSavingConfig = false) }
            }
        }
    }

    /** 通过共享 PTY 执行重启命令（需在 UI 层二次确认后调用）。 */
    fun restartServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestarting = true, error = null, message = null) }
            try {
                val result = shell.runCommandResult(RESTART_COMMAND, timeoutMs = 30_000)
                _uiState.update {
                    it.copy(
                        isRestarting = false,
                        message = result.output.ifBlank { null },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart server", e)
                _uiState.update { it.copy(isRestarting = false, error = e.message) }
            }
        }
    }

    /** 清除一次性提示信息。 */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 解析 `free -m` 输出中的内存总量/已用（单位 MB）。 */
    private fun parseMemory(output: String): String? {
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Mem:") } ?: return null
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 3) return null
        val total = fields[1].toIntOrNull() ?: return null
        val used = fields[2].toIntOrNull() ?: return null
        return "${used}MB / ${total}MB"
    }

    /** 解析 `df -h /` 输出中根分区的已用/总量与使用率。 */
    private fun parseDisk(output: String): String? {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val line = lines.firstOrNull { it.contains(" /") && !it.startsWith("Filesystem") } ?: return null
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 5) return null
        val size = fields[1]
        val used = fields[2]
        val use = fields[4]
        return "$used / $size ($use)"
    }

    /** 解析 `/proc/loadavg` 输出的 1/5/15 分钟负载。 */
    private fun parseLoadAverage(output: String): String? {
        val fields = output.trim().split(Regex("\\s+"))
        if (fields.size < 3) return null
        return "${fields[0]} ${fields[1]} ${fields[2]}"
    }

    private companion object {
        /** 服务重启命令；可按部署方式调整（如 `sudo systemctl restart opencode`）。 */
        const val RESTART_COMMAND: String = "systemctl restart opencode"
    }
}
