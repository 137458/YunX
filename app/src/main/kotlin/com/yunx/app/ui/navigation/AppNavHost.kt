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

package com.yunx.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yunx.app.ui.MainScreen

/**
 * 应用根导航宿主。
 *
 * 现在只注册主框架一个 destination：骨架阶段先让所有页面都运行在同一个 NavHost 里，
 * 后续批次再把全屏页 / 登录页 / 云盘子页逐批搬到 [AppRoute] 对应的 destination 上。
 *
 * 返回语义：单 destination 时返回键仍由系统直接交给 Activity（与迁移前一致）；
 * 一旦有覆盖页入栈，pop 动作由 NavHost 的 PredictiveBackHandler 驱动，
 * 因此系统能拿到手势进度并播放「目标页预览」动画。
 */
@Composable
fun AppNavHost(
    /** 外部来源请求直接打开的 Tab（如点击实况通知 → 下载页）；null = 默认「解析」页 */
    openTab: MainTab? = null,
    /** [openTab] 被消费后回调 */
    onOpenTabConsumed: () -> Unit = {},
    /** 深链接 / 外部分享拿到的待解析文本 */
    incomingLink: String? = null,
    /** [incomingLink] 被消费后回调 */
    onIncomingLinkConsumed: () -> Unit = {},
    /** 深链接拿到的直链 / 磁力 */
    incomingAddUrl: String? = null,
    /** [incomingAddUrl] 被消费后回调 */
    onIncomingAddUrlConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MainRoute) {
        composable<MainRoute> {
            MainScreen(
                openTab = openTab,
                onOpenTabConsumed = onOpenTabConsumed,
                incomingLink = incomingLink,
                onIncomingLinkConsumed = onIncomingLinkConsumed,
                incomingAddUrl = incomingAddUrl,
                onIncomingAddUrlConsumed = onIncomingAddUrlConsumed
            )
        }
    }
}
