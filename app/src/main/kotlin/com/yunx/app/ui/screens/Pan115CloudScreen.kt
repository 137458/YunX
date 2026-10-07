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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.yunx.app.data.network.model.ShareExpire
import com.yunx.app.ui.drive.CloudDriveBrowser
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.DriveActions
import com.yunx.app.ui.drive.DriveExpiryOption
import com.yunx.app.ui.drive.DriveSpec
import com.yunx.app.ui.drive.PasscodePolicy
import com.yunx.app.ui.viewmodel.Pan115CloudUiState
import com.yunx.app.ui.viewmodel.Pan115CloudViewModel
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 平台差异：文案 + 有效期编码 + 提取码策略（115 服务端自动生成访问码） */
private val pan115DriveSpec = DriveSpec(
    title = "115网盘",
    shareDesc = "生成分享链接（自动生成访问码）",
    shareHint = "访问码由系统自动生成",
    expiryOptions = ShareExpire.PAN115_OPTIONS.map { (label, value) ->
        DriveExpiryOption(label, value)
    },
    passcode = PasscodePolicy.Auto,
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

@Composable
fun Pan115CloudScreen(
    viewModel: Pan115CloudViewModel,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val moveState by viewModel.moveUiState.collectAsState()

    val actions = DriveActions(
        refresh = { viewModel.refresh() },
        back = { viewModel.back() },
        navigateToLevel = { viewModel.navigateToLevel(it) },
        toggleSelectAll = { viewModel.toggleSelectAll(it) },
        exitMultiSelect = { viewModel.exitMultiSelect() },
        openFolder = { viewModel.openFolder(it) },
        toggleSelect = { viewModel.toggleSelect(it) },
        enterMultiSelect = { viewModel.enterMultiSelect(it) },
        openActions = { viewModel.openActions(it) },
        dismissActions = { viewModel.dismissActions() },
        openMoveRoot = { viewModel.openMoveRoot() },
        openMoveFolder = { viewModel.openMoveFolder(it) },
        moveBack = { viewModel.moveBack() },
        moveNavigateToLevel = { viewModel.moveNavigateToLevel(it) },
        retryMove = { viewModel.openMoveRoot() },
        move = { to, multi -> if (multi) viewModel.moveSelected(to) else viewModel.moveFile(to) },
        rename = { viewModel.renameFile(it) },
        deleteFile = { viewModel.deleteFile() },
        deleteSelected = { viewModel.deleteSelected() },
        shareFile = { expiry, _ -> viewModel.shareFile(expiredType = expiry as Int) },
        shareSelected = { expiry, _ -> viewModel.shareSelected(expiredType = expiry as Int) },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )

    CloudDriveBrowser(
        spec = pan115DriveSpec,
        state = state.toPan115DriveState(),
        moveState = moveState.toPan115DriveState(),
        isRefreshing = viewModel.refreshing,
        multiSelectMode = viewModel.multiSelectMode,
        selected = viewModel.selected,
        operating = viewModel.isOperating,
        shareResult = viewModel.shareResult,
        actions = actions,
        scrollBehavior = scrollBehavior,
        onExit = onExit,
        onDownloadStarted = onDownloadStarted,
        downloadLink = viewModel.downloadLink,
        onStartDownload = { viewModel.startDownload() },
        onDismissDownloadDialog = { viewModel.dismissDownloadDialog() },
        cloudMessage = viewModel.cloudMessage,
        onConsumeMessage = { viewModel.consumeMessage() },
        downloadTriggered = viewModel.downloadTriggered,
        onConsumeDownloadTriggered = { viewModel.consumeDownloadTriggered() },
        onDismissShareResult = { viewModel.dismissShareResult() },
        requestDownload = { _, proceed -> proceed() },
        modifier = modifier
    )
}

/** 115 目录标识：cid（根="0"） */
private fun Pan115CloudUiState.toPan115DriveState(): CloudDriveUiState = when (this) {
    is Pan115CloudUiState.Loading -> CloudDriveUiState.Loading
    is Pan115CloudUiState.Error -> CloudDriveUiState.Error(message)
    is Pan115CloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirId)
}
