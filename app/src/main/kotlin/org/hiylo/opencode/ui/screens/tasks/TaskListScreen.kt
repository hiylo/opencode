/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : TaskListScreen.kt
 * Date : 2026/09/10 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.opencode.R
import org.hiylo.opencode.domain.model.BackendTask
import org.hiylo.opencode.domain.model.BackendTaskStatus
import org.hiylo.opencode.ui.components.AppCardShape
import org.hiylo.opencode.ui.components.AppPrimaryButton
import org.hiylo.opencode.ui.components.appAmoledBorder
import org.hiylo.opencode.ui.components.isAmoledTheme

private val sampleTasks = listOf(
    BackendTask(
        id = "task_01",
        directory = "/workspaces/opencode",
        prompt = "给所有 controller 层的方法补充统一日志与错误处理",
        status = BackendTaskStatus.Running,
        progress = "正在生成改动…",
        attempts = 1,
        createdAt = "19:20",
    ),
    BackendTask(
        id = "task_02",
        directory = "/workspaces/weemall",
        prompt = "重构订单模块的库存扣减流程",
        status = BackendTaskStatus.Succeeded,
        result = "完成：修改 12 个文件，新增 3 个测试",
        attempts = 1,
        createdAt = "18:55",
    ),
    BackendTask(
        id = "task_03",
        directory = "/workspaces/framework",
        prompt = "跑一遍全量单元测试并汇总失败项",
        status = BackendTaskStatus.Queued,
        attempts = 0,
        createdAt = "19:23",
    ),
    BackendTask(
        id = "task_04",
        directory = "/workspaces/hermes-client",
        prompt = "修复列表滚动时的内存泄漏",
        status = BackendTaskStatus.Failed,
        error = "上游会话忙，重试次数耗尽",
        attempts = 3,
        createdAt = "18:40",
    ),
    BackendTask(
        id = "task_05",
        directory = "/workspaces/components",
        prompt = "批量升级过期依赖并验证兼容性",
        status = BackendTaskStatus.Canceled,
        attempts = 0,
        createdAt = "17:58",
    ),
)

private data class TaskStatusStyle(
    val label: String,
    val color: Color,
    val icon: ImageVector,
)

@Composable
private fun taskStatusStyle(status: BackendTaskStatus): TaskStatusStyle {
    val amoled = isAmoledTheme()
    return when (status) {
        BackendTaskStatus.Queued -> TaskStatusStyle(
            stringResource(R.string.task_status_queued),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule,
        )
        BackendTaskStatus.Running -> TaskStatusStyle(
            stringResource(R.string.task_status_running),
            MaterialTheme.colorScheme.primary,
            Icons.Default.PlayArrow,
        )
        BackendTaskStatus.Succeeded -> TaskStatusStyle(
            stringResource(R.string.task_status_succeeded),
            if (amoled) Color(0xFF4CAF50) else Color(0xFF2E7D32),
            Icons.Default.CheckCircle,
        )
        BackendTaskStatus.Failed -> TaskStatusStyle(
            stringResource(R.string.task_status_failed),
            MaterialTheme.colorScheme.error,
            Icons.Default.Error,
        )
        BackendTaskStatus.Canceled -> TaskStatusStyle(
            stringResource(R.string.task_status_canceled),
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Cancel,
        )
        BackendTaskStatus.Retrying -> TaskStatusStyle(
            stringResource(R.string.task_status_retrying),
            Color(0xFFF57C00),
            Icons.Default.Refresh,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onNavigateBack: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    var prompt by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<BackendTaskStatus?>(null) }

    val visibleTasks = sampleTasks.filter { filter == null || it.status == filter }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            // 提交任务区
            Card(
                shape = AppCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                ),
                border = appAmoledBorder(0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.tasks_prompt_hint)) },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = directory,
                        onValueChange = { directory = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.tasks_directory_hint)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { /* TODO 批量 */ }) {
                            Text(stringResource(R.string.tasks_batch))
                        }
                        Spacer(Modifier.width(8.dp))
                        AppPrimaryButton(
                            onClick = { prompt = "" },
                            enabled = prompt.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.tasks_submit))
                        }
                    }
                }
            }

            // 筛选 chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { filter = null },
                    label = { Text(stringResource(R.string.tasks_filter_all)) },
                )
                listOf(
                    BackendTaskStatus.Queued,
                    BackendTaskStatus.Running,
                    BackendTaskStatus.Succeeded,
                    BackendTaskStatus.Failed,
                    BackendTaskStatus.Canceled,
                ).forEach { status ->
                    FilterChip(
                        selected = filter == status,
                        onClick = { filter = status },
                        label = { Text(taskStatusStyle(status).label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (visibleTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        TaskCard(task = task)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: BackendTask) {
    val isAmoled = isAmoledTheme()
    val style = taskStatusStyle(task.status)

    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.color,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = style.color,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = task.directory.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = task.createdAt.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (task.status) {
                BackendTaskStatus.Running -> {
                    if (!task.progress.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = style.color,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = task.progress,
                                style = MaterialTheme.typography.bodySmall,
                                color = style.color,
                            )
                        }
                    }
                }
                BackendTaskStatus.Succeeded -> {
                    if (!task.result.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = task.result,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
                BackendTaskStatus.Failed -> {
                    if (!task.error.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = task.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
