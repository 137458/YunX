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
import android.webkit.WebStorage
import com.yunx.app.data.db.Pan123AccountDao
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import kotlinx.coroutines.flow.Flow

/**
 * 123 云盘账号仓库：网页登录（yun.123pan.cn 的 localStorage authorToken）→ JWT 落库。
 * 凭证 = authorToken（Bearer JWT，与旧 sign_in 接口返回的 data.token 同源同形，约 90 天过期）；
 * token 失效时重新走网页登录（无 refresh 接口）。
 */
class Pan123AccountRepository(
    private val dao: Pan123AccountDao,
    private val api: Pan123Api
) {

    fun observeAccount(): Flow<Pan123AccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): Pan123AccountEntity? = dao.getAccount()

    /**
     * 网页登录凭证（authorToken）校验并落库，返回三态结果。
     * 旧实现把网络失败与 token 失效混为一谈（都返回 false，界面统一提示「Token 无效」）；
     * 现在服务端没返回昵称时会区分链路不可达（网络失败）与凭证被拒（凭证无效）。
     * @param token 123 云盘网页 localStorage 的 authorToken（Bearer JWT）
     */
    suspend fun saveTokenResult(token: String): SaveResult {
        val t = token.trim()
        if (t.isBlank()) return SaveResult.InvalidCredential
        // 网页登录拿不到手机号：account 留空，账号页展示时回退昵称
        val nickname = api.fetchNickname(t)
        if (nickname == null) {
            return SaveResultPolicy.of(
                credentialShapeValid = true,
                nickname = null,
                transportReachable = probeTransportReachable(
                    Pan123Constants.USER_INFO_URL,
                    Pan123Constants.WEB_UA
                )
            )
        }
        dao.upsert(
            Pan123AccountEntity(
                id = "pan123",
                accessToken = t,
                account = "",
                nickname = nickname
            )
        )
        return SaveResult.Success
    }

    /**
     * Boolean 兼容入口（账号 ViewModel 仍按 Boolean 调用）：
     * 网络失败时抛 [SaveNetworkException]，避免界面把网络问题误报为「Token 无效」。
     */
    suspend fun saveToken(token: String): Boolean =
        when (val result = saveTokenResult(token)) {
            SaveResult.Success -> true
            SaveResult.InvalidCredential -> false
            is SaveResult.NetworkError -> throw SaveNetworkException(result.message)
        }

    /**
     * 校验当前 token 是否仍有效，返回三态结果。
     * 只有确认凭证无效才清库；网络失败时保留登录态，避免断网瞬间被误清导致用户被迫重新登录。
     */
    suspend fun validateResult(): SaveResult {
        val acc = dao.getAccount() ?: return SaveResult.InvalidCredential
        val nickname = api.fetchNickname(acc.accessToken)
        if (nickname != null) return SaveResult.Success
        val result = SaveResultPolicy.of(
            credentialShapeValid = true,
            nickname = null,
            transportReachable = probeTransportReachable(
                Pan123Constants.USER_INFO_URL,
                Pan123Constants.WEB_UA
            )
        )
        if (result == SaveResult.InvalidCredential) dao.clear()
        return result
    }

    /** Boolean 兼容入口：网络失败以异常透传，避免被当成「token 失效」而清库。 */
    suspend fun validate(): Boolean =
        when (val result = validateResult()) {
            SaveResult.Success -> true
            SaveResult.InvalidCredential -> false
            is SaveResult.NetworkError -> throw SaveNetworkException(result.message)
        }

    /**
     * 退出登录：清库 + 清理 WebView 登录态（Cookie 与 localStorage/DOM 存储）。
     * 网页登录态保存在 WebView 里；不清理的话，用户再次打开登录页时，页面残留的
     * authorToken 会被自动登录检测直接登回旧账号，导致「退出登录」形同虚设。
     * WebView 存储方法须在带 Looper 的线程（主线程）调用——本方法由 viewModelScope（Main）执行。
     */
    suspend fun logout() {
        // 清 Cookie：顺带清掉 WebView 里其他平台的会话（与百度/夸克等登出行为一致，均为全量清理）
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        // 清 localStorage/DOM 存储：authorToken 就存在这里
        runCatching { WebStorage.getInstance().deleteAllData() }
        dao.clear()
    }
}
