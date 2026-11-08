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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.MultiAccountEntity
import com.yunx.app.R
import com.yunx.app.util.AccountNames
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup

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
    onEmpty: () -> Unit,
    @StringRes defaultNameRes: Int? = null
) {
    // 最后一个账号被退出后弹窗没有内容可展示了，自动关闭
    LaunchedEffect(accounts.isEmpty()) { if (accounts.isEmpty()) onEmpty() }

    SmallTitle(text = AppStrings.get(R.string.sh_accounts_count, accounts.size))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        accounts.forEach { account ->
            AccountRow(
                account = account,
                onSetPrimary = onSetPrimary,
                onRename = onRename,
                onRemove = onRemove,
                defaultNameRes = defaultNameRes
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
                text = AppStrings.get(R.string.sh_add_account),
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
    Text(
        text = AppStrings.get(R.string.csa_primary_account_hint),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
    )
}

/** 单条账号：昵称/备注 + 主账号徽标（点整行可切换），右侧菜单改名 / 退出 */
@Composable
internal fun AccountRow(
    account: MultiAccountEntity,
    onSetPrimary: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    @StringRes defaultNameRes: Int? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    val displayName = account.displayName(defaultNameRes)

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
                text = if (account.isPrimary) AppStrings.get(R.string.sh_primary_account) else AppStrings.get(R.string.sh_tap_to_set_primary),
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
                    contentDescription = AppStrings.get(R.string.sh_account_actions),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            WindowListPopup(
                show = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                ListPopupColumn {
                    DropdownImpl(
                        text = AppStrings.get(R.string.sh_rename_remark),
                        optionSize = 2,
                        isSelected = false,
                        index = 0,
                        onSelectedIndexChange = {
                            menuExpanded = false
                            showRename = true
                        }
                    )
                    DropdownImpl(
                        text = AppStrings.get(R.string.sh_logout_account),
                        optionSize = 2,
                        isSelected = false,
                        index = 1,
                        onSelectedIndexChange = {
                            menuExpanded = false
                            showRemove = true
                        }
                    )
                }
            }
        }
    }

    if (showRename) {
        var input by remember(account.id) { mutableStateOf(account.remark) }
        WindowDialog(
            show = showRename,
            onDismissRequest = { showRename = false },
            title = AppStrings.get(R.string.sh_rename_remark)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = AppStrings.get(R.string.sh_remark),
                    useLabelAsPlaceholder = true,
                    singleLine = true
                )
                Text(
                    text = AppStrings.get(R.string.sh_video_remark_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = AppStrings.get(R.string.csa_cancel),
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
                        Text(AppStrings.get(R.string.lg_save), style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }

    if (showRemove) {
        WindowDialog(
            show = showRemove,
            onDismissRequest = { showRemove = false },
            title = AppStrings.get(R.string.sh_logout_account)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AppStrings.get(R.string.sh_logout_confirm, displayName),
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        text = AppStrings.get(R.string.csa_cancel),
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
                        Text(AppStrings.get(R.string.sh_logout), style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/** 账号显示名：备注优先，回退昵称（空值或旧语言默认名回退到当前语言），再回退占位文案 */
internal fun MultiAccountEntity.displayName(@StringRes defaultNameRes: Int? = null): String {
    val nick = if (defaultNameRes != null) AccountNames.display(defaultNameRes, nickname) else nickname
    return remark.ifBlank { nick }.ifBlank { AppStrings.get(R.string.sh_unnamed_account) }
}
