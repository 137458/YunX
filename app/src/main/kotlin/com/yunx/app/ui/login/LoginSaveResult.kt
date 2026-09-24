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

package com.yunx.app.ui.login

import com.yunx.app.data.repository.SaveNetworkException
import com.yunx.app.data.repository.SaveResult

/**
 * 把账号仓库的 Boolean 兼容入口还原为三态结果。
 *
 * 账号 ViewModel（ui/viewmodel）不在本次改动范围内，其 save* 方法签名固定为 Boolean；
 * 仓库在「网络失败」时改抛 [SaveNetworkException]，这里在调用侧还原出第三态，
 * 让登录页能区分「登录成功 / 凭证无效 / 网络异常」三种提示。
 */
internal suspend fun loginSaveResult(block: suspend () -> Boolean): SaveResult =
    try {
        if (block()) SaveResult.Success else SaveResult.InvalidCredential
    } catch (e: SaveNetworkException) {
        SaveResult.NetworkError(e.message ?: "网络异常，请重试")
    }
