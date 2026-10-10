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

package com.yunx.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.navigation.MainTab
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 底部导航栏门面：按用户在「主题与外观」里选的档位分发到具体实现。
 *
 * 存在这一层的意义是**让两种形态共用同一个挂载点**：悬浮档（液态玻璃 / 毛玻璃）与
 * Miuix 标准档都不再占 `Scaffold` 的 `bottomBar` / `floatingToolbar` 槽，而是作为内容
 * Box 的兄弟节点 `Modifier.align(Alignment.BottomCenter)` 叠上去。调用处因此不需要
 * 按档位切换槽位，也就不会出现「标准档在槽里、悬浮档在 Box 里」这种双轨写法。
 *
 * 分发依据直接取 [BottomBarStyle.isFloating]，不再另收一个 `isFloating` 参数——
 * 两个来源一旦不一致就会出现「按悬浮档预留避让、却渲染了贴底标准栏」的错位。
 *
 * 系统栏 inset 由各自实现负责：悬浮档在 [AppFloatingBottomBar] 的外壳里自己算，
 * 标准档由 Miuix `NavigationBar` 的 `defaultWindowInsetsPadding` 处理。
 */
@Composable
fun AppBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    style: BottomBarStyle = BottomBarStyle.Default,
    backdrop: Backdrop? = null,
) {
    if (style.isFloating) {
        AppFloatingBottomBar(
            currentTab = currentTab,
            onTabSelected = onTabSelected,
            modifier = modifier,
            backdrop = backdrop,
            style = style
        )
    } else {
        AppStandardBottomBar(
            currentTab = currentTab,
            onTabSelected = onTabSelected,
            modifier = modifier
        )
    }
}
