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

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.data.network.Pan115Constants
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.YunXWavyLoading
import com.yunx.app.ui.viewmodel.Pan115AccountViewModel
import com.yunx.app.util.DiagnosticLog
import com.yunx.app.util.webview.destroyWebView
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 115 网盘登录页（网页登录方案，与夸克/百度/123 一致）：
 * - WebView 打开官网 [Pan115Constants.WEB_LOGIN_URL]，由用户手动登录（扫码/短信/密码都由官网处理）；
 * - 登录成功后 115 把登录态写在 115.com 域的 Cookie 里（UID/CID/SEID/KID，其中 SEID/KID 为 HttpOnly，
 *   Android 的 CookieManager 能读到）；
 * - 右上角「保存」/自动登录检测都从 CookieManager 提取整串 Cookie，经 `GET /user/info` 校验后落库。
 */
@Composable
fun Pan115LoginScreen(
    viewModel: Pan115AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    // 渲染进程崩溃后旧 WebView 不可再用，只能重建：epoch 变化时 remember 会造一个新的
    var webViewEpoch by remember { mutableIntStateOf(0) }
    var rendererGone by remember { mutableStateOf(false) }
    // 重建预算：页面级状态，离开本页即销毁，重新进入重新计数
    var restartBudget by remember { mutableStateOf(RendererRestartBudget()) }

    // 手动输入 Cookie 弹窗状态
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    // 登录教程弹窗：进入页面即展示一次
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    val webView = remember(webViewEpoch) {
        WebView(context).apply {
            LoginWebViewDefaults.apply(this, Pan115Constants.WEB_UA)
            // 桌面 UA：115 网页版是桌面站点，且接口请求头也用同一 UA，避免风控
            settings.suppressRequestedWithHeader()
            webViewClient = object : DiagnosticWebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // 115 网页里的「打开 App / 客户端」按钮会跳自定义 scheme，WebView 内无法处理，直接拦截
                    val url = request?.url?.toString().orEmpty()
                    return !url.startsWith("http://") && !url.startsWith("https://")
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    DiagnosticLog.webview("page_started", url)
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    DiagnosticLog.webview("page_finished", url)
                    isLoading = false
                    // 强制覆盖页面 viewport：适配屏幕宽度 + 允许双指缩放（与 controller 共用同一份脚本）
                    view?.applyViewportOverride()
                }
                override fun onRendererGone() {
                    isLoading = false
                    rendererGone = true
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(Pan115Constants.WEB_LOGIN_URL)
        }
    }

    // 页面销毁时释放 WebView；崩溃路径上基类已销毁过，destroyWebView 保证真正只销毁一次
    DisposableEffect(webView) {
        onDispose { destroyWebView(webView) }
    }

    // 系统返回键 → 返回主页（保存中禁用）
    BackHandler(enabled = !isSaving && !isSavingManual) { onBack() }

    // 自动登录检测：把 CookieManager 里的登录 Cookie 与本地已存值比对，变化即校验入库
    rememberWebLoginAutoDetect(
        sampleCredential = {
            Pan115Constants.extractCookies { CookieManager.getInstance().getCookie(it) }
        },
        isPlausible = { Pan115Constants.isValidCookie(it) },
        validateAndSave = { viewModel.saveCookie(it) },
        isPaused = { isSaving || isSavingManual || showCookieDialog || rendererGone },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = "115网盘登录",
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showCookieDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "手动输入 Cookie",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    if (isSaving) {
                        LoginLoadingIndicator(modifier = Modifier.padding(horizontal = 12.dp))
                    } else {
                        TextButton(
                            text = "保存",
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    // 读取 WebView CookieManager 中的登录 Cookie 作为登录凭证
                                    val cookie = Pan115Constants.extractCookies {
                                        CookieManager.getInstance().getCookie(it)
                                    }
                                    val saved = if (!Pan115Constants.isValidCookie(cookie)) {
                                        false
                                    } else {
                                        viewModel.saveCookie(cookie)
                                    }
                                    isSaving = false
                                    if (saved) {
                                        SnackbarController.show("登录成功")
                                        onSaved()
                                    } else {
                                        SnackbarController.show("未检测到登录态，请先完成登录")
                                    }
                                }
                            },
                            enabled = !isSaving && !isSavingManual && !rendererGone
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            key(webViewEpoch) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isLoading) {
                YunXWavyLoading(modifier = Modifier.fillMaxWidth())
            }
            if (rendererGone) {
                LoginLoadFailureOverlay(
                    message = rendererCrashMessage(restartBudget),
                    onRetry = if (restartBudget.canRetry) {
                        {
                            restartBudget = restartBudget.consume()
                            rendererGone = false
                            isLoading = true
                            webViewEpoch++
                        }
                    } else null
                )
            }
        }
    }

    // 登录教程弹窗
    OverlayDialog(
        show = showTutorial,
        title = "登录教程",
        onDismissRequest = { showTutorial = false }
    ) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "1. 在下方网页中登录 115 账号（支持扫码 / 短信 / 账号密码）",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "2. 登录完成后将自动检测并登录，无需手动操作",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "3. 若自动登录未触发，可点右上角「保存」手动提取（读取网页 Cookie）",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "4. 或点击「粘贴」图标，手动粘贴整串 Cookie",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "5. 115 的 Cookie 会定期轮换，失效后重新登录即可",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        TextButton(
            text = "知道了",
            onClick = { showTutorial = false },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }

    // 手动输入 Cookie 弹窗
    OverlayDialog(
        show = showCookieDialog,
        title = "手动输入 Cookie",
        onDismissRequest = { if (!isSavingManual) showCookieDialog = false }
    ) {
        Column {
            Text(
                text = "登录 115 网页版后，复制浏览器中的 Cookie 粘贴到这里（需包含 UID 与 SEID）",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            TextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                label = "粘贴 Cookie…",
                useLabelAsPlaceholder = true,
                minLines = 4,
                maxLines = 8
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { if (!isSavingManual) showCookieDialog = false },
                    modifier = Modifier.weight(1f),
                    enabled = !isSavingManual
                )
                Button(
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            val saved = viewModel.saveCookie(cookieInput.trim())
                            isSavingManual = false
                            if (saved) {
                                SnackbarController.show("登录成功")
                                showCookieDialog = false
                                onSaved()
                            } else {
                                SnackbarController.show("Cookie 无效或已过期，请检查是否包含 UID 与 SEID")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = cookieInput.isNotBlank() && !isSavingManual,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSavingManual) {
                        LoginLoadingIndicator()
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}
