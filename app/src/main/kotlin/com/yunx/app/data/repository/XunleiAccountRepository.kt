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

import com.yunx.app.data.db.XunleiAccountDao
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.XunleiLoginStep
import java.io.IOException
import kotlinx.coroutines.flow.Flow

/**
 * 迅雷账号仓库：账号+密码登录（可能触发短信验证）→ 换 token 落库。
 */
class XunleiAccountRepository(
    private val dao: XunleiAccountDao,
    private val api: XunleiApi
) {

    fun observeAccount(): Flow<XunleiAccountEntity?> = dao.observeAccount()

    suspend fun getAccount(): XunleiAccountEntity? = dao.getAccount()

    /** 账号密码登录；返回登录步骤（needSms=true 表示需短信验证，携带 smsCreditKey/smsToken） */
    suspend fun loginWithPassword(
        username: String,
        password: String
    ): XunleiLoginStep {
        // 必须用官方真实设备 ID（devicesign 配套），否则 userinfo_expired
        val deviceId = XunleiApi.newDeviceId()
        // 官方首次登录：v3/login 不带任何 creditkey/captcha，返回 review_panel(1007) 触发短信验证
        return api.loginWithPassword(username, password, deviceId)
    }

    /** 发送短信验证码（登录触发 review_panel 后调用） */
    suspend fun sendSms(mobile: String): XunleiLoginStep {
        val deviceId = XunleiApi.newDeviceId()
        return api.sendSms(mobile, deviceId)
    }

    /**
     * 短信验证码登录并换取 token，返回三态结果。
     * 迅雷 SDK 接口在网络不通时会直接抛 IOException（与其它平台「吞异常返回 null」不同），
     * 这里统一收敛为「网络失败」态，避免异常冒泡到调用方。
     */
    suspend fun loginWithSmsResult(
        mobile: String,
        smsCode: String,
        creditKey: String,
        smsToken: String
    ): SaveResult {
        val deviceId = XunleiApi.newDeviceId()
        return try {
            val step = api.smsLogin(mobile, smsCode, creditKey, smsToken, deviceId)
            if (step.sessionId.isBlank()) return SaveResult.InvalidCredential
            // 换 token 前先 initCaptcha 拿 captcha_token（官方时序：smslogin → captcha/init → signin/token）
            val captchaToken = api.initCaptcha(deviceId, mobile) ?: ""
            val tokens = api.exchangeToken(step.sessionId, deviceId, captchaToken)
                ?: return SaveResult.InvalidCredential
            dao.upsert(
                XunleiAccountEntity(
                    id = "xunlei",
                    accessToken = tokens.first,
                    refreshToken = tokens.second,
                    deviceId = deviceId,
                    captchaToken = captchaToken,
                    nickname = step.nickname.ifBlank { "迅雷用户" }
                )
            )
            SaveResult.Success
        } catch (e: IOException) {
            SaveResult.NetworkError(ACCOUNT_NETWORK_ERROR)
        }
    }

    /**
     * Boolean 兼容入口。
     * 迅雷账号 ViewModel 自行消费结果并直接展示错误文案、且不捕获异常，因此这里不抛
     * [SaveNetworkException]：网络失败同样返回 false，由 ViewModel 的既有提示兜底。
     */
    suspend fun loginWithSms(
        mobile: String,
        smsCode: String,
        creditKey: String,
        smsToken: String
    ): Boolean = loginWithSmsResult(mobile, smsCode, creditKey, smsToken) == SaveResult.Success

    /** 密码登录成功后用 sessionID 换 token 落库，返回三态结果（网络异常收敛为网络失败态） */
    suspend fun finishLoginResult(
        step: XunleiLoginStep,
        username: String
    ): SaveResult {
        if (step.sessionId.isBlank()) return SaveResult.InvalidCredential
        val deviceId = XunleiApi.newDeviceId()
        return try {
            val captchaToken = api.initCaptcha(deviceId, username) ?: ""
            val tokens = api.exchangeToken(step.sessionId, deviceId, captchaToken)
                ?: return SaveResult.InvalidCredential
            dao.upsert(
                XunleiAccountEntity(
                    id = "xunlei",
                    accessToken = tokens.first,
                    refreshToken = tokens.second,
                    deviceId = deviceId,
                    captchaToken = captchaToken,
                    nickname = step.nickname.ifBlank { "迅雷用户" }
                )
            )
            SaveResult.Success
        } catch (e: IOException) {
            SaveResult.NetworkError(ACCOUNT_NETWORK_ERROR)
        }
    }

    /** Boolean 兼容入口（同 [loginWithSms]：不抛异常，网络失败返回 false）。 */
    suspend fun finishLogin(
        step: XunleiLoginStep,
        username: String
    ): Boolean = finishLoginResult(step, username) == SaveResult.Success

    /** 刷新 token 后更新 accessToken/refreshToken（deviceId/captchaToken 保持不变） */
    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val acc = dao.getAccount() ?: return
        dao.upsert(acc.copy(accessToken = accessToken, refreshToken = refreshToken))
    }

    suspend fun logout() {
        dao.clear()
    }
}