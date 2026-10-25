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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 账号列表（多账号）：同一平台的多个账号、设主账号 / 改备注 / 退出，以及「添加账号」。
 *
 * ★ 10 个平台的账号弹窗共用这一段：这些操作只关心公共字段，所以参数用 [MultiAccountEntity]
 *   而不是各自的实体类型（各弹窗自己的凭证详情卡仍展示主账号那一条）。
 *
 * @param onSetPrimary 点某个非主账号行时调用（把它设为主账号）
 * @param onRename 改备注（传空串 = 清掉备注，回退显示昵称）
 * @param onRemove 退出单个账号
 * @param onEmpty 账号被清空时回调 —— 弹窗已经没有内容可展示，由调用方关掉它
 */
@Composable
internal fun AccountListSection(
    accounts: List<MultiAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAddAccount: () -> Unit,
    onEmpty: () -> Unit
) {
    // 最后一个账号被退出后弹窗没有内容可展示了，自动关闭
    LaunchedEffect(accounts.isEmpty()) { if (accounts.isEmpty()) onEmpty() }

    SmallTitle(text = "账号（${accounts.size}）")
    Card(modifier = Modifier.fillMaxWidth()) {
        accounts.forEach { account ->
            AccountRow(
                account = account,
                onSetPrimary = onSetPrimary,
                onRename = onRename,
                onRemove = onRemove
            )
        }
        // 添加账号：复用该平台原来的登录入口（网页登录 / 账号密码登录），作为 Card 的直接子项自动带分割线
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddAccount)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "添加账号",
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
    Text(
        text = "主账号用于列目录、转存、上传等需要单一身份的操作；" +
            "开启「多账号下载」后，下载会自动用上同一网盘的其他账号。",
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** 单条账号：昵称/备注 + 主账号徽标（点整行可切换），右侧菜单改名 / 退出 */
@Composable
private fun AccountRow(
    account: MultiAccountEntity,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    val displayName = account.displayName()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 非主账号整行可点：点一下即设为主账号（主账号行点击无动作）
            .clickable(enabled = !account.isPrimary) { onSetPrimary(account.id) }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MiuixTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.take(1),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName, style = MiuixTheme.textStyles.body1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (account.isPrimary) "主账号" else "点一下设为主账号",
                style = MiuixTheme.textStyles.footnote1,
                color = if (account.isPrimary) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                }
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "账号操作",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("重命名备注", style = MiuixTheme.textStyles.body1) },
                    onClick = {
                        menuExpanded = false
                        showRename = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("退出该账号", style = MiuixTheme.textStyles.body1) },
                    onClick = {
                        menuExpanded = false
                        showRemove = true
                    }
                )
            }
        }
    }

    if (showRename) {
        var input by remember(account.id) { mutableStateOf(account.remark) }
        WindowDialog(
            show = showRename,
            onDismissRequest = { showRename = false },
            title = "重命名备注"
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = "备注",
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                Text(
                    text = "同一网盘登录多个账号时，备注能帮你区分（清空备注则显示昵称）。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRename = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showRename = false
                            onRename(account.id, input)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存", style = MiuixTheme.textStyles.button)
                    }
                }
            }
            AppSnackbarHost(rememberGlobalSnackbarHostState())
        }
    }

    if (showRemove) {
        WindowDialog(
            show = showRemove,
            onDismissRequest = { showRemove = false },
            title = "退出该账号"
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "确定要退出「$displayName」吗？只清除这一个账号在本机的登录凭证，其他账号不受影响。",
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRemove = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showRemove = false
                            onRemove(account.id)
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
            AppSnackbarHost(rememberGlobalSnackbarHostState())
        }
    }
}

/** 账号显示名：备注优先，回退昵称，再回退占位文案 */
internal fun MultiAccountEntity.displayName(): String =
    remark.ifBlank { nickname }.ifBlank { "未命名账号" }
