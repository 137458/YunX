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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.VideoAccountEntity
import com.yunx.app.data.network.video.VideoParsers
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.R
import com.yunx.app.ui.items.logoRes
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 账号页分组小标题：遵循 Miuix 规范使用 [SmallTitle]。
 */
@Composable
internal fun AccountSectionLabel(text: String) {
    SmallTitle(text = text)
}

/**
 * 账号页「视频解析」区：列出全部视频平台及其登录态。
 *
 * ★ 平台列表由 [VideoParsers.supportedPlatforms] 驱动，这里不硬编码——新增平台只改解析器注册表即可。
 * ★ 只负责展示 + 把点击意图抛出去（登录 / 管理），弹窗与页面挂载交给 DriveScreen / MainScreen。
 *
 * @param loginState 各平台当前账号（platform → 账号），决定显示「未登录 / 昵称 / 备注」
 * @param accounts 全部平台的视频账号，用于统计「共 N 个账号」
 * @param hideUnlogged 与网盘区共用「隐藏未登录」开关：需要登录且未登录的平台不显示；
 *   免登录平台始终显示（藏掉就等于把可用功能删了，它们也没有「未登录」这个概念）
 */
@Composable
internal fun VideoAccountSection(
    loginState: Map<VideoPlatform, VideoAccountEntity>,
    accounts: List<VideoAccountEntity>,
    onLogin: (VideoPlatform) -> Unit,
    onManage: (VideoPlatform) -> Unit,
    hideUnlogged: Boolean = false
) {
    val allPlatforms = remember { VideoParsers.supportedPlatforms }
    val platforms = if (hideUnlogged) {
        allPlatforms.filter { !it.needsLogin || loginState[it] != null }
    } else {
        allPlatforms
    }
    // 每个平台已登录的账号数：只有多账号时才需要在状态里补「共 N 个账号」
    val counts = remember(accounts) { accounts.groupingBy { it.platform }.eachCount() }

    // ★ 一个平台一张卡片（与「网盘账号」区的 DriveAccountCard 同款：一样的 Miuix Card / 边距 / 字号）。
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        platforms.forEach { platform ->
            VideoPlatformCard(
                platform = platform,
                account = loginState[platform],
                accountCount = counts[platform.name] ?: 0,
                onLogin = onLogin,
                onManage = onManage
            )
        }
    }
}

/**
 * 单个视频平台的卡片外壳：与 [com.yunx.app.ui.screens.DriveScreen] 里的 `DriveAccountCard` 同款。
 */
@Composable
private fun VideoPlatformCard(
    platform: VideoPlatform,
    account: VideoAccountEntity?,
    accountCount: Int,
    onLogin: (VideoPlatform) -> Unit,
    onManage: (VideoPlatform) -> Unit
) {
    val onClick: (() -> Unit)? = when {
        !platform.needsLogin -> null
        account != null -> { { onManage(platform) } }
        else -> { { onLogin(platform) } }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        onClick = onClick
    ) {
        VideoPlatformCardContent(
            platform = platform,
            account = account,
            accountCount = accountCount,
            onLogin = onLogin,
            onManage = onManage
        )
    }
}

/** 单个视频平台卡片的内容行（卡片外壳见 [VideoPlatformCard]）：版式与网盘卡片的内容行对齐 */
@Composable
private fun VideoPlatformCardContent(
    platform: VideoPlatform,
    account: VideoAccountEntity?,
    accountCount: Int,
    onLogin: (VideoPlatform) -> Unit,
    onManage: (VideoPlatform) -> Unit
) {
    val enabled = platform.needsLogin

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 平台 Logo：按图标自身形状原样画（不裁圆、不垫底色，理由见 PlatformLogos 的 KDoc）。
        // 需要登录且当前没登录的平台压暗一档，一眼能看出「这个还没配账号」。
        Image(
            painter = painterResource(platform.logoRes()),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .alpha(if (platform.needsLogin && account == null) 0.45f else 1f),
            contentScale = ContentScale.Fit
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = platform.displayName,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = videoStatusText(platform, account, accountCount),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        when {
            !enabled -> Unit
            account != null -> TextButton(text = AppStrings.get(R.string.rs_manage), onClick = { onManage(platform) })
            else -> TextButton(text = AppStrings.get(R.string.lg_login), onClick = { onLogin(platform) })
        }
    }
}

/** 平台状态文案：免登录 / 未登录 / 账号名（备注优先，其次昵称，都空则「已登录」） */
private fun videoStatusText(
    platform: VideoPlatform,
    account: VideoAccountEntity?,
    accountCount: Int
): String = when {
    !platform.needsLogin -> AppStrings.get(R.string.sh_no_login_needed)
    account == null -> AppStrings.get(R.string.csb_drive_not_logged_in)
    else -> {
        val name = account.remark.ifBlank { account.nickname }.ifBlank { AppStrings.get(R.string.csb_drive_logged_in) }
        if (accountCount > 1) AppStrings.get(R.string.sh_name_accounts, name, accountCount) else name
    }
}
