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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.data.download.MagnetLink
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.util.installApk
import com.yunx.app.ui.util.openSavedFile
import com.yunx.app.ui.viewmodel.DownloadViewModel
import com.yunx.app.util.ByteFormat
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
    /** 下载调试开关（设置 → 关于云析 → 长按 → 开发调试）：开启后长按任务菜单里出现「调试信息」 */
    downloadDebug: Boolean = false,
    /** 深链接进来的直链 / 磁力：预填「添加下载任务」弹窗，用户确认后入队；null = 没有 */
    incomingAddUrl: String? = null,
    /** [incomingAddUrl] 被接手后回调（宿主清空，避免重组时重复弹窗） */
    onIncomingAddUrlConsumed: () -> Unit = {},
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
    // 「调试信息」页打开的任务 id（null = 不显示）。本页在「下载」Tab 内全屏展开，与 ResolveScreen 内嵌
    // ShareDetailScreen 同款（不是 MainScreen 的叠加页，可直接复用下载页的卡片与格式化口径）。
    var debugTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    // 任务可能在调试页打开期间被删除：取不到就自动退回列表（不用再手动关页）
    val debugTask = debugTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
    if (debugTask != null) {
        DownloadDebugScreen(
            task = debugTask,
            stats = stats[debugTask.id],
            viewModel = viewModel,
            onPause = { viewModel.pause(debugTask.id) },
            onResume = { viewModel.resume(debugTask.id) },
            onBack = { debugTaskId = null },
            modifier = modifier
        )
        return
    }

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

    // 深链接进来的直链 / 磁力：只是**预填**弹窗，不直接入队 —— 磁力只有 Gopeed 引擎能下、
    // 低版本还可能缺存储权限，让用户在自己看得见的弹窗里点一次确认更稳妥。
    var addDialogInitialUrl by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(incomingAddUrl) {
        val url = incomingAddUrl?.trim().orEmpty()
        if (url.isEmpty()) return@LaunchedEffect
        addDialogInitialUrl = url
        if (needLegacyPermission && !hasPermission) {
            // 与右下角「+」同一条路：先申请存储权限，授权成功后弹窗会自动打开（见 permissionLauncher）
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            showAddDialog = true
        }
        onIncomingAddUrlConsumed()
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
                        DownloadTaskRow(
                            task = task,
                            stats = stats[task.id],
                            density = TaskRowDensity.Regular,
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id) },
                            onRemove = { pendingDelete = task },
                            onRedownload = { viewModel.redownload(task) },
                            downloadDebug = downloadDebug,
                            onOpenDebug = { debugTaskId = it }
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
                                onRedownload = { viewModel.redownload(it) },
                                downloadDebug = downloadDebug,
                                onOpenDebug = { debugTaskId = it }
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
            initialUrl = addDialogInitialUrl,
            onDismiss = {
                showAddDialog = false
                addDialogInitialUrl = ""
            },
            onConfirm = { url, name ->
                showAddDialog = false
                addDialogInitialUrl = ""
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteAllLocal = !deleteAllLocal },
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
            AppSnackbarHost(rememberGlobalSnackbarHostState())
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteLocal = !deleteLocal },
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
        AppSnackbarHost(rememberGlobalSnackbarHostState())
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
    onRedownload: (DownloadTaskEntity) -> Unit,
    /** 下载调试：开启时组内子任务的长按菜单也出现「调试信息」 */
    downloadDebug: Boolean = false,
    onOpenDebug: (Long) -> Unit = {}
) {
    // 用 rememberSaveable：LazyColumn 的 item 滚出屏幕会被销毁，remember 会把它复位成「展开」，
    // 用户收起的文件夹一滚动就又弹开了（saveable 状态按 item key 保存，滚回来仍是收起）
    var expanded by rememberSaveable { mutableStateOf(true) }
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
                            DownloadTaskRow(
                                task = task,
                                stats = stats[task.id],
                                density = TaskRowDensity.Compact,
                                onPause = { onPause(task.id) },
                                onResume = { onResume(task.id) },
                                onRemove = { onRemove(task) },
                                onRedownload = { onRedownload(task) },
                                downloadDebug = downloadDebug,
                                onOpenDebug = onOpenDebug
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 任务行密度：独立任务卡用 Regular，文件夹组内子任务用 Compact。只改尺寸字号，不改结构 */
private enum class TaskRowDensity(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Dp,
    val iconShape: Shape,
    val glyphSize: Dp,
    val iconSpacing: Dp,
    val actionGlyphSize: Dp,
    val progressHeight: Dp,
    val nestedContainer: Boolean,
    val compactText: Boolean,
) {
    Regular(
        horizontalPadding = 14.dp,
        verticalPadding = 14.dp,
        iconSize = 40.dp,
        iconShape = CircleShape,
        glyphSize = 20.dp,
        iconSpacing = 12.dp,
        actionGlyphSize = 20.dp,
        progressHeight = 6.dp,
        nestedContainer = false,
        compactText = false,
    ),
    Compact(
        horizontalPadding = 10.dp,
        verticalPadding = 8.dp,
        iconSize = 32.dp,
        iconShape = SquircleShape(9.dp),
        glyphSize = 16.dp,
        iconSpacing = 10.dp,
        actionGlyphSize = 18.dp,
        progressHeight = 3.dp,
        nestedContainer = true,
        compactText = true,
    ),
}

/**
 * 下载任务行（列表与文件夹组共用）。
 *
 * 结构固定为：图标 + 文件名 + 状态/速度 + 失败原因 + 进度条 + 底部进度与删除。
 * 两种密度只改内边距、图标尺寸与形状、字号和进度条高度，不增删字段——
 * 切换「按文件夹分组」后同一任务的行高、字号、图标形状与信息量不再跳变。
 */
@Composable
private fun DownloadTaskRow(
    task: DownloadTaskEntity,
    stats: DownloadStats?,
    density: TaskRowDensity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRedownload: () -> Unit,
    /** 下载调试开启时长按菜单出现「调试信息」 */
    downloadDebug: Boolean = false,
    onOpenDebug: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val fraction = if (task.totalSize > 0) {
        (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f)
    } else 0f
    // 相对路径（"A/B/b.mp4" → "B/b.mp4"）：组内子任务与独立任务统一按末级目录以下显示，
    // 既不丢区分度，也不会在分组视图里重复整条路径
    val displayName = task.fileName.substringAfter('/')
    // 长按任务行弹出操作菜单（复制直链 / 重新下载 / 删除）
    var showMenu by remember { mutableStateOf(false) }

    // 只保留长按手势：原先的 onClick = {} 会让卡片被读屏当成"按钮"却没有任何动作。
    Card(
        modifier = Modifier.fillMaxWidth(),
        onLongPress = { showMenu = true },
        colors = if (density.nestedContainer) {
            CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
        } else {
            CardDefaults.defaultColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = density.horizontalPadding,
                vertical = density.verticalPadding,
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 文件小图标
                Surface(
                    modifier = Modifier.size(density.iconSize),
                    shape = density.iconShape,
                    color = MiuixTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(density.glyphSize),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(density.iconSpacing))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = if (density.compactText) {
                            MiuixTheme.textStyles.body2
                        } else {
                            MiuixTheme.textStyles.body1
                        },
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = taskRowSubtitle(task, stats),
                        style = if (density.compactText) {
                            MiuixTheme.textStyles.footnote2
                        } else {
                            MiuixTheme.textStyles.body2
                        },
                        color = if (task.status == DownloadTaskEntity.STATUS_FAILED) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 主操作（暂停/继续/重试/安装/打开）；删除固定在底部行，避免两处入口
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING -> IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Pause, contentDescription = "暂停",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(density.actionGlyphSize)
                        )
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow, contentDescription = "继续",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(density.actionGlyphSize)
                        )
                    }
                    DownloadTaskEntity.STATUS_FAILED -> IconButton(
                        onClick = onResume,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Refresh, contentDescription = "重试",
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(density.actionGlyphSize)
                        )
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> Row {
                        // APK 文件额外给「安装」入口，两种密度一致
                        if (task.fileName.endsWith(".apk", true)) {
                            IconButton(
                                onClick = { installApk(context, task.savePath) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SystemUpdate,
                                    contentDescription = "安装",
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(density.actionGlyphSize)
                                )
                            }
                        }
                        IconButton(
                            onClick = { openSavedFile(context, task.savePath) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "打开",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(density.actionGlyphSize)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            // 进度条：完成态或大小未知时折叠（带高度过渡动画，不残留空白）
            AnimatedVisibility(
                visible = task.status != DownloadTaskEntity.STATUS_COMPLETED && task.totalSize > 0,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier.fillMaxWidth(),
                    height = density.progressHeight,
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

            Spacer(modifier = Modifier.height(8.dp))

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

        TaskRowActionMenu(
            task = task,
            title = displayName,
            show = showMenu,
            onDismiss = { showMenu = false },
            onRedownload = onRedownload,
            onRemove = onRemove,
            downloadDebug = downloadDebug,
            onOpenDebug = onOpenDebug,
        )
    }
}

/** 任务行长按菜单：两种密度共用，菜单项与文案只此一份 */
@Composable
private fun TaskRowActionMenu(
    task: DownloadTaskEntity,
    title: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    downloadDebug: Boolean,
    onOpenDebug: (Long) -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = title
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                text = "复制直链",
                onClick = {
                    onDismiss()
                    copyToClipboard(context, task.url)
                    SnackbarController.show("直链已复制")
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "重新下载",
                onClick = {
                    onDismiss()
                    onRedownload()
                },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = "删除任务",
                onClick = {
                    onDismiss()
                    onRemove()
                },
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (downloadDebug) {
                TextButton(
                    text = "调试信息",
                    onClick = {
                        onDismiss()
                        onOpenDebug(task.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_url", text))
}

internal fun taskStatusLine(task: DownloadTaskEntity): String {
    val status = DownloadTaskEntity.statusText(task.status)
    return if (task.totalSize > 0) {
        // 显示值钳制到 total（防恢复竞态残留导致显示超总大小）
        val shown = minOf(task.downloadedSize, task.totalSize)
        "$status · ${formatSize(shown)} / ${formatSize(task.totalSize)}"
    } else {
        status
    }
}

/**
 * 任务行副标题：状态 + 速度 + 剩余时间 + 线程数，完成态改用平均速度。
 *
 * 列表与文件夹组共用同一份文案推导，避免同一任务在两种排版下显示不同信息量。
 */
internal fun taskRowSubtitle(task: DownloadTaskEntity, stats: DownloadStats?): String {
    val status = DownloadTaskEntity.statusText(task.status)
    val isDownloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_PENDING
    if (isDownloading && stats != null && stats.speed > 0L) {
        val parts = mutableListOf(status, formatSpeed(stats.speed))
        if (stats.remainMillis >= 0L) parts += "剩余 ${formatRemain(stats.remainMillis)}"
        if (stats.chunkCount > 0) parts += "${stats.chunkCount} 线程"
        return parts.joinToString(" · ")
    }
    if (task.status == DownloadTaskEntity.STATUS_COMPLETED && task.avgSpeed > 0L) {
        return "$status · 平均 ${formatSpeed(task.avgSpeed)}"
    }
    return taskStatusLine(task)
}

internal fun progressText(task: DownloadTaskEntity): String {
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

internal fun formatSize(bytes: Long): String = ByteFormat.size(bytes)

internal fun formatSpeed(bytesPerSec: Long): String = ByteFormat.speed(bytesPerSec)

internal fun formatRemain(millis: Long): String {
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
    /** 深链接预填的直链 / 磁力（空 = 用户自己输入） */
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    // 预填时把落盘名一起算好（磁力取 dn=，其余取路径末段）
    var name by remember {
        mutableStateOf(
            when {
                initialUrl.isBlank() -> ""
                MagnetLink.isMagnet(initialUrl) -> MagnetLink.displayName(initialUrl)
                else -> initialUrl.substringAfterLast('/').take(80)
            }
        )
    }

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
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}