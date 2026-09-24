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

import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.data.network.C139Constants
import com.yunx.app.data.repository.SaveResult
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.C139AccountViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 139 网盘（和彩云）登录页：
 * - WebView 加载 yun.139.com，由用户手动登录（短信/验证码/风控由官网处理，统一走 [rememberLoginWebView]）；
 * - 自动登录检测：登录完成后自动从 CookieManager 提取 mail.10086.cn / yun.139.com 的 Cookie
 *   （需含 Os_SSo_Sid + RMKEY，右上角「保存」保留作手动兜底）；
 * - 支持手动粘贴 Cookie（需含 Os_SSo_Sid= 与 RMKEY=）。
 */
@Composable
fun C139LoginScreen(
    viewModel: C139AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    // 统一 WebView：关闭文件访问 / 禁止混合内容 / 允许第三方 Cookie / 错误与超时回调 / 失败重试。
    // userAgent 传 null 即沿用 WebView 默认 UA（139 移动版页面在默认 UA 下渲染稳定）
    val controller = rememberLoginWebView(
        url = C139Constants.LOGIN_URL,
        injectViewport = true
    )

    // 保存中允许返回：网络卡死时不能把用户困在登录页；离开时提示保存已取消
    val exitLogin: () -> Unit = {
        if (isSaving || isSavingManual) SnackbarController.show("已取消保存，可稍后重新登录")
        onBack()
    }

    // 自动登录检测：网页内登录完成（Cookie 出现）即自动保存登录；右上角「保存」保留作手动兜底
    rememberWebLoginAutoDetect(
        sampleCredential = { C139Constants.extractCookies { CookieManager.getInstance().getCookie(it) } },
        isPlausible = { C139Constants.isValidCookie(it) },
        validateAndSave = { loginSaveResult { viewModel.saveC139Account(it) } == SaveResult.Success },
        isPaused = { isSaving || isSavingManual || showCookieDialog },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    // 系统返回键：优先回退网页历史（登录流程多跳转），无历史才退出登录页
    BackHandler { if (!controller.goBack()) exitLogin() }

    // 全局 Snackbar 宿主
    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 16.dp)) },
        topBar = {
            TopAppBar(
                title = "139 网盘登录",
                navigationIcon = {
                    // 顶栏返回始终可点：保存中也要能退出，避免用户被困
                    IconButton(onClick = exitLogin) {
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
                            contentDescription = "手动输入凭证",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(
                            text = "保存",
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    try {
                                        val cookie = C139Constants.extractCookies {
                                            CookieManager.getInstance().getCookie(it)
                                        }
                                        // 三态：139 无网络校验，只会返回成功/凭证无效
                                        when (val result = loginSaveResult { viewModel.saveC139Account(cookie) }) {
                                            SaveResult.Success -> {
                                                SnackbarController.show("登录成功")
                                                onSaved()
                                            }
                                            SaveResult.InvalidCredential ->
                                                SnackbarController.show("未检测到登录状态，请先在网页中完成登录，再点保存")
                                            is SaveResult.NetworkError -> SnackbarController.show(result.message)
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSavingManual
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
                // 键盘避让：网页表单聚焦时把内容顶到键盘上方，输入框不被遮挡
                .imePadding()
        ) {
            AndroidView(
                factory = { controller.webView },
                modifier = Modifier.fillMaxSize()
            )
            if (controller.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            controller.failureMessage?.let { message ->
                LoginLoadFailureOverlay(message = message, onRetry = { controller.retry() })
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
                    text = "1. 在下方网页中登录 139 网盘账号（手机号）",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "2. 登录完成后将自动检测登录；若未自动登录，点右上角「保存」",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "3. 或点击「粘贴」图标，手动输入 Cookie（需含 Os_SSo_Sid= 与 RMKEY=）",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "4. 若网页空白：用系统浏览器打开 yun.139.com 登录后复制 Cookie 粘贴，效果相同",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = "5. Cookie 有效期有限，失效后需重新登录",
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

        // 手动输入凭证弹窗（各平台统一标题，字段名沿用平台自身：Cookie）
        OverlayDialog(
            show = showCookieDialog,
            title = "手动输入凭证",
            onDismissRequest = { if (!isSavingManual) showCookieDialog = false }
        ) {
            Column {
                Text(
                    text = "从网页登录态复制完整的 Cookie（需包含 Os_SSo_Sid= 与 RMKEY=）",
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
                                try {
                                    when (val result = loginSaveResult { viewModel.saveC139Account(cookieInput.trim()) }) {
                                        SaveResult.Success -> {
                                            SnackbarController.show("登录成功")
                                            showCookieDialog = false
                                            onSaved()
                                        }
                                        SaveResult.InvalidCredential ->
                                            SnackbarController.show("Cookie 不完整，请确认复制了完整的登录凭证")
                                        // 网络失败：保留弹窗与已输入内容，只提示重试
                                        is SaveResult.NetworkError -> SnackbarController.show(result.message)
                                    }
                                } finally {
                                    isSavingManual = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = cookieInput.isNotBlank() && !isSavingManual,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        if (isSavingManual) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}
