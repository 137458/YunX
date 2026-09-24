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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Logout
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
import com.yunx.app.ui.SnackbarController
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 账号抽屉「登录信息」里的一行 */
data class CloudInfoRow(val label: String, val value: String)

/**
 * 账号抽屉通用件：头像首字 + 昵称 + 平台标签 + 信息行 + 密钥行（可展开/复制）+ 退出登录（二次确认）。
 *
 * 退出登录确认沿用 OverlayDialog：六个账号抽屉原本都用 OverlayDialog，
 * 且账号抽屉自身是 OverlayBottomSheet、内部不再嵌套其它 Overlay，
 * 不存在「删除确认」那种抽屉内嵌套 Overlay 的冲突，换 WindowDialog 只会改变观感与返回键行为。
 */
@Composable
fun CloudAccountSheet(
    nickname: String,
    platformLabel: String,
    infoRows: List<CloudInfoRow>,
    secretLabel: String,
    secretValue: String,
    /** 退出登录确认文案：各平台对清除对象的描述不同（Cookie / Token） */
    logoutMessage: String,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secretVisible: Boolean = true
) {
    val context = LocalContext.current
    var showFullSecret by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val secretTruncated = secretVisible && secretValue.length > SECRET_PREVIEW_LIMIT
    val displaySecret = if (showFullSecret || !secretTruncated) {
        secretValue
    } else {
        secretValue.take(SECRET_PREVIEW_LIMIT) + "…"
    }

    val scrollState = rememberScrollState()

    OverlayBottomSheet(
        show = true,
        onDismissRequest = {
            showFullSecret = false
            showLogoutConfirm = false
            onDismiss()
        }
    ) {
        Column(
            modifier = modifier
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
                            text = nickname.take(1),
                            style = MiuixTheme.textStyles.title3,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimaryVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 昵称可能很长，限 2 行省略，避免把退出登录按钮顶出可视区
                    Text(
                        text = nickname,
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
                            text = "$platformLabel · 已登录",
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
                    infoRows.forEachIndexed { index, row ->
                        if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                        CloudInfoRowView(label = row.label, value = row.value)
                    }
                    if (secretVisible) {
                        if (infoRows.isNotEmpty()) Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = secretLabel,
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
                                    copyToClipboard(context, secretValue)
                                    SnackbarController.show("$secretLabel 已复制")
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.error,
                    contentColor = MiuixTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onError
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录", style = MiuixTheme.textStyles.button)
            }
        }
    }

    if (showLogoutConfirm) {
        OverlayDialog(
            show = showLogoutConfirm,
            onDismissRequest = { showLogoutConfirm = false },
            title = "退出登录"
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = logoutMessage,
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showLogoutConfirm = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showLogoutConfirm = false
                            onLogout()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        )
                    ) {
                        Text("退出", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/** 信息行：标签定宽 + 值（限 2 行省略，避免长昵称/长设备号撑破卡片） */
@Composable
private fun CloudInfoRowView(label: String, value: String) {
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

/** 登录时间统一格式：六个账号抽屉原本都使用同一格式 */
fun formatCloudLoginTime(updatedAt: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(updatedAt))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("cloud_secret", text))
}
