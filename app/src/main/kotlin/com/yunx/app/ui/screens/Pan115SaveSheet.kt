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
import com.yunx.app.ui.viewmodel.Pan115CloudUiState
import com.yunx.app.ui.viewmodel.Pan115CloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel

/**
 * 转存到 115 网盘转存底部抽屉：浏览目标网盘目录，确认后把当前解析出的内容转存到该目录。
 */
@Composable
fun Pan115SaveSheet(
    resolveViewModel: ResolveViewModel,
    cloudViewModel: Pan115CloudViewModel,
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
        title = "转存到 115 网盘",
        targetName = resolveViewModel.saveTarget?.fname ?: "",
        state = cloudState.toDrivePickerState(),
        onDismiss = onDismiss,
        onNavigateToLevel = { cloudViewModel.navigateToLevel(it) },
        onBack = { cloudViewModel.back() },
        onOpenFolder = { cloudViewModel.openFolder(it) },
        onRetry = { cloudViewModel.loadRoot() },
        saving = resolveViewModel.isSaving,
        onConfirm = { resolveViewModel.saveToCloud(cloudState.currentDir()) },
        contentLabel = "pan115SaveState"
    )
}

private fun Pan115CloudUiState.toDrivePickerState(): CloudDriveUiState = when (this) {
    is Pan115CloudUiState.Loading -> CloudDriveUiState.Loading
    is Pan115CloudUiState.Error -> CloudDriveUiState.Error(message)
    is Pan115CloudUiState.Loaded -> CloudDriveUiState.Loaded(files, pathNames, dirId)
}

private fun Pan115CloudUiState.currentDir(): String =
    (this as? Pan115CloudUiState.Loaded)?.dirId ?: "0"
