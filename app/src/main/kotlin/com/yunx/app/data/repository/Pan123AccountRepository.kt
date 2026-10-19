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

import com.yunx.app.data.db.Pan123AccountDao
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.resolveAccountSlot
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.network.Pan123LoginResult
import kotlinx.coroutines.flow.Flow

/**
 * 123 云盘账号仓库：两条登录路径产出同一种凭证（authorToken，Bearer JWT，约 90 天过期）——
 * ① 网页登录读 yun.123pan.cn 的 localStorage；② 账号密码打原生 sign_in 接口。
 * 123 没有 refresh 接口，token 失效后只能重新登录（两条路任选）。
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
     * @param newAccount true = 网盘页「添加账号」进来的，落成新的一行；false = 覆盖当前主账号那一行
     *                   （日常重新登录、会话过期后重登都走这条，避免次次重登都堆出一个失效账号）
     */
    suspend fun saveTokenResult(token: String, newAccount: Boolean = false): SaveResult {
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
        val slot = resolveAccountSlot(DownloadPlatform.PAN123, dao.getAccount(), newAccount)
        dao.upsert(
            Pan123AccountEntity(
                id = slot.id,
                accessToken = t,
                account = "",
                nickname = nickname,
                isPrimary = slot.isPrimary,
                remark = slot.remark
            )
        )
        return SaveResult.Success
    }

    /**
     * Boolean 兼容入口（账号 ViewModel 仍按 Boolean 调用）：
     * 网络失败时抛 [SaveNetworkException]，避免界面把网络问题误报为「Token 无效」。
     * @param newAccount true = 网盘页「添加账号」进来的，落成新的一行；false = 覆盖当前主账号那一行
     */
    suspend fun saveToken(token: String, newAccount: Boolean = false): Boolean =
        when (val result = saveTokenResult(token, newAccount)) {
            SaveResult.Success -> true
            SaveResult.InvalidCredential -> false
            is SaveResult.NetworkError -> throw SaveNetworkException(result.message)
        }

    /**
     * 账号密码登录（123 原生接口 sign_in）。
     *
     * 与网页登录落库的是同一种凭证（authorToken，同源同形），区别只在「token 从哪来」：
     * 这条不需要 WebView，也不用让用户在网页里手输一次。
     *
     * @return 成功返回 null；失败返回可直接展示给用户的原因（已做过错误映射，不回显服务端原文）
     * @param newAccount true = 网盘页「添加账号」进来的，落成新的一行；false = 覆盖当前主账号那一行
     */
    suspend fun loginWithPassword(
        account: String,
        password: String,
        newAccount: Boolean = false
    ): String? =
        when (val result = api.passwordLogin(account, password)) {
            is Pan123LoginResult.Success -> {
                // sign_in 只回 token，昵称要另取一次；取不到（网络抖动）不算登录失败——
                // token 是刚签发的确实可用，昵称退化成账号名展示即可，别把到手的登录态扔掉
                val nickname = api.fetchNickname(result.token).orEmpty()
                val slot = resolveAccountSlot(DownloadPlatform.PAN123, dao.getAccount(), newAccount)
                dao.upsert(
                    Pan123AccountEntity(
                        id = slot.id,
                        accessToken = result.token,
                        account = account.trim(),
                        nickname = nickname,
                        isPrimary = slot.isPrimary,
                        remark = slot.remark
                    )
                )
                null
            }

            is Pan123LoginResult.Failure -> result.message
        }

    /**
     * 校验当前 token 是否仍有效，返回三态结果。
     * 只有确认凭证无效才清登录态；网络失败时保留登录态，避免断网瞬间被误清导致用户被迫重新登录。
     * 失效时只删**这一个账号**（[Pan123AccountDao.deleteById]），同平台其他账号保留。
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
        if (result == SaveResult.InvalidCredential) dao.deleteById(acc.id)
        return result
    }

    /** Boolean 兼容入口：网络失败以异常透传，避免被当成「token 失效」而删账号。 */
    suspend fun validate(): Boolean =
        when (val result = validateResult()) {
            SaveResult.Success -> true
            SaveResult.InvalidCredential -> false
            is SaveResult.NetworkError -> throw SaveNetworkException(result.message)
        }
}
