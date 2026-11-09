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

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 迅雷网盘登录页：账号+密码登录，触发风控时切换短信验证码流程。
 * 步骤：账号密码 →（需要时）发送短信 → 输入验证码 → 完成。
 *
 * 错误提示只保留表单内联红字（与其余 5 个登录页「只用 Snackbar」不同，这里以贴近输入框为准），
 * 不再额外弹一条内容相同的全局 Snackbar，避免同一条错误重复展示。
 */
@Composable
fun XunleiLoginScreen(
    viewModel: XunleiAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onVerify: (url: String, deviceId: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val step = viewModel.loginStep
    val error = viewModel.loginError
    val smsSent = viewModel.smsSent
    val sendingSms = viewModel.sendingSms
    // 自动关闭只看「本次登录是否真的写入了账号」（见下方 LaunchedEffect）：
    // ★ 不能改成「账号非空」判定——「添加账号」时主账号本来就在库里，
    //   页面一渲染就会被判成登录成功而立刻 onSaved()，表现为登录页闪一下就关闭。
    val loginSucceeded = viewModel.loginSucceeded

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // 重发倒计时：ViewModel 记的是「冷却截止时刻」（只有真的发出去才开始算），这里只负责每秒刷新显示
    var resendCooldown by remember { mutableIntStateOf(0) }
    val cooldownUntil = viewModel.smsCooldownUntil
    LaunchedEffect(cooldownUntil) {
        while (true) {
            val left = ((cooldownUntil - System.currentTimeMillis()) / 1000).toInt()
            resendCooldown = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(1_000)
        }
    }

    // 进入页面清掉上一次的中间态：登录页可能在「安全验证」那一步被关掉，重开时那个 creditkey
    // 早已失效，直接渲染成验证步骤只会让用户提交必然失败的验证码
    LaunchedEffect(Unit) { viewModel.resetLoginStep() }

    // 登录错误提示
    LaunchedEffect(error) {
        error?.let {
            SnackbarController.show(it)
            viewModel.consumeLoginError()
        }
    }
    // 登录成功后自动关闭登录页（短信/密码/网页任一方式成功都会置位该标记）
    LaunchedEffect(loginSucceeded) {
        if (loginSucceeded) onSaved()
    }

    // 返回由 NavHost 处理（本页已是 destination），系统预测性返回动画得以保留


    Scaffold(
        topBar = {
            TopAppBar(
                title = AppStrings.get(R.string.lg_xunlei_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppStrings.get(R.string.lg_back)
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
                // 键盘避让：输入框聚焦时把内容顶到键盘上方，密码/验证码输入框不被遮挡
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (step?.needSms == true) AppStrings.get(R.string.lg_xunlei_sms_title) else AppStrings.get(R.string.lg_xunlei_title),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onBackground
            )
            Text(
                text = if (step?.needSms == true) {
                    if (smsSent) AppStrings.get(R.string.lg_xunlei_sms_sent_to, username)
                    else AppStrings.get(R.string.lg_xunlei_sms_prompt)
                } else {
                    AppStrings.get(R.string.lg_xunlei_login_desc)
                },
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            if (step == null || !step.needSms) {
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = AppStrings.get(R.string.lg_phone_or_email),
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
                    singleLine = true
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = AppStrings.get(R.string.lg_password),
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) AppStrings.get(R.string.lg_hide_password) else AppStrings.get(R.string.lg_show_password)
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (passwordVisible) KeyboardType.Text else KeyboardType.Password),
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        isSending = true
                        viewModel.login(username, password)
                        isSending = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = username.isNotBlank() && password.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSending) {
                        LoginLoadingIndicator()
                    } else {
                        Text(AppStrings.get(R.string.lg_login))
                    }
                }
            } else {
                TextField(
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = AppStrings.get(R.string.lg_sms_code),
                    leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.padding(horizontal = 12.dp)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        isSending = true
                        viewModel.loginWithSms(
                            username, smsCode,
                            step.smsCreditKey, step.smsToken
                        )
                        isSending = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = smsCode.isNotBlank() && !isSending,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isSending) {
                        LoginLoadingIndicator()
                    } else {
                        Text(AppStrings.get(R.string.lg_verify_and_login))
                    }
                }
                if (!smsSent) {
                    // 进入界面不会自动发送验证码：主按钮「发送验证码」提示用户主动获取
                    Button(
                        onClick = {
                            viewModel.sendSms(username)
                            SnackbarController.show(AppStrings.get(R.string.lg_sms_sent))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text(AppStrings.get(R.string.lg_send_code))
                    }
                } else {
                    TextButton(
                        text = AppStrings.get(R.string.lg_resend_code),
                        onClick = {
                            viewModel.sendSms(username)
                            SnackbarController.show(AppStrings.get(R.string.lg_sms_sent))
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                Text(
                    text = AppStrings.get(R.string.lg_sms_not_received_hint),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // 短信发不出时的应用内验证兜底（应用内 WebView 承载验证页；核心验证仍走自有短信流）
                if (step.reviewUrl.isNotBlank()) {
                    TextButton(
                        text = AppStrings.get(R.string.lg_verify_in_app),
                        onClick = {
                            // 用与登录请求一致的设备签名（deviceSign = div101.xxx）：
                            // 验证页会把 URL 里的 deviceid 原样当 devicesign 用，
                            // 必须与 v3/login 的 devicesign 字段一致，否则报"登录信息已过期"
                            onVerify(step.reviewUrl, com.yunx.app.data.network.XunleiDeviceFingerprint.deviceSign())
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                    Text(
                        text = AppStrings.get(R.string.lg_verify_in_app_hint),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // 未设置密码：跳转迅雷官网设置（浏览器打开）
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                text = AppStrings.get(R.string.lg_set_password),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://i.xunlei.com/xluser/validate/findpwd_acc.html")
                            )
                        )
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}
