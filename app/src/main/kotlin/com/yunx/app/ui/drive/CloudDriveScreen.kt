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

package com.yunx.app.ui.drive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.DownloadLink
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.data.network.model.ShareInfo
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.resolve.DownloadLinkDialog
import com.yunx.app.ui.screens.CloudActionItem
import com.yunx.app.ui.screens.CloudDriveDeleteDialog
import com.yunx.app.ui.screens.CloudDriveErrorView
import com.yunx.app.ui.screens.CloudDriveLoadingView
import com.yunx.app.ui.screens.CloudDriveRenameDialog
import com.yunx.app.ui.screens.CloudDriveScaffold
import com.yunx.app.ui.screens.ShareResultDialog
import com.yunx.app.ui.theme.SquircleShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 文件操作抽屉的步骤 */
private enum class DriveStep { MENU, MOVE, SHARE, RENAME, DELETE }

/**
 * 五个网盘浏览页（百度 / UC / 迅雷 / 139 / 123）的通用实现。
 *
 * 各平台差异集中在 [DriveSpec] 与 [DriveActions] 两处：
 * 视图状态由包装层转换为 [CloudDriveUiState]，ViewModel 操作由包装层以 lambda 注入，
 * 因此本组件内部没有任何平台类型判断。
 *
 * @param requestDownload 下载前拦截钩子。百度非会员 >300MB 需要先弹限速提示，
 *   包装层可在钩子里弹窗并在用户确认后调用 proceed；其余平台直接 proceed。
 */
