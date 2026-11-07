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

package com.yunx.app.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.network.model.ShareFile

/**
 * 网盘长按多选的共享状态：10 个网盘浏览 ViewModel 原先各持一份完全相同的实现，
 * 规则一旦在各处漂移就会表现为「选中的文件删不掉」这类静默问题。
 * 全选按「数量相等」判满：列表被刷新后按数量清空，行为与既有实现保持一致。
 */
class CloudMultiSelect {
    var isActive by mutableStateOf(false)
        private set

    private val entries = mutableStateListOf<ShareFile>()

    val selected: List<ShareFile> get() = entries

    fun enter(file: ShareFile) {
        isActive = true
        entries.clear()
        entries.add(file)
    }

    fun toggle(file: ShareFile) {
        if (entries.contains(file)) entries.remove(file) else entries.add(file)
    }

    fun toggleAll(files: List<ShareFile>) {
        if (entries.size == files.size) {
            entries.clear()
        } else {
            entries.clear()
            entries.addAll(files)
        }
    }

    fun exit() {
        isActive = false
        entries.clear()
    }
}
