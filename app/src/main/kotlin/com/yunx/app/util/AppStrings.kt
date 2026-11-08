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

package com.yunx.app.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.yunx.app.ui.theme.LocaleController
import java.util.Locale

/**
 * 「不是 Composable、却要取多语言文案」的全局入口。
 *
 * 适用场景：ViewModel / 下载引擎 / 通知 / 数据层错误消息 —— 这些地方拿不到 Compose 的
 * `stringResource`，也不能直接用 `context.getString`（那只会取系统语言，不是用户在设置里选的
 * 应用内语言）。这里复用 [LocaleController.wrap] 按已保存语言包装 Context 再取字符串。
 *
 * 由 `YunXApp.onCreate` 调用 [install] 装配，与项目既有的 `DiagnosticLog.install` /
 * `DownloadDebugLog.install` 等保持同一惯例。
 *
 * ★ 未装配时（例如 JVM 单元测试）返回空串，绝不抛异常，避免把测试拖垮。
 */
object AppStrings {

    @Volatile
    private var ctx: Context? = null

    @Volatile
    private var wrapped: Context? = null

    @Volatile
    private var wrappedTag: String? = null

    /** JVM 单测注入的资源表替身；为 null 时走真实资源。 */
    @Volatile
    private var testResolver: ((Int, Array<out Any>) -> String)? = null

    fun install(context: Context) {
        ctx = context.applicationContext
        wrapped = null
        wrappedTag = null
    }

    /** 仅供 JVM 单测：注入资源表替身，测试结束后传 null 复位。 */
    fun installResolverForTest(resolver: ((Int, Array<out Any>) -> String)?) {
        testResolver = resolver
    }

    /** 取多语言字符串；未装配或取不到时返回空串。 */
    fun get(@StringRes resId: Int, vararg args: Any): String {
        testResolver?.let { return it(resId, args) }
        val base = ctx ?: return ""
        return runCatching {
            val tag = base.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
                .getString("app_language", "").orEmpty()
            val target = if (wrapped == null || wrappedTag != tag) {
                LocaleController.wrap(base).also {
                    wrapped = it
                    wrappedTag = tag
                }
            } else {
                wrapped ?: base
            }
            if (args.isEmpty()) target.getString(resId) else target.getString(resId, *args)
        }.getOrDefault("")
    }

    /**
     * 取指定语言下的字符串。用于识别存量数据里按旧语言写死的默认值（例如账号默认昵称）：
     * 把各语言的取值收集起来做比对，而不是只看当前语言。
     */
    fun getInLanguage(@StringRes resId: Int, languageTag: String): String {
        testResolver?.let { return it(resId, emptyArray()) }
        val base = ctx ?: return ""
        return runCatching {
            val locale = Locale.forLanguageTag(languageTag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLocales(LocaleList(locale))
            base.createConfigurationContext(config).getString(resId)
        }.getOrDefault("")
    }
}
