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
import android.webkit.WebChromeClient
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
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.data.network.XunleiWebCredential
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.YunXWavyLoading
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel
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
 * 迅雷网页登录页：在应用内打开 pan.xunlei.com，由官网页面完成登录（扫码 / 账号密码 / 验证码都由它自己处理）。
 *
 * 登录完成后网页把登录态写进当前域 localStorage（`credentials_<clientId>`），本页轮询读取该对象、
 * 过一次真实接口校验后落库，用户不需要做任何额外操作；读取失败时还能手动粘贴兜底。
 *
 * 为什么值得单独做一条网页登录：迅雷对「App 通道的账号密码/短信登录」风控很严（新设备必触发 review_panel），
 * 而网页版登录走的是另一套接口，不受这套风控影响，是收不到短信/密码被拦时最稳的一条路。
 */
@Composable
fun XunleiWebLoginScreen(
    viewModel: XunleiAccountViewModel,
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

    // 手动粘贴凭据弹窗状态
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    // 登录教程弹窗：进入页面即展示一次
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    val webView = remember(webViewEpoch) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // 登录态存在 localStorage，必须开启
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // ★ 不要用 NARROW_COLUMNS：那是给移动版单列排版用的，会把桌面版 SPA 的布局压坏（实测整页空白）。
            //   桌面版页面走默认算法 + useWideViewPort/loadWithOverviewMode 自动缩放适配即可。
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // 桌面 UA：网页版登录态（credentials_* 键）只在桌面版页面写入，移动版页面是另一套
            // ★ 光改 UA 不够：必须同步 client hints（Sec-CH-UA 系），否则服务端按移动端判定，
            //   页面会退化成「迅雷云盘 / 打开APP」落地页（见 XunleiWebCredential.applyDesktopClientHints）
            settings.userAgentString = XunleiWebCredential.DESKTOP_UA
            XunleiWebCredential.applyDesktopClientHints(settings)
            settings.suppressRequestedWithHeader()
            webViewClient = object : DiagnosticWebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    DiagnosticLog.webview("page_started", url)
                    isLoading = true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    DiagnosticLog.webview("page_finished", url)
                    isLoading = false
                    // 修桌面版登录页的渲染（布局视口 + CSS 高度，见 XunleiWebCredential.DESKTOP_RENDER_FIX_JS）
                    view?.evaluateJavascript(XunleiWebCredential.DESKTOP_RENDER_FIX_JS, null)
                }

                @Deprecated("继承自 WebView 的弃用回调，旧 WebView 仍走此签名，需保留覆写")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    // 登录流程会跳 i.xunlei.com / xluser-ssl.xunlei.com，统一放行 *.xunlei.com；
                    // 出了这个域（含 http 明文页）直接拦掉，不给钓鱼页任何机会
                    return !XunleiWebCredential.isTrustedUrl(url)
                }

                override fun onRendererGone() {
                    isLoading = false
                    rendererGone = true
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl(XunleiWebCredential.LOGIN_URL)
        }
    }

    // 渲染进程崩溃时基类已先销毁过，destroyWebView 保证真正只销毁一次
    DisposableEffect(webView) { onDispose { destroyWebView(webView) } }

    BackHandler { if (!isSaving && !isSavingManual) onBack() }

    // 自动登录检测：网页把凭据写进 localStorage 就自动校验并落库，无需手动点「保存」
    rememberWebLoginAutoDetect(
        sampleCredential = { webView.evaluateJsEncoded(XunleiWebCredential.READ_SCRIPT) },
        isPlausible = { XunleiWebCredential.parse(it) != null },
        validateAndSave = { viewModel.saveWebCredential(it) },
        isPaused = { isSaving || isSavingManual || showPasteDialog || rendererGone },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )


    Scaffold(
        topBar = {
            TopAppBar(
                title = AppStrings.get(R.string.lg_xunlei_web_title),
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSavingManual) onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.get(R.string.lg_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!isSaving && !isSavingManual) showPasteDialog = true },
                        enabled = !isSaving && !isSavingManual
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = AppStrings.get(R.string.lg_manual_paste_credential),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    if (isSaving) {
                        LoginLoadingIndicator(modifier = Modifier.padding(horizontal = 12.dp))
                    } else {
                        TextButton(
                            text = AppStrings.get(R.string.lg_save),
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        // 从网页 localStorage 提取登录凭据并校验落库
                                        val raw = webView.evaluateJsEncoded(XunleiWebCredential.READ_SCRIPT)
                                        val saved = raw.isNotBlank() && viewModel.saveWebCredential(raw)
                                        if (saved) {
                                            SnackbarController.show(AppStrings.get(R.string.lg_login_success))
                                            onSaved()
                                        } else {
                                            SnackbarController.show(AppStrings.get(R.string.lg_no_login_state_web))
                                        }
                                    } finally {
                                        // 必须 finally：抛异常时「保存」按钮要能恢复可点，否则这个页面就废了
                                        isSaving = false
                                    }
                                }
                            },
                            // 渲染进程崩溃后 WebView 已销毁，取凭据必然拿到空值，别让用户点了白点
                            enabled = !isSaving && !isSavingManual && !rendererGone
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            key(webViewEpoch) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
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

    // 网页登录说明弹窗
    OverlayDialog(
        show = showTutorial,
        title = AppStrings.get(R.string.lg_web_login_instructions),
        onDismissRequest = { showTutorial = false }
    ) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.lg_xunlei_web_tutorial_1),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.get(R.string.lg_xunlei_web_tutorial_2),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.get(R.string.lg_xunlei_web_tutorial_3),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.get(R.string.lg_xunlei_web_tutorial_4),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = AppStrings.get(R.string.lg_xunlei_web_tutorial_5),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        TextButton(
            text = AppStrings.get(R.string.lg_got_it),
            onClick = { showTutorial = false },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }

    // 手动粘贴凭据弹窗
    OverlayDialog(
        show = showPasteDialog,
        title = AppStrings.get(R.string.lg_manual_paste_credential),
        onDismissRequest = { if (!isSavingManual) showPasteDialog = false }
    ) {
        Column {
            Text(
                text = AppStrings.get(
                    R.string.lg_xunlei_web_storage_hint,
                    XunleiWebCredential.STORAGE_KEY
                ),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            TextField(
                value = pasteInput,
                onValueChange = { pasteInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                label = AppStrings.get(R.string.lg_paste_credential_hint),
                useLabelAsPlaceholder = true,
                minLines = 4,
                maxLines = 8
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = AppStrings.get(R.string.lg_cancel),
                    onClick = { if (!isSavingManual) showPasteDialog = false },
                    modifier = Modifier.weight(1f),
                    enabled = !isSavingManual
                )
                Button(
                    onClick = {
                        scope.launch {
                            isSavingManual = true
                            try {
                                val saved = viewModel.saveWebCredential(pasteInput.trim())
                                if (saved) {
                                    SnackbarController.show(AppStrings.get(R.string.lg_login_success))
                                    showPasteDialog = false
                                    onSaved()
                                } else {
                                    SnackbarController.show(AppStrings.get(R.string.lg_credential_invalid))
                                }
                            } finally {
                                isSavingManual = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = pasteInput.isNotBlank() && !isSavingManual,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSavingManual) {
                        LoginLoadingIndicator()
                    } else {
                        Text(AppStrings.get(R.string.lg_save))
                    }
                }
            }
        }
    }
}
