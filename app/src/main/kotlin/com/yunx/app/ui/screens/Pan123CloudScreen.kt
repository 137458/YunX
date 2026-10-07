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
import com.yunx.app.ui.viewmodel.Pan123CloudUiState
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 平台差异：文案 + 有效期编码 + 提取码策略 */
private val pan123DriveSpec = DriveSpec(
    title = "123云盘",
    shareDesc = "生成分享链接（可设提取码/有效期）",
    shareHint = null,
    expiryOptions = listOf<DriveExpiryOption<Any?>>(
        DriveExpiryOption("永久有效", ShareExpire.FOREVER),
        DriveExpiryOption("1 天", ShareExpire.ONE_DAY),
        DriveExpiryOption("7 天", ShareExpire.SEVEN_DAYS),
        DriveExpiryOption("30 天", ShareExpire.THIRTY_DAYS)
    ),
    passcode = PasscodePolicy.Optional(label = "4 位提取码"),
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

@Composable
fun Pan123CloudScreen(
    viewModel: Pan123CloudViewModel,
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
        retryMove = { viewModel.retryMove() },
        move = { to, multi -> if (multi) viewModel.moveSelected(to) else viewModel.moveFile(to) },
        rename = { viewModel.renameFile(it) },
        deleteFile = { viewModel.deleteFile() },
        deleteSelected = { viewModel.deleteSelected() },
        shareFile = { expiry, passcode ->
            viewModel.shareFile(
                expiredType = expiry as Int,
                sharePwd = if (passcode.isBlank()) "" else passcode
            )
        },
        shareSelected = { expiry, passcode ->
            viewModel.shareSelected(
                expiredType = expiry as Int,
                sharePwd = if (passcode.isBlank()) "" else passcode
            )
        },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )

    CloudDriveBrowser(
        spec = pan123DriveSpec,
        state = state.toPan123DriveState(),
        moveState = moveState.toPan123DriveState(),
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

/** Pan123 目录标识：dirId（根 "0"） */
private fun Pan123CloudUiState.toPan123DriveState(): CloudDriveUiState = when (this) {
    is Pan123CloudUiState.Loading -> CloudDriveUiState.Loading
    is Pan123CloudUiState.Error -> CloudDriveUiState.Error(message)
    is Pan123CloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirId)
}
