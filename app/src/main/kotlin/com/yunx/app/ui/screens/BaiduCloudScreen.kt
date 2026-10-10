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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.drive.CloudDriveBrowser
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.DriveActions
import com.yunx.app.ui.drive.DriveExpiryOption
import com.yunx.app.ui.drive.DriveSpec
import com.yunx.app.ui.drive.PasscodePolicy
import com.yunx.app.ui.viewmodel.BaiduCloudUiState
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 百度非会员限速阈值：>300MB 提示 */
private const val BAIDU_LIMIT_BYTES = 300L * 1024 * 1024

/** 百度网盘平台差异：文案 + 有效期编码 0/1/7/30（默认 0）+ 必带 4 位提取码 */
private val baiduDriveSpec = DriveSpec(
    title = "百度网盘",
    shareDesc = "生成分享链接（带提取码）",
    shareHint = "百度分享必须带 4 位提取码",
    expiryOptions = listOf<DriveExpiryOption<Any?>>(
        DriveExpiryOption("永久有效", 0),
        DriveExpiryOption("1 天", 1),
        DriveExpiryOption("7 天", 7),
        DriveExpiryOption("30 天", 30)
    ),
    passcode = PasscodePolicy.Fixed(label = "提取码（4 位字母数字）"),
    folderIcon = Icons.AutoMirrored.Outlined.DriveFileMove
)

/**
 * 百度网盘云盘浏览页。
 *
 * 界面结构与其余四个网盘完全一致，这里只做两件事：
 * 1. 把 [BaiduCloudUiState] 映射成通用 [CloudDriveUiState]；
 * 2. 把 ViewModel 操作绑定成 [DriveActions]，并保留百度特有的 >300MB 限速拦截。
 */
@Composable
fun BaiduCloudScreen(
    viewModel: BaiduCloudViewModel,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    onDownloadStarted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val moveState by viewModel.moveUiState.collectAsState()
    val driveState = state.toBaiduDriveState()
    val driveMoveState = moveState.toBaiduDriveState()

    // 百度非会员 >300MB 限速提示：记住「不再显示」，单文件/批量/文件夹下载前拦截
    val settingsRepo = remember { SettingsRepository(context) }
    var limitHintDismissed by remember { mutableStateOf(settingsRepo.baiduLimitHintDismissed) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<(() -> Unit)?>(null) }

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
        shareFile = { expiry, passcode -> viewModel.shareFile(expiry as Int, passcode) },
        shareSelected = { expiry, passcode -> viewModel.shareSelected(expiry as Int, passcode) },
        downloadFile = { viewModel.downloadFile() },
        downloadFolder = { viewModel.downloadFolder() },
        downloadSelected = { viewModel.downloadSelected() }
    )

    CloudDriveBrowser(
        spec = baiduDriveSpec,
        state = driveState,
        moveState = driveMoveState,
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
        requestDownload = { files, proceed ->
            if (!limitHintDismissed && files.any { it.fsize > BAIDU_LIMIT_BYTES }) {
                pendingDownload = proceed
                showLimitDialog = true
            } else {
                proceed()
            }
        },
        modifier = modifier
    )

    if (showLimitDialog) {
        var neverShow by remember { mutableStateOf(false) }
        WindowDialog(
            show = true,
            onDismissRequest = { showLimitDialog = false },
            title = "下载大文件提示"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "百度网盘非会员超过300MB会被限速，下载速度可能较慢。是否继续下载？",
                    style = MiuixTheme.textStyles.body1
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { neverShow = !neverShow }
                ) {
                    Checkbox(
                        state = ToggleableState(neverShow),
                        onClick = { neverShow = !neverShow }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("不再显示此提示", style = MiuixTheme.textStyles.body2)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showLimitDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showLimitDialog = false
                            if (neverShow) {
                                settingsRepo.baiduLimitHintDismissed = true
                                limitHintDismissed = true
                            }
                            pendingDownload?.invoke()
                            pendingDownload = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text("继续下载", style = MiuixTheme.textStyles.button)
                    }
                }
            }
            AppSnackbarHost(rememberGlobalSnackbarHostState())
        }
    }
}

/** 百度目录标识使用绝对路径（根为 "/"） */
private fun BaiduCloudUiState.toBaiduDriveState(): CloudDriveUiState = when (this) {
    is BaiduCloudUiState.Loading -> CloudDriveUiState.Loading
    is BaiduCloudUiState.Error -> CloudDriveUiState.Error(message)
    is BaiduCloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirPath)
}