@Composable
fun CloudDriveBrowser(
    spec: DriveSpec,
    state: CloudDriveUiState,
    moveState: CloudDriveUiState,
    isRefreshing: Boolean,
    multiSelectMode: Boolean,
    selected: List<ShareFile>,
    operating: Boolean,
    shareResult: ShareInfo?,
    actions: DriveActions,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit,
    downloadLink: DownloadLink?,
    onStartDownload: () -> Unit,
    onDismissDownloadDialog: () -> Unit,
    cloudMessage: String?,
    onConsumeMessage: () -> Unit,
    downloadTriggered: Int,
    onConsumeDownloadTriggered: () -> Unit,
    onDismissShareResult: () -> Unit,
    requestDownload: (files: List<ShareFile>, proceed: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(DriveStep.MENU) }
    var actionFile by remember { mutableStateOf<ShareFile?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scrollPositions = remember { mutableStateMapOf<String, Int>() }

    // 系统返回键：多选模式下先退出多选；否则子目录返回上一级，根目录返回账号列表
    BackHandler {
        if (multiSelectMode) {
            actions.exitMultiSelect()
        } else {
            val s = state
            if (s is CloudDriveUiState.Loaded && s.pathNames.isNotEmpty()) actions.back() else onExit()
        }
    }

    LaunchedEffect(cloudMessage) {
        cloudMessage?.let { onConsumeMessage() }
    }

    LaunchedEffect(downloadTriggered) {
        if (downloadTriggered > 0) {
            onConsumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    // 分享成功后关闭表单：结果由 shareResult 驱动 ShareResultDialog 展示
    LaunchedEffect(shareResult) {
        if (shareResult != null) step = DriveStep.MENU
    }

    downloadLink?.let { link ->
        DownloadLinkDialog(
            link = link,
            onDownload = onStartDownload,
            onDismiss = onDismissDownloadDialog
        )
    }

    val openActions: (ShareFile) -> Unit = { file ->
        actions.openActions(file)
        actionFile = file
        step = DriveStep.MENU
    }
    val closeActions = {
        step = DriveStep.MENU
        actionFile = null
        actions.dismissActions()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MiuixTheme.colorScheme.surface
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "cloudDriveState"
        ) { s ->
            when (s) {
                is CloudDriveUiState.Loading -> CloudDriveLoadingView()

                is CloudDriveUiState.Error -> CloudDriveErrorView(
                    message = s.message,
                    onExit = onExit,
                    onRetry = actions.refresh
                )

                is CloudDriveUiState.Loaded -> {
                    val multiSelectActions = remember(selected.size) {
                        listOf(
                            MultiSelectAction(
                                label = "下载",
                                icon = Icons.Outlined.Download,
                                onClick = { requestDownload(selected) { actions.downloadSelected() } }
                            ),
                            MultiSelectAction(
                                label = "分享",
                                icon = Icons.Outlined.Share,
                                onClick = { step = DriveStep.SHARE }
                            ),
                            MultiSelectAction(
                                label = "移动",
                                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                                onClick = { actions.openMoveRoot(); step = DriveStep.MOVE }
                            ),
                            MultiSelectAction(
                                label = "删除",
                                icon = Icons.Outlined.Delete,
                                onClick = { showDeleteConfirm = true }
                            )
                        )
                    }

                    CloudDriveScaffold(
                        driveTitle = spec.title,
                        files = s.files,
                        pathNames = s.pathNames,
                        isRefreshing = isRefreshing,
                        onRefresh = actions.refresh,
                        multiSelectMode = multiSelectMode,
                        selectedFiles = selected.toSet(),
                        scrollBehavior = scrollBehavior,
                        onExit = onExit,
                        onBackToParent = actions.back,
                        onNavigateToLevel = actions.navigateToLevel,
                        onToggleSelectAll = actions.toggleSelectAll,
                        onExitMultiSelect = actions.exitMultiSelect,
                        onFileClick = { file ->
                            if (multiSelectMode) {
                                actions.toggleSelect(file)
                            } else if (file.isdir) {
                                actions.openFolder(file)
                            } else {
                                openActions(file)
                            }
                        },
                        onFileMoreClick = { file -> openActions(file) },
                        onFileLongClick = actions.enterMultiSelect,
                        multiSelectActions = multiSelectActions,
                        scrollPositions = scrollPositions
                    )
                }
            }
        }
    }

    val file = actionFile
    if (file != null && step == DriveStep.MENU) {
        OverlayBottomSheet(show = true, onDismissRequest = closeActions) {
            Column(modifier = sheetColumnModifier()) {
                DriveActionHeader(file = file, folderIcon = spec.folderIcon)
                Spacer(modifier = Modifier.height(12.dp))
                if (!file.isdir) {
                    CloudActionItem(
                        icon = Icons.Outlined.Download,
                        title = "下载",
                        desc = "使用内置下载功能保存到本机",
                        tint = MiuixTheme.colorScheme.primary,
                        onClick = {
                            val target = file
                            closeActions()
                            requestDownload(listOf(target)) { actions.downloadFile() }
                        }
                    )
                } else {
                    CloudActionItem(
                        icon = Icons.Outlined.Download,
                        title = "下载文件夹",
                        desc = "递归下载整个文件夹，保持目录结构",
                        tint = MiuixTheme.colorScheme.primary,
                        onClick = {
                            val target = file
                            closeActions()
                            requestDownload(listOf(target)) { actions.downloadFolder() }
                        }
                    )
                }
                CloudActionItem(
                    icon = Icons.Outlined.Share,
                    title = "分享",
                    desc = spec.shareDesc,
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = { step = DriveStep.SHARE }
                )
                CloudActionItem(
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    title = "移动到",
                    desc = "移动到网盘的其他目录",
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = { actions.openMoveRoot(); step = DriveStep.MOVE }
                )
                CloudActionItem(
                    icon = Icons.Outlined.Edit,
                    title = "重命名",
                    desc = "修改文件名",
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = { step = DriveStep.RENAME }
                )
                CloudActionItem(
                    icon = Icons.Outlined.Delete,
                    title = "删除",
                    desc = "删除到回收站",
                    tint = MiuixTheme.colorScheme.error,
                    onClick = { step = DriveStep.DELETE }
                )
            }
        }
    } else if (file != null) {
        when (step) {
            DriveStep.MOVE -> OverlayBottomSheet(show = true, onDismissRequest = { step = DriveStep.MENU }) {
                Column(modifier = sheetColumnModifier()) {
                    DriveDirPicker(
                        title = "移动到",
                        subtitle = if (multiSelectMode) "已选 " + selected.size + " 项" else file.fname,
                        state = moveState,
                        onBack = { step = DriveStep.MENU },
                        onNavigateToLevel = actions.moveNavigateToLevel,
                        onBackToParent = actions.moveBack,
                        onOpenFolder = actions.openMoveFolder,
                        onRetry = actions.retryMove,
                        confirmText = "移动到此处（%s）",
                        onConfirm = {
                            val to = (moveState as? CloudDriveUiState.Loaded)?.dir ?: ""
                            actions.move(to, multiSelectMode)
                            closeActions()
                        },
                        loading = operating,
                        contentLabel = "driveMoveState"
                    )
                }
            }

            DriveStep.SHARE -> OverlayBottomSheet(show = true, onDismissRequest = { step = DriveStep.MENU }) {
                ShareFormSheet(
                    subtitle = if (multiSelectMode) "已选 " + selected.size + " 项" else file.fname,
                    spec = spec,
                    onBack = { step = DriveStep.MENU },
                    onSubmit = { expiry, passcode ->
                        if (multiSelectMode) {
                            actions.shareSelected(expiry, passcode)
                        } else {
                            actions.shareFile(expiry, passcode)
                        }
                        step = DriveStep.MENU
                    }
                )
            }

            DriveStep.RENAME -> CloudDriveRenameDialog(
                show = true,
                initialName = file.fname,
                onConfirm = {
                    actions.rename(it)
                    closeActions()
                },
                onDismiss = { step = DriveStep.MENU }
            )

            DriveStep.DELETE -> CloudDriveDeleteDialog(
                show = true,
                onDismissRequest = { step = DriveStep.MENU },
                title = "删除文件",
                message = "确定要删除「" + file.fname + "」吗？删除后将移入回收站。",
                onConfirm = {
                    actions.deleteFile()
                    closeActions()
                }
            )

            DriveStep.MENU -> Unit
        }
    }

    // 批量删除二次确认（多选模式）
    CloudDriveDeleteDialog(
        show = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = "删除文件",
        message = "确定要删除选中的 " + selected.size + " 项吗？删除后将移入回收站。",
        onConfirm = { actions.deleteSelected() }
    )

    shareResult?.let { info ->
        ShareResultDialog(info = info, onDismiss = onDismissShareResult)
    }
}

/** 操作抽屉头部：40dp 图标底板 + 文件名 + 文件/文件夹标签 */
@Composable
private fun DriveActionHeader(file: ShareFile, folderIcon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = SquircleShape(12.dp),
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (file.isdir) folderIcon else Icons.Outlined.Download,
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
}

/** 抽屉内容统一内边距 */
private fun sheetColumnModifier(): Modifier = Modifier
    .fillMaxWidth()
    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
