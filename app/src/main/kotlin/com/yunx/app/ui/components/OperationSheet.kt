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

package com.yunx.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

internal val LocalFileOperationBusy = staticCompositionLocalOf { false }

/** 「busy 期间禁止关闭」的句柄：[hide] 收起面板（退场动画由 show=false 驱动），供动作执行前调用。 */
internal class OperationSheetState {
    internal var hideCallback: (() -> Unit)? = null

    /** 先收起面板再执行动作（避免弹窗瞬间消失、与紧接着弹出的下一个弹窗叠在一起） */
    fun hide() {
        hideCallback?.invoke()
    }
}

/** busy 语义仅用于「面板打开期间禁关」，miuix 的 OverlayBottomSheet 无状态对象，句柄退化成普通记忆实例。 */
@Composable
internal fun rememberOperationSheetState(busy: Boolean): OperationSheetState =
    remember { OperationSheetState() }

/**
 * 文件操作抽屉的统一外壳（miuix OverlayBottomSheet）。
 *
 * @param busy 操作执行中：禁止下滑 / 返回键 / 点外部关闭（请求已发出、面板先消失会造成状态错乱），
 *   同时经 [LocalFileOperationBusy] 下发，行级点击可用它禁用
 */
@Composable
internal fun OperationSheet(
    busy: Boolean,
    onDismiss: () -> Unit,
    sheetState: OperationSheetState = rememberOperationSheetState(busy),
    content: @Composable ColumnScope.() -> Unit
) {
    val currentBusy by rememberUpdatedState(busy)
    var visible by remember { mutableStateOf(true) }

    DisposableEffect(sheetState) {
        sheetState.hideCallback = {
            if (!currentBusy) visible = false
        }
        onDispose { sheetState.hideCallback = null }
    }

    OverlayBottomSheet(
        show = visible,
        allowDismiss = !busy,
        onDismissRequest = { if (!busy) onDismiss() }
    ) {
        CompositionLocalProvider(LocalFileOperationBusy provides busy) {
            Column { content() }
            // 弹窗是独立 Overlay 窗口，需自带 Snackbar 宿主
            AppSnackbarHost(rememberGlobalSnackbarHostState())
        }
    }
}
