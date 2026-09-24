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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 登录页保存结果桥接测试：
 * 账号 ViewModel 的方法签名固定为 Boolean，网络失败由仓库以 [SaveNetworkException] 透传，
 * 这里验证调用侧能把它还原成三态，从而给出「登录成功 / 凭证无效 / 网络异常」三种不同提示。
 */
class LoginSaveResultTest {

    @Test
    fun mapsTrueToSuccess() = runBlocking {
        assertEquals(SaveResult.Success, loginSaveResult { true })
    }

    @Test
    fun mapsFalseToInvalidCredential() = runBlocking {
        assertEquals(SaveResult.InvalidCredential, loginSaveResult { false })
    }

    @Test
    fun mapsNetworkExceptionToNetworkError() = runBlocking {
        val result = loginSaveResult { throw SaveNetworkException("网络异常，请重试") }
        assertEquals(SaveResult.NetworkError("网络异常，请重试"), result)
    }
}
