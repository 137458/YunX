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
import com.yunx.app.data.db.BaiduAccountDao
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.network.HttpClients
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 账号保存/校验三态结果：成功 / 凭证无效 / 网络失败。
 * 6 个账号仓库共用；定义放在本文件是因为本次改动范围只包含 *AccountRepository.kt，无法新建共享文件。
 */
sealed interface SaveResult {
    /** 校验通过且已落库 */
    data object Success : SaveResult

    /** 凭证形态不合法，或服务端明确拒绝（需要用户检查输入或重新登录） */
    data object InvalidCredential : SaveResult

    /** 网络不可达或请求失败：可重试，调用侧应保留用户已输入内容 */
    data class NetworkError(val message: String) : SaveResult
}

/** 网络失败统一提示文案 */
internal const val ACCOUNT_NETWORK_ERROR = "网络异常，请重试"

/**
 * 网络失败异常：仓库对外的 Boolean 兼容入口用它把「网络失败」与「凭证无效」区分开。
 * 账号 ViewModel（ui/viewmodel）不在本次改动范围内，其 save* 方法签名固定为 Boolean，
 * 调用侧（登录页）通过捕获本异常还原出第三态。
 */
class SaveNetworkException(message: String) : Exception(message)

/**
 * 三态判定规则（纯函数，便于单元测试）：
 * - 凭证形态不合法 → 凭证无效；
 * - 服务端返回昵称 → 成功；
 * - 服务端未返回昵称时：链路可达说明是服务端拒绝（凭证无效），链路不可达才是网络失败。
 */
internal object SaveResultPolicy {
    fun of(credentialShapeValid: Boolean, nickname: String?, transportReachable: Boolean): SaveResult = when {
        !credentialShapeValid -> SaveResult.InvalidCredential
        nickname != null -> SaveResult.Success
        transportReachable -> SaveResult.InvalidCredential
        else -> SaveResult.NetworkError(ACCOUNT_NETWORK_ERROR)
    }
}

/**
 * 探测专用客户端：复用全局客户端的连接池与线程池，只收紧超时。
 * 探测仅用于区分「服务端拒绝凭证」与「链路不通」，不能让保存结果长时间等待。
 */
private val probeClient: OkHttpClient by lazy {
    HttpClients.apiClient().newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}

/**
 * 传输层可达性探测。
 * 各平台 API 的 fetchNickname 内部用 runCatching 把 IOException 吞成 null，仓库层拿不到
 * 「服务端拒绝」与「链路不通」的区分；因此在 API 返回 null 时，用同一 OkHttp 客户端对平台
 * 账号接口发一个无凭证的轻量 GET：拿到任意 HTTP 响应（含 4xx/5xx）即链路可达，抛 IOException
 * 即网络失败。
 */
internal suspend fun probeTransportReachable(url: String, userAgent: String?): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url).get()
            if (!userAgent.isNullOrBlank()) builder.header("User-Agent", userAgent)
            probeClient.newCall(builder.build()).execute().use { true }
        }.getOrDefault(false)
    }

/** 百度账号接口同域地址，仅用于传输层探测（无凭证 GET，判断链路是否可达） */
private const val BAIDU_PROBE_URL = "https://pan.baidu.com/"

/**
 * 百度账号数据仓库：Room 持久化 + 网络验证（gettemplatevariable 拿昵称）。
 */
class BaiduAccountRepository(
    private val dao: BaiduAccountDao,
    private val api: BaiduApi
) {

    fun observeAccount(): Flow<BaiduAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): BaiduAccountEntity? = dao.getAccount()

    /** 退出登录：清理 WebView Cookie + 清除本地记录 */
    suspend fun logoutBaidu() {
        withContext(Dispatchers.IO) {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
        dao.clear()
    }

    /**
     * 校验 Cookie 有效性（需含 BDUSS）并落库，返回三态结果。
     * 旧实现把「服务端校验失败」也当成登录成功（拿不到昵称就回退默认昵称并返回 true），
     * 会写入无效登录态；现在服务端没返回昵称即视为未通过校验，并区分网络失败。
     */
    suspend fun saveBaiduAccountResult(cookie: String): SaveResult {
        if (!BaiduConstants.isValidCookie(cookie)) return SaveResult.InvalidCredential
        val nickname = api.fetchNickname(cookie)
        if (nickname == null) {
            // 服务端没给昵称：链路可达说明是服务端拒绝凭证，链路不可达才是网络失败
            return SaveResultPolicy.of(
                credentialShapeValid = true,
                nickname = null,
                transportReachable = probeTransportReachable(BAIDU_PROBE_URL, BaiduConstants.UA_WEB)
            )
        }
        dao.upsert(
            BaiduAccountEntity(
                id = "baidu",
                cookie = cookie,
                nickname = nickname
            )
        )
        return SaveResult.Success
    }

    /**
     * Boolean 兼容入口（账号 ViewModel 仍按 Boolean 调用）：
     * 网络失败时抛 [SaveNetworkException]，避免被调用侧误判为「凭证无效」而提示检查 Cookie。
     */
    suspend fun saveBaiduAccount(cookie: String): Boolean =
        when (val result = saveBaiduAccountResult(cookie)) {
            SaveResult.Success -> true
            SaveResult.InvalidCredential -> false
            is SaveResult.NetworkError -> throw SaveNetworkException(result.message)
        }
}