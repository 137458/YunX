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

package com.yunx.app.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 收藏页点「解析」后要交给主框架的一次解析请求。 */
data class ResolveRequest(val link: String, val pwd: String)

/**
 * 跨 destination 的进程内事件通道（同 `PreviewLauncher` 的思路）。
 *
 * 收藏页迁出主框架后，它的「解析」动作仍要落到主框架的解析链路与 Tab 上：
 * 页面只发请求并回退，主框架订阅后切到解析页并发起解析。
 */
object PendingResolve {
    private val _request = MutableStateFlow<ResolveRequest?>(null)
    val request: StateFlow<ResolveRequest?> = _request.asStateFlow()

    fun request(link: String, pwd: String) {
        _request.value = ResolveRequest(link, pwd)
    }

    fun consume() {
        _request.value = null
    }
}
