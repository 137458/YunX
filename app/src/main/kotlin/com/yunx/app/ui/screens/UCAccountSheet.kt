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

import androidx.compose.runtime.Composable
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.ui.drive.CloudAccountSheet
import com.yunx.app.ui.drive.CloudInfoRow
import com.yunx.app.ui.drive.formatCloudLoginTime

/**
 * 已登录UC网盘账号的底部抽屉：展示用户信息、登录时间、密钥（可展开/复制），并提供退出登录。
 */
@Composable
fun UCAccountSheet(
    account: UCAccountEntity,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    CloudAccountSheet(
        nickname = account.nickname,
        platformLabel = "UC网盘",
        infoRows = buildList {
            add(CloudInfoRow(label = "登录时间", value = formatCloudLoginTime(account.updatedAt)))
            
        },
        secretLabel = "Cookie",
        secretValue = account.cookie,
        logoutMessage = "确定要退出当前 UC 账号吗？退出后将清除本地 Cookie。",
        onLogout = onLogout,
        onDismiss = onDismiss
    )
}
