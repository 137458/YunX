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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.GuangYaAccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 验证码重发倒计时（秒），与迅雷短信流程保持同一观感。 */
private const val SMS_COUNTDOWN_SECONDS = 60

/**
 * 光鸭云盘登录页：
 * - 默认账号密码登录（文档 §2.5）：/v1/auth/signin（内部先初始化人机验证）；
 * - 登录按键下方提供「验证码登录」入口，切换到手机号短信验证码流程（文档 §2.6-§2.8）：
 *   发送验证码 → 校验换 verification_token → 老用户 signin / 新用户自动 signup。
 */
@Composable
fun GuangYaLoginScreen(
    viewModel: GuangYaAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    // ---------- 短信验证码登录状态 ----------
    // smsMode：密码登录 ↔ 验证码登录 两个表单互斥展示（入口在密码登录按键下方）
    var smsMode by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var smsCode by rememberSaveable { mutableStateOf("") }
    // 本次验证码对应的 verification_id：手机号变更即失效（必须重新获取，避免用旧 id 登录）
    var verificationId by rememberSaveable { mutableStateOf("") }
    // 是否老用户：由发码响应决定，登录时据此走 signin / signup
    var isExistingUser by rememberSaveable { mutableStateOf(false) }
    var countdown by rememberSaveable { mutableStateOf(0) }
    var isSendingSms by remember { mutableStateOf(false) }

    // 重发倒计时：每秒递减，到 0 才允许再次获取（与迅雷短信流程一致）
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }


    BackHandler(enabled = !isSaving && !isSendingSms) { onBack() }

    /** 发送验证码：成功后记录 verification_id / is_user 并启动 60s 倒计时 */
    val sendSmsCode: () -> Unit = {
        scope.launch {
            isSendingSms = true
            error = null
            when (val result = viewModel.sendSms(phone.trim())) {
                is GuangYaAccountViewModel.GuangYaSmsResult.Success -> {
                    verificationId = result.verificationId
                    isExistingUser = result.isUser
                    countdown = SMS_COUNTDOWN_SECONDS
                    SnackbarController.show("验证码已发送")
                }
                is GuangYaAccountViewModel.GuangYaSmsResult.Failure -> error = result.message
            }
            isSendingSms = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "光鸭云盘登录",
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving && !isSendingSms) onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTutorial = true }, enabled = !isSaving && !isSendingSms) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "登录教程",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (smsMode) "使用手机号接收短信验证码登录 / 注册（新用户自动注册）"
                else "使用光鸭云盘账号密码登录（如账号未设置密码，请到官网完成设置）",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            if (!smsMode) {
                TextField(
                    value = account,
                    onValueChange = {
                        account = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "账号",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    enabled = !isSaving
                )

                TextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "密码",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    enabled = !isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )

                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            error = null
                            val (ok, msg) = viewModel.login(account.trim(), password)
                            isSaving = false
                            if (ok) {
                                SnackbarController.show("登录成功")
                                onSaved()
                            } else {
                                error = msg
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = account.isNotBlank() && password.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录中…")
                    } else {
                        Text("登录")
                    }
                }

                // 登录按键下方的「验证码登录」入口：切到手机号短信验证码表单
                TextButton(
                    text = "验证码登录",
                    onClick = {
                        smsMode = true
                        error = null
                    },
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                TextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                        error = null
                        // 手机号变更后旧验证码失效，必须重新获取
                        if (verificationId.isNotBlank() || countdown > 0) {
                            verificationId = ""
                            countdown = 0
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    useLabelAsPlaceholder = true,
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    enabled = !isSaving
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = smsCode,
                        onValueChange = {
                            smsCode = it
                            error = null
                        },
                        modifier = Modifier.weight(1f),
                        label = "短信验证码",
                        useLabelAsPlaceholder = true,
                        leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isSaving
                    )
                    Button(
                        onClick = sendSmsCode,
                        enabled = phone.isNotBlank() && countdown == 0 && !isSendingSms,
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        if (isSendingSms) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (countdown > 0) "${countdown}s 后重发" else "获取验证码",
                                maxLines = 1
                            )
                        }
                    }
                }

                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            error = null
                            val (ok, msg) = viewModel.smsLogin(
                                phone.trim(), verificationId, smsCode.trim(), isExistingUser
                            )
                            isSaving = false
                            if (ok) {
                                SnackbarController.show("登录成功")
                                onSaved()
                            } else {
                                error = msg
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = phone.isNotBlank() && smsCode.isNotBlank() && verificationId.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("登录中…")
                    } else {
                        Text("登录 / 注册")
                    }
                }

                TextButton(
                    text = "返回密码登录",
                    onClick = {
                        smsMode = false
                        error = null
                    },
                    enabled = !isSaving,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    // 登录说明弹窗
    OverlayDialog(
        show = showTutorial,
        title = "登录说明",
        onDismissRequest = { showTutorial = false }
    ) {
        Column(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "1. 输入光鸭云盘账号与密码后点击「登录」。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "2. 也可点「验证码登录」，用手机号接收短信验证码登录（新用户自动注册）。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "3. 登录成功后 access / refresh 令牌与设备标识会加密保存在本机。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "4. 若提示需要安全验证，请重试或改用其它登录方式。",
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
}
