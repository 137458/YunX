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

package com.yunx.app.util.webview

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import java.util.Collections
import java.util.WeakHashMap

/*
 * WebView 的平台级助手：登录页（ui.login）与无头解析通道（data.network.video）共用。
 * ★ 放在 util 而不是 ui.login：无头解析在数据层，若反向 import ui 包，数据层就被 UI 绑死
 *   （编译期单向依赖也便于以后把数据层单独测试/复用）。
 */

/**
 * 渲染进程崩溃后的收尾：把 WebView 从视图树摘下并销毁。
 *
 * ★ 必须在 `onRenderProcessGone` 返回 true 之前调用：不销毁的话，崩溃的渲染进程会一直挂在这个
 *   不可用的 WebView 上，每次绘制都可能再次拖垮宿主；而销毁是官方在返回 true 之后要求的动作。
 */
internal fun handleRendererProcessGone(view: WebView?) {
    runCatching { (view?.parent as? ViewGroup)?.removeView(view) }
    destroyWebView(view)
}

/** 已销毁的实例：崩溃回调与 Compose `onDispose` 会对同一个 WebView 各销毁一次，靠它去重。 */
private val destroyedWebViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

/**
 * 幂等销毁 WebView。
 *
 * ★ 为什么需要幂等：渲染进程崩溃时基类会销毁一次，随后 `DisposableEffect` 的 `onDispose` 还会再销毁
 *   一次 —— 对同一个实例重复 `destroy()` 属于未定义行为（可能触发 native 崩溃），`runCatching` 只能
 *   挡住 Java 层异常，挡不住 native 层的。
 */
internal fun destroyWebView(view: WebView?) {
    if (view == null) return
    val firstTime = synchronized(destroyedWebViews) { destroyedWebViews.add(view) }
    if (firstTime) view.destroy()
}

/**
 * 把 WebView 的**客户端提示**（`Sec-CH-UA` / `Sec-CH-UA-Mobile` / `Sec-CH-UA-Platform`，以及页面里的
 * `navigator.userAgentData`）对齐到刚设置的那条 UA。
 *
 * ★ 为什么必须做：Android WebView 默认发出的 client hints 里带着 `"Android WebView"` 品牌、平台写死
 *   Android。只要用 `WebSettings.userAgentString` 把 UA 改成别的（例如桌面 Chrome），服务端就会看到
 *   一份**自相矛盾的指纹**：「UA 说自己是 Chrome，client hints 却说自己是 WebView」——
 *   普通站点不在意，但登录页背后的风控会据此判定「不是浏览器环境」。
 *   （迅雷那边同理，见 `XunleiWebCredential.applyDesktopClientHints`。）
 * ★ 品牌顺序与版本号取自 [userAgent] 里的 Chrome 大版本：client hints 与 UA 的版本**不允许冲突**，
 *   写死会造成新的矛盾指纹。
 * ★ 依赖 WebView 的 `USER_AGENT_METADATA` 特性，不支持时静默跳过（页面回退到仅靠 UA 判定）。
 */
internal fun WebSettings.alignClientHintsWith(userAgent: String) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return
    val major = Regex("Chrome/(\\d+)").find(userAgent)?.groupValues?.get(1) ?: "131"
    val full = "$major.0.0.0"
    val brandVersions = listOf(
        Triple("Google Chrome", major, full),
        Triple("Chromium", major, full),
        Triple("Not_A Brand", "24", "24.0.0.0")
    ).map { (brand, brandMajor, brandFull) ->
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand(brand)
            .setMajorVersion(brandMajor)
            .setFullVersion(brandFull)
            .build()
    }
    val isAndroid = userAgent.contains("Android")
    val isIos = userAgent.contains("iPhone") || userAgent.contains("iPad")
    val metadata = UserAgentMetadata.Builder()
        .setBrandVersionList(brandVersions)
        .setFullVersion(full)
        .setPlatform(if (isAndroid) "Android" else if (isIos) "iOS" else "Windows")
        .setPlatformVersion(if (isAndroid) "14.0.0" else if (isIos) "17.0.0" else "10.0.0")
        .setArchitecture(if (isAndroid || isIos) "" else "x86")
        // 机型跟着 UA 走（UA 里写了 Pixel 8 就写 Pixel 8，两处保持一致）
        .setModel(if (isAndroid) "Pixel 8" else "")
        .setMobile(isAndroid || isIos || userAgent.contains("Mobile"))
        .setWow64(false)
        .build()
    WebSettingsCompat.setUserAgentMetadata(this, metadata)
}
