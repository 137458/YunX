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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.data.network.BaiduConstants
import com.yunx.app.data.repository.SaveResult
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
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
 * 百度网盘登录页：
 * - WebView 加载百度网盘官网，由用户手动登录（统一走 [rememberLoginWebView]）；
 * - 自动登录检测：网页内登录完成后自动提取 Cookie（关键字段 BDUSS/STOKEN）并校验落库（右上角「保存」保留作手动兜底）；
 * - 支持手动粘贴 Cookie（需含 BDUSS=）。
 */
@Composable
fun BaiduLoginScreen(
    viewModel: BaiduAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var isSavingManual by remember { mutableStateOf(false) }

    var showTutorial by remember { mutableStateOf(false) }
    // 进入登录页先弹风控提示，确认后再弹登录教程（避免两个弹窗叠层）
    var showRiskDialog by remember { mutableStateOf(true) }
    // 风控提示必须勾选「已阅读」才能继续（未勾选时「继续」不可点、点外部也关不掉）
    var riskAcknowledged by remember { mutableStateOf(false) }

    // 统一 WebView：关闭文件访问 / 禁止混合内容 / 允许第三方 Cookie / 错误与超时回调 / 失败重试
    val controller = rememberLoginWebView(
        url = BaiduConstants.LOGIN_URL,
        userAgent = BaiduConstants.UA_WEB,
        injectViewport = true
    )

    // 保存中允许返回：网络卡死时不能把用户困在登录页；离开时提示保存已取消
    val exitLogin: () -> Unit = {
        if (isSaving || isSavingManual) SnackbarController.show(AppStrings.get(R.string.lg_save_cancelled))
        onBack()
    }

    // 自动登录检测：网页内登录完成（BDUSS 出现并通过接口校验）即自动保存登录；右上角「保存」保留作手动兜底
    rememberWebLoginAutoDetect(
        sampleCredential = { CookieManager.getInstance().getCookie(BaiduConstants.COOKIE_DOMAIN).orEmpty() },
        isPlausible = { BaiduConstants.isValidCookie(it) },
        validateAndSave = { loginSaveResult { viewModel.saveBaiduAccount(it) } == SaveResult.Success },
        isPaused = { isSaving || isSavingManual || showCookieDialog || controller.isRendererGone },
        onInFlightChange = { isSaving = it },
        onAutoSaved = onSaved
    )

    // 系统返回键：优先回退网页历史（登录流程多跳转），无历史才退出登录页
    BackHandler { if (!controller.goBack()) exitLogin() }


    Scaffold(
        topBar = {
            TopAppBar(
                title = AppStrings.get(R.string.lg_baidu_title),
                navigationIcon = {
                    // 顶栏返回始终可点：保存中也要能退出，避免用户被困
                    IconButton(onClick = exitLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.get(R.string.lg_back)
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
                            contentDescription = AppStrings.get(R.string.lg_manual_credential),
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
                                        val cookie = CookieManager.getInstance()
                                            .getCookie(BaiduConstants.COOKIE_DOMAIN)
                                            .orEmpty()
                                        // 三态：成功才关闭登录页；凭证无效提示检查；网络失败保留输入、留在本页
                                        when (val result = loginSaveResult { viewModel.saveBaiduAccount(cookie) }) {
                                            SaveResult.Success -> {
                                                SnackbarController.show(AppStrings.get(R.string.lg_login_success))
                                                onSaved()
                                            }
                                            SaveResult.InvalidCredential ->
                                                SnackbarController.show(AppStrings.get(R.string.lg_no_login_state_save))
                                            is SaveResult.NetworkError -> SnackbarController.show(result.message)
                                        }
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSavingManual && !controller.isRendererGone
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
            // 渲染进程崩溃后 controller 会重建 WebView，靠 generation 换视图（否则 AndroidView 仍持旧实例）
            key(controller.generation) {
                AndroidView(
                    factory = { controller.webView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (controller.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            controller.failureMessage?.let { message ->
                LoginLoadFailureOverlay(message = message, onRetry = controller.retryOrNull)
            }
        }

        // 风控温馨提示弹窗（进入登录页优先展示）
        // 上游 v1.3.1 起：必须勾选「我已阅读并了解上述风险」才能继续，且不允许点外部/返回键关闭
        OverlayDialog(
            show = showRiskDialog,
            title = AppStrings.get(R.string.lg_baidu_risk_title),
            onDismissRequest = {}
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.get(R.string.lg_baidu_risk_1),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(R.string.lg_baidu_risk_2),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = AppStrings.get(R.string.lg_baidu_risk_3),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                // 勾选行整行可点（只点小方框对小屏不友好）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { riskAcknowledged = !riskAcknowledged }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = ToggleableState(riskAcknowledged),
                        onClick = { riskAcknowledged = !riskAcknowledged }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get(R.string.lg_baidu_risk_ack),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
            TextButton(
                text = AppStrings.get(R.string.lg_continue),
                onClick = {
                    showRiskDialog = false
                    showTutorial = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = riskAcknowledged,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }

        // 登录教程弹窗
        OverlayDialog(
            show = showTutorial,
            title = AppStrings.get(R.string.lg_login_tutorial),
            onDismissRequest = { showTutorial = false }
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.get(R.string.lg_baidu_tutorial_1),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(R.string.lg_baidu_tutorial_2),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(R.string.lg_baidu_tutorial_3),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(R.string.lg_baidu_tutorial_4),
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

        // 手动输入凭证弹窗（各平台统一标题，字段名沿用平台自身：Cookie）
        OverlayDialog(
            show = showCookieDialog,
            title = AppStrings.get(R.string.lg_manual_credential),
            onDismissRequest = { if (!isSavingManual) showCookieDialog = false }
        ) {
            Column {
                Text(
                    text = AppStrings.get(R.string.lg_baidu_cookie_hint),
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
                    label = AppStrings.get(R.string.lg_paste_cookie_hint),
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
                        onClick = { if (!isSavingManual) showCookieDialog = false },
                        modifier = Modifier.weight(1f),
                        enabled = !isSavingManual
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                isSavingManual = true
                                try {
                                    when (val result = loginSaveResult { viewModel.saveBaiduAccount(cookieInput.trim()) }) {
                                        SaveResult.Success -> {
                                            SnackbarController.show(AppStrings.get(R.string.lg_login_success))
                                            showCookieDialog = false
                                            onSaved()
                                        }
                                        SaveResult.InvalidCredential ->
                                            SnackbarController.show(AppStrings.get(R.string.lg_cookie_incomplete))
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
                            LoginLoadingIndicator()
                        } else {
                            Text(AppStrings.get(R.string.lg_save))
                        }
                    }
                }
            }
        }
    }
}
