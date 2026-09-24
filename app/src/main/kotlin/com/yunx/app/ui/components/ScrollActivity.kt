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

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * 列表滚动活跃态：正在滚动（含手指按住慢拖）为 true，停止 [debounceMs] 毫秒后回落到 false。
 *
 * 用途：滚动中隐藏悬浮 UI（返回顶部按钮、底部操作条），减少遮挡与合成开销。
 *
 * 为什么不用 snapshotFlow + debounce：debounce 会把「开始滚动」也延后一个窗口，
 * 悬浮 UI 要等停手才隐藏，反而更晚。这里用 collectLatest 表达非对称语义：
 * 上升沿立即下发 true；只有持续 false 达到窗口长度才下发 false。
 *
 * 手指按住但未移动时 isScrollInProgress 仍为 true，因此慢拖不会被误判为停止。
 */
@Composable
fun rememberScrollActivityState(
    listState: LazyListState,
    debounceMs: Long = 700
): State<Boolean> = produceState(initialValue = false, listState, debounceMs) {
    snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
        if (scrolling) {
            value = true
        } else {
            delay(debounceMs)
            value = false
        }
    }
}
