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

package com.yunx.app.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.screens.CloudSelectChip
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 分享表单（提取码 + 有效期），供单文件分享与批量分享共用。
 *
 * 提取码策略与有效期选项来自 [DriveSpec]，本组件不含任何平台分支。
 * 有效期值以 Any? 透传给调用方：百度 0/1/7/30、UC 与迅雷 1/2/3/4、C139 与 123 null/1/7/30
 * 三套编码互不兼容，收敛会改变请求参数，因此逐条保留。
 */
@Composable
fun ShareFormSheet(
    subtitle: String,
    spec: DriveSpec,
    onBack: () -> Unit,
    onSubmit: (expiry: Any?, passcode: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var passcode by remember { mutableStateOf("") }
    var withPasscode by remember { mutableStateOf(false) }
    val options = spec.expiryOptions
    var expiry by remember { mutableStateOf(options.first().value) }
    val policy = spec.passcode

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "分享文件", subtitle = subtitle, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))

        when (policy) {
            // 百度/迅雷：必须带提取码，输入框常驻
            is PasscodePolicy.Fixed -> {
                spec.shareHint?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                TextField(
                    value = passcode,
                    onValueChange = { passcode = it.normalizePasscode(policy.length, policy.filter) },
                    modifier = Modifier.fillMaxWidth(),
                    label = policy.label,
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // UC/123：可无提取码，也可设置（设置时自动填随机码）
            is PasscodePolicy.Optional -> {
                Text(
                    text = "提取码",
                    style = MiuixTheme.textStyles.headline2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CloudSelectChip(
                        selected = !withPasscode,
                        label = "无提取码",
                        onClick = { withPasscode = false }
                    )
                    CloudSelectChip(
                        selected = withPasscode,
                        label = "设置提取码",
                        onClick = {
                            withPasscode = true
                            if (passcode.isBlank() && policy.autoGenerate) {
                                passcode = randomPasscode(policy.length)
                            }
                        }
                    )
                }
                if (withPasscode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(
                        value = passcode,
                        onValueChange = { passcode = it.normalizePasscode(policy.length) },
                        modifier = Modifier.fillMaxWidth(),
                        label = policy.label,
                        useLabelAsPlaceholder = true,
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // C139：提取码由服务端自动生成，只显示说明
            PasscodePolicy.Auto -> {
                spec.shareHint?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        Text(
            text = "有效期",
            style = MiuixTheme.textStyles.headline2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                CloudSelectChip(
                    selected = expiry == option.value,
                    label = option.label,
                    onClick = { expiry = option.value }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(expiry, if (withPasscode) passcode else "") },
            enabled = when (policy) {
                is PasscodePolicy.Fixed -> passcode.length == policy.length
                is PasscodePolicy.Optional -> !withPasscode || passcode.length == policy.length
                PasscodePolicy.Auto -> true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("创建分享", style = MiuixTheme.textStyles.button)
        }
    }
}

/** 提取码输入：限长 + 仅保留字母数字 */
private fun String.normalizePasscode(length: Int, filter: Boolean = true): String {
    val limited = take(length)
    return if (filter) limited.filter { it.isLetterOrDigit() } else limited
}
