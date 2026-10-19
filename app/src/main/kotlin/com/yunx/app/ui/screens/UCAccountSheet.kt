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

package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.drive.formatCloudLoginTime
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 已登录 UC 账号的底部弹窗：展示用户信息、登录信息、密钥（可展开 / 复制），
 * 并提供多账号管理（主账号 / 备注 / 退出该账号 / 添加账号）。
 */
@Composable
fun UCAccountSheet(
    account: UCAccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showFullSecret by rememberSaveable { mutableStateOf(false) }

    val secretTruncated = account.cookie.length > SECRET_PREVIEW_LIMIT
    val displaySecret = if (showFullSecret || !secretTruncated) {
        account.cookie
    } else {
        account.cookie.take(SECRET_PREVIEW_LIMIT) + "…"
    }
    val loginTime = remember(account.updatedAt) { formatCloudLoginTime(account.updatedAt) }
    val scrollState = rememberScrollState()

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showFullSecret) {
                        Modifier
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                    } else {
                        Modifier
                    }
                )
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            // 用户简要信息：头像首字 + 昵称 + 平台标签
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.primaryVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = account.nickname.take(1),
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimaryVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 昵称可能很长，限 2 行省略，避免把账号列表顶出可视区
                    Text(
                        text = account.nickname,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MiuixTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UC网盘 · 已登录",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "登录信息",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column {
                    InfoRow(label = "登录时间", value = loginTime)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cookie",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displaySecret,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (secretTruncated) {
                            TextButton(
                                text = if (showFullSecret) "收起" else "展开全部",
                                onClick = { showFullSecret = !showFullSecret }
                            )
                        }
                        TextButton(
                            text = "复制",
                            onClick = {
                                copyToClipboard(context, account.cookie, "uc_cookie")
                                SnackbarController.show("Cookie 已复制")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 多账号：同一网盘可以同时登录多个账号，这里管理它们（主账号 / 备注 / 退出 / 添加）
            AccountListSection(
                accounts = accounts,
                onSetPrimary = onSetPrimary,
                onRename = onRename,
                onRemove = onRemove,
                onAddAccount = onAddAccount,
                onEmpty = onDismiss
            )
        }
    }
}

/** 信息行：标签定宽 + 值（限 2 行省略，避免长昵称 / 长设备号撑破卡片） */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 密钥预览长度：与各平台原实现一致 */
private const val SECRET_PREVIEW_LIMIT = 200

private fun copyToClipboard(context: Context, text: String, clipLabel: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
}
