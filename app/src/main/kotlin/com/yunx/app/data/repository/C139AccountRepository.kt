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

import android.webkit.CookieManager
import com.yunx.app.data.db.C139AccountDao
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.network.C139Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 139 网盘账号数据仓库：Room 持久化 + Cookie 校验。
 * 登录态 = mail.10086.cn 的 Os_SSo_Sid + RMKEY（WebView 登录后提取）。
 */
class C139AccountRepository(
    private val dao: C139AccountDao
) {

    fun observeAccount(): Flow<C139AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): C139AccountEntity? = dao.getAccount()

    /** 退出登录：清理 WebView Cookie + 清除本地记录 */
    suspend fun logoutC139() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    /**
     * 校验 139 Cookie 有效性（Os_SSo_Sid+RMKEY 或 authorization 任一成立）并落库，返回三态结果。
     * 139 登录态完全在本地解析，不发网络校验请求，因此不会产生「网络失败」态；
     * 旧实现把「服务端校验失败」当成功（拿不到账号就回退默认昵称并返回 true），现在统一由
     * [C139Constants.isValidCookie] 决定，形态不合法即视为凭证无效。
     */
    suspend fun saveC139AccountResult(cookie: String): SaveResult {
        if (!C139Constants.isValidCookie(cookie)) return SaveResult.InvalidCredential
        val nickname = C139Constants.extractAccount(cookie) ?: "139用户"
        val authorization = C139Constants.extractAuthorization(cookie).orEmpty()
        dao.upsert(
            C139AccountEntity(
                id = "c139",
                cookie = cookie,
                nickname = nickname,
                authorization = authorization
            )
        )
        return SaveResult.Success
    }

    /**
     * Boolean 兼容入口（账号 ViewModel 仍按 Boolean 调用）。
     * 139 无网络校验，不存在网络失败态，故直接按成功/凭证无效返回。
     */
    suspend fun saveC139Account(cookie: String): Boolean =
        saveC139AccountResult(cookie) == SaveResult.Success
}