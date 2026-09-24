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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.drive.CloudDriveUiState
import com.yunx.app.ui.drive.CloudPickerSheet
import com.yunx.app.ui.viewmodel.QuarkCloudUiState
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel

/**
 * 转存到夸克网盘底部抽屉：浏览夸克个人网盘目录，确认后转存到当前目录。
 */
@Composable
fun SaveToCloudSheet(
    resolveViewModel: ResolveViewModel,
    cloudViewModel: QuarkCloudViewModel,
    onDismiss: () -> Unit
) {
    val cloudState by cloudViewModel.uiState.collectAsState()

    // 打开抽屉时回到根目录
    LaunchedEffect(Unit) { cloudViewModel.loadRoot() }

    // 转存结果提示（由外层 Scaffold 统一托管）
    LaunchedEffect(resolveViewModel.saveMessage) {
        resolveViewModel.saveMessage?.let {
            SnackbarController.show(it)
            resolveViewModel.consumeSaveMessage()
        }
    }

    CloudPickerSheet(
        title = "转存到夸克网盘",
        targetName = resolveViewModel.saveTarget?.fname ?: "",
        state = cloudState.toQuarkPickerState(),
        onDismiss = onDismiss,
        onNavigateToLevel = { cloudViewModel.navigateToLevel(it) },
        onBack = { cloudViewModel.back() },
        onOpenFolder = { cloudViewModel.openFolder(it) },
        onRetry = { cloudViewModel.loadRoot() },
        saving = resolveViewModel.isSaving,
        onConfirm = { resolveViewModel.saveToCloud(cloudState.currentQuarkDir()) },
        contentLabel = "saveState"
    )
}

private fun QuarkCloudUiState.toQuarkPickerState(): CloudDriveUiState = when (this) {
    is QuarkCloudUiState.Loading -> CloudDriveUiState.Loading
    is QuarkCloudUiState.Error -> CloudDriveUiState.Error(message)
    is QuarkCloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirFid)
}

private fun QuarkCloudUiState.currentQuarkDir(): String =
    (this as? QuarkCloudUiState.Loaded)?.dirFid ?: "0"
