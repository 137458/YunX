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

import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yunx.app.R
import com.yunx.app.ui.drive.CloudDriveBrowser
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.DriveActions
import com.yunx.app.ui.drive.DriveExpiryOption
import com.yunx.app.ui.drive.DriveSpec
import com.yunx.app.ui.drive.PasscodePolicy
import com.yunx.app.ui.viewmodel.UCCloudUiState
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 平台差异：文案 + 有效期编码 + 提取码策略 */
private val ucDriveSpec = DriveSpec(
    title = AppStrings.get(R.string.csa_platform_uc),
    shareDesc = AppStrings.get(R.string.csa_action_share_desc),
    shareHint = null,
    expiryOptions = listOf<DriveExpiryOption<Any?>>(
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_forever), 1),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_1day), 2),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_7days), 3),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_30days), 4)
    ),
    passcode = PasscodePolicy.Optional(label = AppStrings.get(R.string.csa_passcode_4digit)),
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

@Composable
fun UCCoudScreen(
    viewModel: UCCoudViewModel,
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
                urlType = if (passcode.isBlank()) 1 else 2,
                passcode = passcode,
                expiredType = expiry as Int
            )
        },
        shareSelected = { expiry, passcode ->
            viewModel.shareSelected(
                urlType = if (passcode.isBlank()) 1 else 2,
                passcode = passcode,
                expiredType = expiry as Int
            )
        },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )


    // 上传要读 ContentResolver：共享层只回传选中的 uri，实际读取由 ViewModel 完成
    val context = LocalContext.current
    CloudDriveBrowser(
        spec = ucDriveSpec,
        state = state.toUCDriveState(),
        moveState = moveState.toUCDriveState(),
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
        // 上传 / 新建文件夹 / 在线预览：弹窗与系统选文件器都在共享层内部，这里只接 VM
        onCreateFolder = { name -> viewModel.createFolder(name) },
        onUploadFiles = { uris -> viewModel.uploadFiles(context.contentResolver, uris) },
        uploadProgress = viewModel.uploadProgress,
        onCancelUpload = { viewModel.cancelUpload() },
        onPreview = { viewModel.openPreview() },
        modifier = modifier
    )
}

/** UC 目录标识：dirFid（根 "0"） */
private fun UCCloudUiState.toUCDriveState(): CloudDriveUiState = when (this) {
    is UCCloudUiState.Loading -> CloudDriveUiState.Loading
    is UCCloudUiState.Error -> CloudDriveUiState.Error(message)
    is UCCloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirFid)
}
