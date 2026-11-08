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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import com.yunx.app.R
import com.yunx.app.ui.drive.CloudDriveBrowser
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.DriveActions
import com.yunx.app.ui.drive.DriveSpec
import com.yunx.app.ui.drive.PasscodePolicy
import com.yunx.app.ui.viewmodel.ILanzouCloudUiState
import com.yunx.app.ui.viewmodel.ILanzouCloudViewModel
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.ScrollBehavior

private fun ilanzouDriveSpec() = DriveSpec(
    title = AppStrings.get(R.string.platform_ilanzou),
    shareDesc = AppStrings.get(R.string.csb_share_title),
    shareHint = null,
    expiryOptions = emptyList(),
    passcode = PasscodePolicy.Auto,
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

@Composable
fun ILanzouCloudScreen(
    viewModel: ILanzouCloudViewModel,
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
        shareFile = { _, _ -> },
        shareSelected = { _, _ -> },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )


    // 上传要读 ContentResolver：共享层只回传选中的 uri，实际读取由 ViewModel 完成
    val context = LocalContext.current
    CloudDriveBrowser(
        spec = ilanzouDriveSpec(),
        state = state.toILanzouDriveState(),
        moveState = moveState.toILanzouDriveState(),
        isRefreshing = viewModel.refreshing,
        multiSelectMode = viewModel.multiSelectMode,
        selected = viewModel.selected,
        operating = viewModel.isOperating,
        shareResult = null,
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
        onDismissShareResult = {},
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

private fun ILanzouCloudUiState.toILanzouDriveState(): CloudDriveUiState = when (this) {
    is ILanzouCloudUiState.Loading -> CloudDriveUiState.Loading
    is ILanzouCloudUiState.Error -> CloudDriveUiState.Error(message)
    is ILanzouCloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirId)
}
