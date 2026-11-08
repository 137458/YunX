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
import com.yunx.app.data.network.model.ShareExpire
import com.yunx.app.R
import com.yunx.app.ui.drive.CloudDriveBrowser
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.DriveActions
import com.yunx.app.ui.drive.DriveExpiryOption
import com.yunx.app.ui.drive.DriveSpec
import com.yunx.app.ui.drive.PasscodePolicy
import com.yunx.app.ui.viewmodel.C139CloudUiState
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.ScrollBehavior

/** 平台差异：文案 + 有效期编码 + 提取码策略 */
private fun c139DriveSpec() = DriveSpec(
    title = AppStrings.get(R.string.platform_c139),
    shareDesc = AppStrings.get(R.string.csb_c139_share_title),
    shareHint = AppStrings.get(R.string.csb_c139_share_hint),
    expiryOptions = listOf<DriveExpiryOption<Any?>>(
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_forever), ShareExpire.FOREVER),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_1day), ShareExpire.ONE_DAY),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_7days), ShareExpire.SEVEN_DAYS),
        DriveExpiryOption(AppStrings.get(R.string.csa_expire_30days), ShareExpire.THIRTY_DAYS)
    ),
    passcode = PasscodePolicy.Auto,
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

@Composable
fun C139CloudScreen(
    viewModel: C139CloudViewModel,
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
        shareFile = { expiry, _ -> viewModel.shareFile(expiredType = expiry as Int) },
        shareSelected = { expiry, _ -> viewModel.shareSelected(expiredType = expiry as Int) },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )


    // 上传要读 ContentResolver：共享层只回传选中的 uri，实际读取由 ViewModel 完成
    val context = LocalContext.current
    CloudDriveBrowser(
        spec = c139DriveSpec(),
        state = state.toC139DriveState(),
        moveState = moveState.toC139DriveState(),
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

/** C139 目录标识：dirId（根 ""） */
private fun C139CloudUiState.toC139DriveState(): CloudDriveUiState = when (this) {
    is C139CloudUiState.Loading -> CloudDriveUiState.Loading
    is C139CloudUiState.Error -> CloudDriveUiState.Error(message)
    is C139CloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirId)
}
