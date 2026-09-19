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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.BreadcrumbBar
import com.yunx.app.ui.components.BreadcrumbItem
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.DownloadLinkDialog
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.viewmodel.UCCloudUiState
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * UC 网盘云盘浏览页：
 * - 目录浏览 + 下拉刷新 + 面包屑回退
 * - 长按多选（批量下载/分享/移动/删除）
 * - 文件/文件夹操作菜单（下载/重命名/移动/分享/删除）
 * 基于 CloudDriveScaffold 规范实现。
 */
@Composable
fun UCCoudScreen(
    viewModel: UCCoudViewModel,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // 系统返回键：多选模式下先退出多选；否则子目录返回上一级，根目录返回账号列表
    BackHandler {
        if (viewModel.multiSelectMode) {
            viewModel.exitMultiSelect()
        } else {
            val s = state
            if (s is UCCloudUiState.Loaded && s.pathNames.isNotEmpty()) viewModel.back() else onExit()
        }
    }

    val scrollPositions = remember { mutableStateMapOf<String, Int>() }
    var showActionSheet by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.cloudMessage) {
        viewModel.cloudMessage?.let {
            SnackbarController.show(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(viewModel.downloadTriggered) {
        if (viewModel.downloadTriggered > 0) {
            viewModel.consumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    // 单文件下载确认弹窗
    viewModel.downloadLink?.let { link ->
        DownloadLinkDialog(
            link = link,
            onDownload = { viewModel.startDownload() },
            onDismiss = { viewModel.dismissDownloadDialog() }
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.surface
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(140))
            },
            label = "ucCloudState"
        ) { s ->
            when (s) {
                is UCCloudUiState.Loading -> CloudDriveLoadingView()

                is UCCloudUiState.Error -> CloudDriveErrorView(
                    message = s.message,
                    onExit = onExit,
                    onRetry = { viewModel.refresh() }
                )

                is UCCloudUiState.Loaded -> {
                    val multiSelectActions = remember(viewModel.selected.size) {
                        listOf(
                            MultiSelectAction(
                                label = "下载",
                                icon = Icons.Outlined.Download,
                                onClick = { viewModel.downloadSelected() }
                            ),
                            MultiSelectAction(
                                label = "分享",
                                icon = Icons.Outlined.Share,
                                onClick = { showShare = true }
                            ),
                            MultiSelectAction(
                                label = "移动",
                                icon = Icons.Outlined.DriveFileMove,
                                onClick = { showMove = true }
                            ),
                            MultiSelectAction(
                                label = "删除",
                                icon = Icons.Outlined.Delete,
                                onClick = { showDeleteConfirm = true }
                            )
                        )
                    }

                    CloudDriveScaffold(
                        driveTitle = "UC网盘",
                        files = s.files,
                        pathNames = s.pathNames,
                        isRefreshing = viewModel.refreshing,
                        onRefresh = { viewModel.refresh() },
                        multiSelectMode = viewModel.multiSelectMode,
                        selectedFiles = viewModel.selected.toSet(),
                        scrollBehavior = scrollBehavior,
                        onExit = onExit,
                        onBackToParent = { viewModel.back() },
                        onNavigateToLevel = { viewModel.navigateToLevel(it) },
                        onToggleSelectAll = { viewModel.toggleSelectAll(it) },
                        onExitMultiSelect = { viewModel.exitMultiSelect() },
                        onFileClick = { file ->
                            if (viewModel.multiSelectMode) {
                                viewModel.toggleSelect(file)
                            } else if (file.isdir) {
                                viewModel.openFolder(file)
                            } else {
                                viewModel.openActions(file)
                                showActionSheet = true
                            }
                        },
                        onFileMoreClick = { file ->
                            viewModel.openActions(file)
                            showActionSheet = true
                        },
                        onFileLongClick = { file -> viewModel.enterMultiSelect(file) },
                        multiSelectActions = multiSelectActions,
                        scrollPositions = scrollPositions
                    )
                }
            }
        }
    }

    // 文件操作弹窗
    if (showActionSheet && viewModel.actionFile != null) {
        val file = viewModel.actionFile!!
        UCActionSheet(
            file = file,
            viewModel = viewModel,
            onDownload = {
                showActionSheet = false
                viewModel.downloadFile()
            },
            onDownloadFolder = if (file.isdir) {
                {
                    showActionSheet = false
                    viewModel.downloadFolder()
                }
            } else null,
            onRename = {
                showActionSheet = false
                showRename = true
            },
            onMove = {
                showActionSheet = false
                showMove = true
            },
            onShare = {
                showActionSheet = false
                showShare = true
            },
            onDelete = {
                showActionSheet = false
                showDeleteConfirm = true
            },
            onDismiss = {
                showActionSheet = false
                viewModel.dismissActions()
            }
        )
    }

    // 重命名弹窗（WindowDialog）
    if (showRename && viewModel.actionFile != null) {
        CloudDriveRenameDialog(
            show = true,
            initialName = viewModel.actionFile?.fname ?: "",
            onConfirm = {
                viewModel.renameFile(it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }

    // 移动目录弹窗
    if (showMove) {
        UCMoveSheet(
            viewModel = viewModel,
            onDismiss = { showMove = false }
        )
    }

    // 分享弹窗
    if (showShare) {
        UCShareSheet(
            viewModel = viewModel,
            onDismiss = { showShare = false }
        )
    }

    // 分享结果弹窗（WindowDialog）
    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }

    // 删除二次确认（WindowDialog）
    CloudDriveDeleteDialog(
        show = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = "删除文件",
        message = if (viewModel.multiSelectMode) {
            "确定要删除选中的 ${viewModel.selected.size} 项吗？删除后将移入回收站。"
        } else {
            "确定要删除「${viewModel.actionFile?.fname}」吗？删除后将移入回收站。"
        },
        onConfirm = {
            if (viewModel.multiSelectMode) {
                viewModel.deleteSelected()
            } else {
                viewModel.deleteFile()
            }
        }
    )
}

/** UC 网盘操作菜单抽屉（OverlayBottomSheet） */
@Composable
private fun UCActionSheet(
    file: ShareFile,
    viewModel: UCCoudViewModel,
    onDownload: () -> Unit,
    onDownloadFolder: (() -> Unit)? = null,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = SquircleShape(12.dp),
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (file.isdir) Icons.Outlined.DriveFileMove else Icons.Outlined.Download,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fname,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (file.isdir) "文件夹" else "文件",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (!file.isdir) {
                CloudActionItem(
                    icon = Icons.Outlined.Download,
                    title = "下载",
                    desc = "使用内置下载功能保存到本机",
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onDownload
                )
            } else if (onDownloadFolder != null) {
                CloudActionItem(
                    icon = Icons.Outlined.Download,
                    title = "下载文件夹",
                    desc = "递归下载整个文件夹，保持目录结构",
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onDownloadFolder
                )
            }
            CloudActionItem(
                icon = Icons.Outlined.Share,
                title = "分享",
                desc = "生成分享链接（可设提取码/有效期）",
                tint = MiuixTheme.colorScheme.primary,
                onClick = onShare
            )
            CloudActionItem(
                icon = Icons.Outlined.DriveFileMove,
                title = "移动到",
                desc = "移动到网盘的其他目录",
                tint = MiuixTheme.colorScheme.primary,
                onClick = onMove
            )
            CloudActionItem(
                icon = Icons.Outlined.Edit,
                title = "重命名",
                desc = "修改文件名",
                tint = MiuixTheme.colorScheme.primary,
                onClick = onRename
            )
            CloudActionItem(
                icon = Icons.Outlined.Delete,
                title = "删除",
                desc = "移入回收站",
                tint = MiuixTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

/** 移动目录选择抽屉（OverlayBottomSheet + BreadcrumbBar） */
@Composable
private fun UCMoveSheet(
    viewModel: UCCoudViewModel,
    onDismiss: () -> Unit
) {
    val moveState by viewModel.moveUiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.openMoveRoot() }

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            CloudStepHeader(
                title = "移动到",
                subtitle = if (viewModel.multiSelectMode) "已选 ${viewModel.selected.size} 项" else (viewModel.actionFile?.fname ?: ""),
                onBack = onDismiss
            )
            Spacer(modifier = Modifier.height(8.dp))
            val movePathNames = (moveState as? UCCloudUiState.Loaded)?.pathNames ?: emptyList()
            val moveBreadcrumbs = remember(movePathNames) {
                buildList {
                    add(BreadcrumbItem(path = "root", text = "根目录"))
                    movePathNames.forEachIndexed { index, name ->
                        add(BreadcrumbItem(path = (index + 1).toString(), text = name))
                    }
                }
            }
            BreadcrumbBar(
                items = moveBreadcrumbs,
                onItemClick = { viewModel.moveNavigateToLevel(it) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if ((moveState as? UCCloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
                BackToParentItem(onClick = { viewModel.moveBack() })
                Spacer(modifier = Modifier.height(4.dp))
            }
            AnimatedContent(
                targetState = moveState,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "ucMoveState"
            ) { s ->
                when (s) {
                    is UCCloudUiState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) { InfiniteProgressIndicator() }

                    is UCCloudUiState.Error -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = s.message,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }

                    is UCCloudUiState.Loaded -> {
                        val dirs = s.files.filter { it.isdir }
                        if (dirs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "当前目录没有子文件夹，可直接移动到此处",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(dirs, key = { it.fid }) { dir ->
                                    ShareFileRow(file = dir, onClick = { viewModel.openMoveFolder(dir) })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val dirName = (moveState as? UCCloudUiState.Loaded)?.pathNames?.lastOrNull() ?: "根目录"
            Button(
                onClick = {
                    val to = (moveState as? UCCloudUiState.Loaded)?.dirFid ?: "0"
                    if (viewModel.multiSelectMode) viewModel.moveSelected(to) else viewModel.moveFile(to)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = Icons.Outlined.DriveFileMove,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("移动到此处（$dirName）", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** UC 分享设置抽屉（OverlayBottomSheet） */
@Composable
private fun UCShareSheet(
    viewModel: UCCoudViewModel,
    onDismiss: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }
    val expireOptions = listOf(
        "永久有效" to 1,
        "1 天" to 2,
        "7 天" to 3,
        "30 天" to 4
    )

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            CloudStepHeader(
                title = "分享文件",
                subtitle = if (viewModel.multiSelectMode) "已选 ${viewModel.selected.size} 项" else (viewModel.actionFile?.fname ?: ""),
                onBack = onDismiss
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "提取码",
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudSelectChip(
                    selected = !withPassword,
                    label = "无提取码",
                    onClick = { withPassword = false }
                )
                CloudSelectChip(
                    selected = withPassword,
                    label = "设置提取码",
                    onClick = {
                        withPassword = true
                        if (passcode.isBlank()) passcode = randomPasscode()
                    }
                )
            }
            if (withPassword) {
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = passcode,
                    onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = "4 位提取码",
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "有效期",
                style = MiuixTheme.textStyles.headline2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                expireOptions.forEach { (name, value) ->
                    CloudSelectChip(
                        selected = expiredType == value,
                        label = name,
                        onClick = { expiredType = value }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (viewModel.multiSelectMode) {
                        viewModel.shareSelected(
                            urlType = if (withPassword) 2 else 1,
                            passcode = passcode,
                            expiredType = expiredType
                        )
                    } else {
                        viewModel.shareFile(
                            urlType = if (withPassword) 2 else 1,
                            passcode = passcode,
                            expiredType = expiredType
                        )
                    }
                    onDismiss()
                },
                enabled = !withPassword || passcode.length == 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

private fun randomPasscode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..4).map { chars.random() }.joinToString("")
}