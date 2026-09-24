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

import android.webkit.WebView
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
import com.yunx.app.data.network.Pan123Constants
import com.yunx.app.data.repository.SaveResult
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
 * 123 云盘登录页（网页登录方案，与夸克/百度等一致）：
 * - WebView 打开官网个人盘主页 [Pan123Constants.WEB_LOGIN_URL]，由用户手动登录（统一走 [rememberLoginWebView]）；
 * - 登录成功后网页 SPA 会把 Bearer JWT 写入当前域 localStorage，键名 authorToken（旧账号密码登录的 data.token 同源同形）；
 * - 右上角「保存」/自动登录检测均从 localStorage 提取该值，经 user/info 接口校验后落库。
 */
@Composable
fun Pan123LoginScreen(
    viewModel: Pan123AccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // 手动输入凭证弹窗状态
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    // 登录教程弹窗：进入页面即展示一次
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    // 统一 WebView：关闭文件访问 / 禁止混合内容 / 允许第三方 Cookie / 错误与超时回调 / 失败重试
    val controller = rememberLoginWebView(
        url = Pan123Constants.WEB_LOGIN_URL,
        userAgent = Pan123Constants.WEB_UA,
        injectViewport = true
    )

    // 保存中允许返回：网络卡死时不能把用户困在登录页；离开时提示保存已取消
    val exitLogin: () -> Unit = {
        if (isSaving || isSavingManual) SnackbarController.show("已取消保存，可稍后重新登录")
        onBack()
    }

    // 自动登录检测：网页登录完成（authorToken 写入 localStorage）即自动提取并校验登录，无需手动点「保存」
    rememberWebLoginAutoDetect(
        sampleCredential = { controller.webView.readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY) },
        isPlausible = { it.isNotBlank() },
        validateAndSave = { loginSaveResult { viewModel.saveToken(it) } == SaveResult.Success },
        isPaused = { isSaving || isSavingManual || showTokenDialog },
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
                title = "123 云盘登录",
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
                        onClick = { if (!isSaving && !isSavingManual) showTokenDialog = true },
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
                                        // 提取网页 localStorage 的 authorToken 作为登录凭证
                                        val token = controller.webView
                                            .readLocalStorageValue(Pan123Constants.LOCAL_STORAGE_TOKEN_KEY)
                                        // 三态：成功才关闭登录页；凭证无效提示检查；网络失败保留输入、留在本页
                                        val result = if (token.isBlank()) {
                                            SaveResult.InvalidCredential
                                        } else {
                                            loginSaveResult { viewModel.saveToken(token) }
                                        }
                                        when (result) {
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
                    text = "1. 在下方网页中登录 123 云盘账号（支持账号密码 / 短信验证码）",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "2. 登录完成后将自动检测并登录，无需手动操作",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "3. 若自动登录未触发，可点右上角「保存」手动提取（读取网页 localStorage 的 authorToken）",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "4. 或点击「粘贴」图标，手动粘贴 authorToken",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "5. Token 长期有效，失效后需重新登录",
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

        // 手动输入凭证弹窗（各平台统一标题，字段名沿用平台自身：authorToken）
        OverlayDialog(
            show = showTokenDialog,
            title = "手动输入凭证",
            onDismissRequest = { if (!isSavingManual) showTokenDialog = false }
        ) {
            Column {
                Text(
                    text = "登录 123 云盘网页后，复制其 localStorage 中的 authorToken 值粘贴到这里（可用浏览器开发者工具查看）",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                TextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    label = "粘贴 authorToken…",
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
                        onClick = { if (!isSavingManual) showTokenDialog = false },
                        modifier = Modifier.weight(1f),
                        enabled = !isSavingManual
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSavingManual = true
                                try {
                                    when (val result = loginSaveResult { viewModel.saveToken(tokenInput.trim()) }) {
                                        SaveResult.Success -> {
                                            SnackbarController.show("登录成功")
                                            showTokenDialog = false
                                            onSaved()
                                        }
                                        SaveResult.InvalidCredential ->
                                            SnackbarController.show("Token 不完整，请确认复制了完整的登录令牌")
                                        // 网络失败：保留弹窗与已输入内容，只提示重试
                                        is SaveResult.NetworkError -> SnackbarController.show(result.message)
                                    }
                                } finally {
                                    isSavingManual = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = tokenInput.isNotBlank() && !isSavingManual,
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

/**
 * 读取当前 WebView 页面 localStorage 中 [key] 的值（123 云盘登录态存于 authorToken）。
 * ⚠️ 依赖 123 站点私有实现（键名 authorToken / 值为裸 JWT），官网改版可能失效——失效时用户可走「粘贴 Token」兜底。
 * JS 侧用 encodeURIComponent 包一层返回，避免 JWT 特殊字符干扰 evaluateJavascript 的 JSON 返回值解析；
 * 页面未就绪 / 跨域（如还停留在登录跳转中间页）时返回空串，由调用方继续轮询。
 */
private suspend fun WebView.readLocalStorageValue(key: String): String =
    withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            try {
                evaluateJavascript(
                    "(function(){try{var v=localStorage.getItem('" + key + "');" +
                        "return v===null?'':encodeURIComponent(v)}catch(e){return ''}})()"
                ) { result ->
                    val raw = result?.trim() ?: ""
                    val value = when {
                        raw.isEmpty() || raw == "\"\"" || raw == "null" -> ""
                        raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                            raw.substring(1, raw.length - 1)
                        else -> raw
                    }
                    val decoded = if (value.isBlank()) "" else
                        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
                    if (cont.isActive) cont.resume(decoded)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume("")
            }
        }
    } ?: ""
