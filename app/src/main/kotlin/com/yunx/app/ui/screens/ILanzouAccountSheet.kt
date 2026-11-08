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

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.ILanzouAccountEntity
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.R
import com.yunx.app.util.AccountNames
import com.yunx.app.ui.SnackbarController
import com.yunx.app.util.AppStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 已登录蓝奏优享账号的底部弹窗：展示用户信息、登录时间、appToken（可展开/复制），并提供多账号管理（主账号 / 备注 / 退出该账号 / 添加账号）。
 */
@Composable
fun ILanzouAccountSheet(
    account: ILanzouAccountEntity,
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showFullToken by rememberSaveable { mutableStateOf(false) }

    val tokenPreviewLimit = 200
    val tokenTruncated = account.appToken.length > tokenPreviewLimit
    val displayToken = if (showFullToken || !tokenTruncated) {
        account.appToken
    } else {
        account.appToken.take(tokenPreviewLimit) + "…"
    }
    val loginTime = remember(account.updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(account.updatedAt))
    }
    // 账号可能是手机号/邮箱等敏感信息，展示时脱敏（与其它网盘一致，不明文列出账号）；
    // 空值或旧语言写死的默认名不脱敏，直接显示当前语言的默认昵称
    val rawName = account.nickname.ifBlank { account.account }
    val displayName = if (AccountNames.isUnset(R.string.e2_ilanzou_default_nickname, rawName)) {
        AppStrings.get(R.string.e2_ilanzou_default_nickname)
    } else {
        maskAccount(rawName)
    }

    val scrollState = rememberScrollState()

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (showFullToken) Modifier.fillMaxHeight() else Modifier)
                // 纵向滚动无条件挂载：上游把 verticalScroll 从「展开全部」分支里提出来，修横屏等高度不足时
                // 内容在屏幕外又滑不动的问题；内容不足一屏时仍按内容包裹，观感不变。
                // （上游另在 ModalBottomSheet 的 sheetState 上挂了嵌套滚动；本地弹窗是 Miuix OverlayBottomSheet、
                //  没有 sheetState，故未引入。）
                .verticalScroll(scrollState)
                .padding(top = 4.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.take(1).ifBlank { AppStrings.get(R.string.csb_drive_avatar_lanzou) },
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = displayName,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium
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
                            text = AppStrings.get(R.string.sh_ilanzou_logged_in),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = AppStrings.get(R.string.sh_login_info),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column {
                    InfoRow(label = AppStrings.get(R.string.sh_login_time), value = loginTime)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "appToken",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayToken.ifBlank { "—" },
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (tokenTruncated) {
                            TextButton(
                                text = if (showFullToken) AppStrings.get(R.string.dl_collapse) else AppStrings.get(R.string.sh_expand_all),
                                onClick = { showFullToken = !showFullToken }
                            )
                        }
                        TextButton(
                            text = AppStrings.get(R.string.sh_copy),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ilanzou_token", account.appToken))
                                SnackbarController.show(AppStrings.get(R.string.sh_apptoken_copied))
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
                onEmpty = onDismiss,
                defaultNameRes = R.string.e2_ilanzou_default_nickname
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}
