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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.VideoAccountEntity
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.ui.items.logoRes
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
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 已登录视频平台的账号弹窗：账号列表（设主账号 / 改备注 / 退出）+ 添加账号 + 退出全部账号。
 *
 * Miuix 规范重构：OverlayBottomSheet、Card 自带内边框分割线、SmallTitle、WindowDialog。
 *
 * @param accounts 该平台的全部账号（主账号在前，由 DAO 排序保证）
 * @param onRemoveAll 退出该平台全部账号（本机凭证全清）
 */
@Composable
internal fun VideoAccountSheet(
    platform: VideoPlatform,
    accounts: List<VideoAccountEntity>,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onRemoveAll: () -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRemoveAll by remember { mutableStateOf(false) }

    // 最后一个账号被退出后弹窗已无内容可展示，自动关闭
    LaunchedEffect(accounts.isEmpty()) { if (accounts.isEmpty()) onDismiss() }

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            // 头部：平台 Logo + 平台名 + 登录态
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(platform.logoRes()),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = platform.displayName,
                        style = MiuixTheme.textStyles.title3,
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
                            text = if (accounts.size > 1) {
                                "已登录 · 共 ${accounts.size} 个账号"
                            } else {
                                "已登录"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmallTitle(text = "账号（${accounts.size}）")
            Card(modifier = Modifier.fillMaxWidth()) {
                accounts.forEach { account ->
                    VideoAccountRow(
                        account = account,
                        onSetPrimary = onSetPrimary,
                        onRename = onRename,
                        onRemove = onRemove
                    )
                }
                // 添加账号：作为 Card 直接子项，Miuix 自动处理圆角与分割线
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
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                // 退出全部：多账号一键清空入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRemoveAll = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "退出全部账号",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }

            // 「登录后能得到什么」说明
            if (platform.loginBenefit.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = platform.loginBenefit,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }

    if (showRemoveAll) {
        WindowDialog(
            show = showRemoveAll,
            onDismissRequest = { showRemoveAll = false },
            title = "退出全部账号"
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "确定要退出「${platform.displayName}」的全部 ${accounts.size} 个账号吗？只清除本机保存的登录凭证。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRemoveAll = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            showRemoveAll = false
                            onRemoveAll()
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("全部退出", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/** 单条视频账号：昵称/备注 + 主账号徽标（点整行切换），右侧菜单改名 / 退出 */
@Composable
private fun VideoAccountRow(
    account: VideoAccountEntity,
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
                    text = "同一平台登录多个账号时，备注能帮你区分（清空备注则显示昵称）。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("退出", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}
