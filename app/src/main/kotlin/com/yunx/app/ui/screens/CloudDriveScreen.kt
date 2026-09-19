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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.resolve.DownloadLinkDialog
import com.yunx.app.ui.viewmodel.QuarkCloudUiState
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 夸克云盘浏览页：展示个人网盘文件，支持进入文件夹 / 返回 / 面包屑回退。
 * 基于 CloudDriveScaffold 通用模板构建。
 */
@Composable
fun CloudDriveScreen(
    viewModel: QuarkCloudViewModel,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    /** 下载入队后通知上层切换到「下载」Tab（对齐解析页行为） */
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
            if (s is QuarkCloudUiState.Loaded && s.pathNames.isNotEmpty()) viewModel.back() else onExit()
        }
    }

    val scrollPositions = remember { mutableStateMapOf<String, Int>() }

    // 批量操作弹窗（多选模式底部栏触发：分享/移动需要设置或选目录，下载/删除直接执行）
    var showBatchActions by remember { mutableStateOf(false) }
    var batchInitial by remember { mutableStateOf(BatchStep.MENU) }
    // 批量删除二次确认
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 操作结果 Toast
    LaunchedEffect(viewModel.cloudMessage) {
        viewModel.cloudMessage?.let {
            SnackbarController.show(it)
            viewModel.consumeMessage()
        }
    }

    // 下载入队后通知上层切换到「下载」Tab（消费事件，避免再次进入本页重复触发）
    LaunchedEffect(viewModel.downloadTriggered) {
        if (viewModel.downloadTriggered > 0) {
            viewModel.consumeDownloadTriggered()
            onDownloadStarted()
        }
    }

    // 单文件下载确认弹窗（展示直链，长按可复制）
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
            label = "quarkCloudState"
        ) { s ->
            when (s) {
                is QuarkCloudUiState.Loading -> CloudDriveLoadingView()

                is QuarkCloudUiState.Error -> CloudDriveErrorView(
                    message = s.message,
                    onExit = onExit,
                    onRetry = { viewModel.loadRoot() }
                )

                is QuarkCloudUiState.Loaded -> {
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
                                onClick = {
                                    batchInitial = BatchStep.SHARE
                                    showBatchActions = true
                                }
                            ),
                            MultiSelectAction(
                                label = "移动",
                                icon = Icons.Outlined.DriveFileMove,
                                onClick = {
                                    batchInitial = BatchStep.MOVE
                                    showBatchActions = true
                                }
                            ),
                            MultiSelectAction(
                                label = "删除",
                                icon = Icons.Outlined.Delete,
                                onClick = { showDeleteConfirm = true }
                            )
                        )
                    }

                    CloudDriveScaffold(
                        driveTitle = "夸克网盘",
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
                            }
                        },
                        onFileMoreClick = { file -> viewModel.openActions(file) },
                        onFileLongClick = { file -> viewModel.enterMultiSelect(file) },
                        multiSelectActions = multiSelectActions,
                        scrollPositions = scrollPositions
                    )
                }
            }
        }
    }

    // 文件操作弹窗（更多按钮/点击文件 → 下载/分享/移动/重命名/删除）
    viewModel.actionFile?.let { file ->
        FileActionSheet(
            file = file,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissActions() }
        )
    }

    // 批量操作弹窗（长按多选 → 底部栏分享/移动）
    if (showBatchActions) {
        BatchActionSheet(
            viewModel = viewModel,
            initialStep = batchInitial,
            onDismiss = { showBatchActions = false }
        )
    }

    // 批量删除二次确认（WindowDialog）
    CloudDriveDeleteDialog(
        show = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = "删除文件",
        message = "确定要删除选中的 ${viewModel.selected.size} 项吗？删除后将移入回收站。",
        onConfirm = { viewModel.deleteSelected() }
    )
}