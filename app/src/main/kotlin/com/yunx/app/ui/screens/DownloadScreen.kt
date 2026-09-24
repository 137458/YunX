/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.util.installApk
import com.yunx.app.ui.util.openSavedFile
import com.yunx.app.ui.viewmodel.DownloadViewModel
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 下载页：任务列表（分片多线程下载 / 断点续传）、进度展示、暂停/继续/删除/打开。
 */
@Composable
fun DownloadScreen(
    scrollBehavior: ScrollBehavior,
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scaffoldPadding = LocalScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current
    val tasks by viewModel.tasks.collectAsState()
    val stats by viewModel.stats.collectAsState()
    // 任务分组只随任务列表变化重算。LazyListScope 内不能调用 remember，
    // 而速度统计每 250ms 刷新一次——放在列表 lambda 里会让每次重组都全量 filter + groupBy。
    val rootTasks = remember(tasks) { tasks.filter { !it.fileName.contains('/') } }
    val folderGroups = remember(tasks) {
        tasks.filter { it.fileName.contains('/') }.groupBy { it.fileName.substringBefore('/') }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Android 9- 写公共目录需要 WRITE_EXTERNAL_STORAGE
    val needLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showAddDialog = true
        else SnackbarController.show("需要存储权限才能保存到下载目录")
    }
    val hasPermission = remember {
        if (needLegacyPermission) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            EmptyDownloadState(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
                    top = 8.dp + scaffoldPadding.calculateTopPadding(),
                    end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = 80.dp + scaffoldPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 批量操作栏：全部暂停 / 全部开始 / 删除全部
                item(key = "download_batch_bar") {
                    DownloadBatchBar(
                        hasActive = tasks.any {
                            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                                it.status == DownloadTaskEntity.STATUS_PENDING
                        },
                        hasResumable = tasks.any {
                            it.status == DownloadTaskEntity.STATUS_PAUSED ||
                                it.status == DownloadTaskEntity.STATUS_FAILED
                        },
                        onPauseAll = { viewModel.pauseAll() },
                        onResumeAll = { viewModel.resumeAll() },
                        onDeleteAll = { showDeleteAllConfirm = true }
                    )
                }

                // 根目录任务（无相对路径）单独显示；文件夹任务按「顶级目录」分组（整个文件夹归为一组）
                items(rootTasks, key = { it.id }, contentType = { "download_task" }) { task ->
                        DownloadTaskCard(
                            task = task,
                            stats = stats[task.id],
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id) },
                            onRemove = { pendingDelete = task },
                            onRedownload = { viewModel.redownload(task) }
                        )
                    }

                    folderGroups.forEach { (folder, groupTasks) ->
                        item(key = "folder_$folder", contentType = "download_folder") {
                            FolderDownloadGroup(
                                folder = folder,
                                tasks = groupTasks,
                                stats = stats,
                                onPause = { viewModel.pause(it) },
                                onResume = { viewModel.resume(it) },
                                onRemove = { pendingDelete = it },
                                onRedownload = { viewModel.redownload(it) }
                            )
                        }
                    }
                }
            }

        // 悬浮添加下载任务按钮（Miuix 风格）
        FloatingActionButton(
            onClick = {
                if (needLegacyPermission && !hasPermission) {
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    showAddDialog = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = 16.dp + scaffoldPadding.calculateBottomPadding()
                ),
            shape = SquircleShape(18.dp),
            containerColor = MiuixTheme.colorScheme.primary,
            minWidth = 52.dp,
            minHeight = 52.dp
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "添加任务",
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    if (showAddDialog) {
        AddDownloadDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, name ->
                showAddDialog = false
                viewModel.enqueue(url, name)
            }
        )
    }

    // 删除二次确认（可选同时删除本地文件）
    pendingDelete?.let { task ->
        DeleteConfirmDialog(
            task = task,
            onDismiss = { pendingDelete = null },
            onConfirm = { deleteLocal ->
                pendingDelete = null
                viewModel.remove(task.id, deleteLocal)
            }
        )
    }

    // 删除全部任务二次确认（可选同时删除本地文件）
    if (showDeleteAllConfirm) {
        var deleteAllLocal by remember { mutableStateOf(false) }
        val hasCompletedFile = tasks.any {
            it.status == DownloadTaskEntity.STATUS_COMPLETED && it.savePath.isNotBlank()
        }
        WindowDialog(
            show = showDeleteAllConfirm,
            onDismissRequest = { showDeleteAllConfirm = false },
            title = "删除全部任务"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "确定删除所有下载任务吗？删除后任务记录将被清除，且不可恢复。",
                    style = MiuixTheme.textStyles.body1
                )
                if (hasCompletedFile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            state = ToggleableState(deleteAllLocal),
                            onClick = { deleteAllLocal = !deleteAllLocal }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "同时删除本地文件",
                            style = MiuixTheme.textStyles.body1
                        )
                    }
                    Text(
                        text = "勾选后将一并删除所有已下载到 Download 目录的文件，且不可恢复。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showDeleteAllConfirm = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showDeleteAllConfirm = false
                            viewModel.removeAll(deleteAllLocal)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        )
                    ) {
                        Text("全部删除", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/** 批量操作栏：全部暂停 / 全部开始 / 删除全部（Miuix 紧凑按钮，无可用操作时禁用） */
@Composable
private fun DownloadBatchBar(
    hasActive: Boolean,
    hasResumable: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            text = "全部暂停",
            onClick = onPauseAll,
            enabled = hasActive
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            text = "全部开始",
            onClick = onResumeAll,
            enabled = hasResumable
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            text = "删除全部",
            onClick = onDeleteAll,
            colors = ButtonDefaults.textButtonColors(
                textColor = MiuixTheme.colorScheme.error
            )
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (deleteLocal: Boolean) -> Unit
) {
    var deleteLocal by remember { mutableStateOf(false) }
    val hasLocalFile = task.status == DownloadTaskEntity.STATUS_COMPLETED && task.savePath.isNotBlank()

    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "删除下载任务"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "确定删除「${task.fileName}」吗？",
                style = MiuixTheme.textStyles.body1
            )
            if (hasLocalFile) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = ToggleableState(deleteLocal),
                        onClick = { deleteLocal = !deleteLocal }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "同时删除本地文件",
                        style = MiuixTheme.textStyles.body1
                    )
                }
            }
            Text(
                text = if (hasLocalFile) {
                    "勾选后将一并删除已下载到 Download 目录的文件，且不可恢复。"
                } else if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
                    "该任务没有已完成的本地文件。"
                } else {
                    "该任务尚未完成，删除后将同时清除已下载的临时文件，且不可恢复。"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(deleteLocal) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError
                    )
                ) {
                    Text("删除", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MiuixTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无下载任务",
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "解析分享后点击文件即可加入下载队列\n也可点击右下角按钮手动添加",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 文件夹下载组：同一「顶级目录」下的所有任务合并为一个可展开卡片。
 * 收起时显示文件夹名 + 统计 + 总体进度；展开后显示子任务（含子文件夹内文件）紧凑列表。
 */
@Composable
private fun FolderDownloadGroup(
    folder: String,
    tasks: List<DownloadTaskEntity>,
    stats: Map<Long, DownloadStats>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRemove: (DownloadTaskEntity) -> Unit,
    onRedownload: (DownloadTaskEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val completed = tasks.count { it.status == DownloadTaskEntity.STATUS_COMPLETED }
    val totalSize = tasks.sumOf { it.totalSize }
    // 聚合显示钳制：任何单项竞态残留都不会让"已下载 > 总大小"
    val downloaded = minOf(tasks.sumOf { it.downloadedSize }, totalSize)
    val fraction = if (totalSize > 0) {
        (downloaded.toFloat() / totalSize).coerceIn(0f, 1f)
    } else 0f
    val done = completed == tasks.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors()
    ) {
        Column {
            // 头部：点击展开/收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文件夹图标（圆角方块，主色容器）
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = SquircleShape(14.dp),
                    color = MiuixTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${completed}/${tasks.size} 个文件 · ${formatSize(downloaded)} / ${formatSize(totalSize)}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                // 总体进度徽标
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (done) {
                        MiuixTheme.colorScheme.secondaryContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = when {
                            done -> "已完成"
                            // 分组内存在大小未知的任务时不报假的 0%
                            totalSize <= 0 -> "进行中"
                            else -> "${(fraction * 100).toInt()}%"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) {
                            MiuixTheme.colorScheme.onSecondaryContainer
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            // 展开区：总体进度条 + 子任务紧凑列表
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150), shrinkTowards = Alignment.Top)
            ) {
                Column {
                    // 总体进度条（已完成或大小未知时隐藏，避免显示 0% 的假进度）
                    if (!done && totalSize > 0) {
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            height = 4.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                    // 子任务列表（紧凑行，含子文件夹内文件）
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tasks.forEach { task ->
                            DownloadSubTaskRow(
                                task = task,
                                stats = stats[task.id],
                                onPause = { onPause(task.id) },
                                onResume = { onResume(task.id) },
                                onRemove = { onRemove(task) },
                                onRedownload = { onRedownload(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 文件夹组内子任务紧凑行：相对路径 + 状态 + 进度条 + 操作按钮。
 * 相比独立任务卡更轻量，适合嵌套在文件夹组内。
 */
@Composable
private fun DownloadSubTaskRow(
    task: DownloadTaskEntity,
    stats: DownloadStats?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRedownload: () -> Unit
) {
    val context = LocalContext.current
    val isDownloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_PENDING
    val fraction = if (task.totalSize > 0) {
        (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f)
    } else 0f
    // 显示相对路径（去掉顶级目录前缀，如 "A/B/b.mp4" → "B/b.mp4"）
    val displayName = task.fileName.substringAfter('/')
    // 长按任务行弹出操作菜单（复制直链 / 重新下载 / 删除）
    var showMenu by remember { mutableStateOf(false) }

    // 只保留长按手势：原先的 onClick = {} 会让卡片被读屏当成"按钮"却没有任何动作。
    Card(
        modifier = Modifier.fillMaxWidth(),
        onLongPress = { showMenu = true },
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 文件小图标
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = SquircleShape(9.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isDownloading && stats != null && stats.speed > 0 ->
                                "${DownloadTaskEntity.statusText(task.status)} · ${formatSpeed(stats.speed)}"
                            task.status == DownloadTaskEntity.STATUS_COMPLETED && task.avgSpeed > 0 ->
                                "${DownloadTaskEntity.statusText(task.status)} · 平均 ${formatSpeed(task.avgSpeed)}"
                            else -> taskStatusLine(task)
                        },
                        style = MiuixTheme.textStyles.footnote2,
                        color = if (task.status == DownloadTaskEntity.STATUS_FAILED) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        }
                    )
                }
                // 主操作（暂停/继续/重试/打开）
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING -> IconButton(onClick = onPause, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.Pause, contentDescription = "暂停",
                            tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                        )
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> IconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.PlayArrow, contentDescription = "继续",
                            tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                        )
                    }
                    DownloadTaskEntity.STATUS_FAILED -> IconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.Refresh, contentDescription = "重试",
                            tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(18.dp)
                        )
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> IconButton(
                        onClick = { openSavedFile(context, task.savePath) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "打开",
                            tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // 删除（命中区 48dp：Miuix IconButton 不会自动扩展触摸目标）
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Delete, contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(18.dp)
                    )
                }
            }
            // 细进度条（完成态或大小未知时折叠，带过渡动画）
            AnimatedVisibility(
                visible = task.status != DownloadTaskEntity.STATUS_COMPLETED && task.totalSize > 0,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    height = 3.dp,
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = if (task.status == DownloadTaskEntity.STATUS_FAILED) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }

        // 长按任务行弹出操作菜单（复制直链 / 重新下载 / 删除）
        if (showMenu) {
            WindowDialog(
                show = showMenu,
                onDismissRequest = { showMenu = false },
                title = displayName
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = "复制直链",
                        onClick = {
                            showMenu = false
                            copyToClipboard(context, task.url)
                            SnackbarController.show("直链已复制")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "重新下载",
                        onClick = {
                            showMenu = false
                            onRedownload()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "删除任务",
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        text = "取消",
                        onClick = { showMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    stats: DownloadStats?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRedownload: () -> Unit
) {
    val context = LocalContext.current
    val isDownloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_PENDING
    val fraction = if (task.totalSize > 0) {
        (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f)
    } else 0f
    // 长按任务卡弹出操作菜单（复制直链 / 重新下载 / 删除）
    var showMenu by remember { mutableStateOf(false) }

    // 只保留长按手势：原先的 onClick = {} 会让卡片被读屏当成"按钮"却没有任何动作。
    Card(
        modifier = Modifier.fillMaxWidth(),
        onLongPress = { showMenu = true },
        colors = CardDefaults.defaultColors()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = taskStatusLine(task),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                // 主操作按钮
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING -> IconButton(onClick = onPause) {
                        Icon(Icons.Outlined.Pause, contentDescription = "暂停", tint = MiuixTheme.colorScheme.primary)
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> IconButton(onClick = onResume) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "继续", tint = MiuixTheme.colorScheme.primary)
                    }
                    DownloadTaskEntity.STATUS_FAILED -> IconButton(onClick = onResume) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重试", tint = MiuixTheme.colorScheme.error)
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> Row {
                        // APK 文件：额外显示「安装」按钮
                        if (task.fileName.endsWith(".apk", true)) {
                            IconButton(onClick = { installApk(context, task.savePath) }) {
                                Icon(
                                    imageVector = Icons.Outlined.SystemUpdate,
                                    contentDescription = "安装",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = {
                            openSavedFile(context, task.savePath)
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "打开", tint = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 失败原因（红色小字展示具体错误）
            if (task.status == DownloadTaskEntity.STATUS_FAILED && task.errorMsg.isNotBlank()) {
                Text(
                    text = "失败原因：${task.errorMsg}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 实时统计 + 进度条：完成态整体折叠（带高度过渡动画，不残留空白）
            AnimatedVisibility(
                visible = task.status != DownloadTaskEntity.STATUS_COMPLETED,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    if (isDownloading && stats != null && stats.speed > 0) {
                        Text(
                            text = "${formatSpeed(stats.speed)} · 剩余 ${formatRemain(stats.remainMillis)} · ${stats.chunkCount} 线程",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    // 大小未知（服务器未返回 Content-Length）时不画 0% 的假进度条
                    if (task.totalSize > 0) {
                        LinearProgressIndicator(
                            progress = fraction,
                            modifier = Modifier.fillMaxWidth(),
                            height = 6.dp,
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = when (task.status) {
                                    DownloadTaskEntity.STATUS_FAILED -> MiuixTheme.colorScheme.error
                                    else -> MiuixTheme.colorScheme.primary
                                },
                                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
                        if (task.avgSpeed > 0) {
                            "平均 ${formatSpeed(task.avgSpeed)} · ${formatSize(task.totalSize)}"
                        } else {
                            formatSize(task.totalSize)
                        }
                    } else {
                        progressText(task)
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        textColor = MiuixTheme.colorScheme.error
                    )
                )
            }
        }

        // 长按任务卡弹出操作菜单（复制直链 / 重新下载 / 删除）
        if (showMenu) {
            WindowDialog(
                show = showMenu,
                onDismissRequest = { showMenu = false },
                title = task.fileName
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        text = "复制直链",
                        onClick = {
                            showMenu = false
                            copyToClipboard(context, task.url)
                            SnackbarController.show("直链已复制")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "重新下载",
                        onClick = {
                            showMenu = false
                            onRedownload()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "删除任务",
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        text = "取消",
                        onClick = { showMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_url", text))
}

private fun taskStatusLine(task: DownloadTaskEntity): String {
    val status = DownloadTaskEntity.statusText(task.status)
    return if (task.totalSize > 0) {
        // 显示值钳制到 total（防恢复竞态残留导致显示超总大小）
        val shown = minOf(task.downloadedSize, task.totalSize)
        "$status · ${formatSize(shown)} / ${formatSize(task.totalSize)}"
    } else {
        status
    }
}

private fun progressText(task: DownloadTaskEntity): String {
    // 服务器未返回 Content-Length 时 totalSize 为 0：进度条没有意义，
    // 但必须给出"大小未知"文案，否则底部只剩一个删除按钮，用户以为卡死。
    if (task.totalSize <= 0) {
        return if (task.downloadedSize > 0) "已下载 " + formatSize(task.downloadedSize) + "（大小未知）" else "大小未知"
    }
    // 显示值钳制到 total（防恢复竞态残留导致显示超总大小）
    val shown = minOf(task.downloadedSize, task.totalSize)
    val percent = (shown * 100 / task.totalSize).toInt().coerceIn(0, 100)
    return "已下载 ${formatSize(shown)} / ${formatSize(task.totalSize)} · $percent%"
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSec.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

private fun formatRemain(millis: Long): String {
    if (millis < 0) return "计算中"
    val sec = millis / 1000
    return when {
        sec < 60 -> "${sec}秒"
        sec < 3600 -> "${sec / 60}分${sec % 60}秒"
        else -> "${sec / 3600}时${(sec % 3600) / 60}分"
    }
}

@Composable
private fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "添加下载任务"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "下载文件将保存到 ${Environment.DIRECTORY_DOWNLOADS} 目录",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            TextField(
                value = url,
                onValueChange = {
                    url = it
                    if (name.isBlank()) name = it.substringAfterLast('/').take(80)
                },
                modifier = Modifier.fillMaxWidth(),
                label = "文件直链 URL",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "保存文件名",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(url.trim(), name.trim()) },
                    enabled = url.isNotBlank() && name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始下载", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}