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

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunx.app.util.LogRedactor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TAG = "LoginWebView"

/** 加载超时：WebView 弱网下可能既不回调完成也不回调错误，超过该时间即置失败态，避免进度条永久转圈 */
private const val LOAD_TIMEOUT_MS = 20_000L

/** 渲染进程崩溃后的自动重载上限：超过则进入失败态，避免「崩溃 → 重载 → 再崩溃」死循环 */
private const val MAX_RENDER_CRASH_RETRY = 2

private const val LOAD_TIMEOUT_MESSAGE = "加载超时，请检查网络后重试"
private const val LOAD_ERROR_MESSAGE = "页面加载失败，请检查网络后重试"

/** 覆盖页面 viewport：适配屏幕宽度并允许双指缩放（部分官网页面缺失 viewport 或限制了缩放） */
private const val VIEWPORT_SCRIPT =
    "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
        "var c='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
        "if(m){m.setAttribute('content',c);}else{var n=document.createElement('meta');" +
        "n.name='viewport';n.content=c;document.head.appendChild(n);}" +
        "window.dispatchEvent(new Event('resize'));})()"

/**
 * 登录 WebView 统一配置：6 个承载登录页的 WebView 共用，避免每页各写一份导致加固项遗漏。
 */
internal object LoginWebViewDefaults {

    /**
     * 应用统一设置：
     * - 关闭文件/内容访问：登录页只走 http(s)，关掉可减少页面读取本地文件的面；
     * - mixedContentMode = NEVER_ALLOW：禁止 https 页面加载 http 子资源（防中间人注入）；
     * - 允许第三方 Cookie：各平台登录会跨 passport/SSO 域名，不开启则登录态写不进当前域；
     * - UA 由各平台按自身页面要求传入（移动版/桌面版差异）。
     */
    fun apply(webView: WebView, userAgent: String?) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (!userAgent.isNullOrBlank()) settings.userAgentString = userAgent
        webView.setInitialScale(0)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
}

/**
 * 登录 WebView 控制器：把「加载中 / 加载失败 / 回退网页历史」等状态从各登录页收拢到一处。
 *
 * 创建后立即加载 [initialUrl]；主文档错误、HTTP 4xx-5xx、加载超时、渲染进程反复崩溃都会置
 * [failureMessage]，登录页据此显示「加载失败 + 重试」覆盖层，而不是只留一个永久转圈的进度条。
 */
@Stable
class LoginWebViewController internal constructor(
    context: Context,
    private val initialUrl: String,
    userAgent: String?,
    private val injectViewport: Boolean,
    private val allowedHosts: Set<String>
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 加载超时任务：触发后置失败态 */
    private val timeoutTask = Runnable {
        if (isLoading) markFailure(LOAD_TIMEOUT_MESSAGE)
    }

    /** 页面是否仍在加载（顶部进度条据此显示） */
    var isLoading by mutableStateOf(true)
        private set

    /** 失败提示；非空即进入失败态并显示覆盖层 */
    var failureMessage by mutableStateOf<String?>(null)
        private set

    /** 渲染进程崩溃后的自动重载次数（上限见 MAX_RENDER_CRASH_RETRY） */
    private var renderCrashRetries = 0

    val webView: WebView = WebView(context).apply {
        LoginWebViewDefaults.apply(this, userAgent)
        webChromeClient = WebChromeClient()
        webViewClient = buildClient()
        loadUrl(initialUrl)
    }

    /** 重试：清空失败态并重新加载当前页（reload 保留已填表单；无历史时退回初始地址） */
    fun retry() {
        failureMessage = null
        isLoading = true
        renderCrashRetries = 0
        scheduleTimeout()
        webView.post {
            if (webView.url.isNullOrBlank()) webView.loadUrl(initialUrl) else webView.reload()
        }
    }

    /** 回退网页历史；无历史返回 false，由调用方决定退出登录页 */
    fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    /** 释放 WebView（渲染进程资源昂贵，页面销毁必须回收） */
    fun destroy() {
        mainHandler.removeCallbacks(timeoutTask)
        webView.destroy()
    }

    private fun markFailure(message: String) {
        isLoading = false
        failureMessage = message
        mainHandler.removeCallbacks(timeoutTask)
    }

    private fun scheduleTimeout() {
        mainHandler.removeCallbacks(timeoutTask)
        mainHandler.postDelayed(timeoutTask, LOAD_TIMEOUT_MS)
    }

    private fun buildClient(): WebViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            isLoading = true
            failureMessage = null
            scheduleTimeout()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            isLoading = false
            mainHandler.removeCallbacks(timeoutTask)
            if (injectViewport) view?.evaluateJavascript(VIEWPORT_SCRIPT, null)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            // 只把主文档失败当作加载失败：图片/统计等子资源失败不影响页面可用性
            if (request?.isForMainFrame != true) return
            Log.e(TAG, "onReceivedError: code=${error?.errorCode} desc=${error?.description} url=${LogRedactor.url(request.url.toString())}")
            markFailure(LOAD_ERROR_MESSAGE)
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            if (request?.isForMainFrame != true) return
            val code = errorResponse?.statusCode ?: return
            if (code >= 400) {
                Log.e(TAG, "onReceivedHttpError: status=$code url=${LogRedactor.url(request.url.toString())}")
                markFailure("服务返回异常（HTTP $code），请稍后重试")
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
            // 返回 true 表示已处理，避免系统直接杀掉宿主进程
            Log.e(TAG, "onRenderProcessGone: didCrash=${detail?.didCrash()}")
            if (renderCrashRetries < MAX_RENDER_CRASH_RETRY) {
                renderCrashRetries++
                isLoading = true
                failureMessage = null
                scheduleTimeout()
                view?.post { view.reload() }
            } else {
                markFailure("页面渲染进程反复崩溃，请稍后重试或改用手动输入凭证")
            }
            return true
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            // 白名单为空表示不限制（登录常跨 passport/SSO 域名，默认放开以免阻断登录流程）
            if (allowedHosts.isEmpty()) return false
            val host = request?.url?.host?.lowercase() ?: return true
            return allowedHosts.none { host == it || host.endsWith(".$it") }
        }
    }

    init {
        // 兜底计时：即使 WebView 始终不回调 onPageStarted（页面卡在建立连接阶段），也会按时进入失败态
        scheduleTimeout()
    }
}

/**
 * 创建并记忆登录用 WebView（统一配置 + 错误回调 + 超时 + 历史回退），页面销毁时自动释放。
 *
 * @param url 初始加载地址
 * @param userAgent 平台指定 UA；null 表示沿用 WebView 默认
 * @param injectViewport 是否在加载完成后注入 viewport 覆盖脚本
 * @param allowedHosts 域名白名单；空集表示不限制（默认值，避免阻断跨域登录）
 */
@Composable
fun rememberLoginWebView(
    url: String,
    userAgent: String? = null,
    injectViewport: Boolean = false,
    allowedHosts: Set<String> = emptySet()
): LoginWebViewController {
    val context = LocalContext.current
    val controller = remember {
        LoginWebViewController(context, url, userAgent, injectViewport, allowedHosts)
    }
    DisposableEffect(controller) {
        onDispose { controller.destroy() }
    }
    return controller
}

/**
 * 加载失败覆盖层：加载失败/超时/渲染进程反复崩溃时覆盖 WebView，提供明确的「加载失败 + 重试」出口。
 */
@Composable
fun LoginLoadFailureOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "加载失败",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text("重试")
            }
        }
    }
}
