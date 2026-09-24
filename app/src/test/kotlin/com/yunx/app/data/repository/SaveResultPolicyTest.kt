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

package com.yunx.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 账号保存三态判定规则测试：
 * 覆盖「凭证形态非法 / 服务端返回昵称 / 服务端拒绝 / 链路不可达」四种组合，
 * 确保「服务端校验失败」不会再被当成登录成功，且网络失败与凭证无效能区分开。
 */
class SaveResultPolicyTest {

    @Test
    fun rejectsCredentialWithBadShape() {
        assertEquals(
            SaveResult.InvalidCredential,
            SaveResultPolicy.of(credentialShapeValid = false, nickname = "任意昵称", transportReachable = true)
        )
    }

    @Test
    fun succeedsWhenServerReturnsNickname() {
        assertEquals(
            SaveResult.Success,
            SaveResultPolicy.of(credentialShapeValid = true, nickname = "百度用户", transportReachable = true)
        )
    }

    @Test
    fun treatsServerRejectionAsInvalidCredential() {
        // 形态合法但服务端没给昵称：链路可达说明是服务端拒绝，不能算成功
        assertEquals(
            SaveResult.InvalidCredential,
            SaveResultPolicy.of(credentialShapeValid = true, nickname = null, transportReachable = true)
        )
    }

    @Test
    fun treatsUnreachableTransportAsNetworkError() {
        assertEquals(
            SaveResult.NetworkError(ACCOUNT_NETWORK_ERROR),
            SaveResultPolicy.of(credentialShapeValid = true, nickname = null, transportReachable = false)
        )
    }
}
