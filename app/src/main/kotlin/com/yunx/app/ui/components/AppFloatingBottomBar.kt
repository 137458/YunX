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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.components.liquid.FloatingBottomBar
import com.yunx.app.ui.components.liquid.FloatingBottomBarDefaults
import com.yunx.app.ui.components.liquid.FloatingBottomBarItem
import com.yunx.app.ui.components.liquid.FloatingBottomBarMode
import com.yunx.app.ui.components.liquid.LocalFloatingBottomBarContentColor
import com.yunx.app.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮式底部导航栏（液态玻璃 / 毛玻璃）。
 *
 * 具体渲染档位由 [style] 决定，运行时不支持时在 [FloatingBottomBar] 内部继续降级。
 */
@Composable
fun AppFloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    style: BottomBarStyle = BottomBarStyle.Default,
) {
    val tabs = remember { MainTab.entries }
    val colors = FloatingBottomBarDefaults.colors(
        containerColor = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor = MiuixTheme.colorScheme.primary,
        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        activeContentColor = MiuixTheme.colorScheme.primary
    )

    FloatingBottomBar(
        selectedIndex = { currentTab.ordinal },
        onSelected = { index -> onTabSelected(tabs[index.coerceIn(0, tabs.lastIndex)]) },
        tabsCount = tabs.size,
        modifier = modifier,
        backdrop = backdrop,
        mode = style.requestedFloatingMode ?: FloatingBottomBarMode.LiquidGlass,
        colors = colors
    ) {
        tabs.forEach { tab ->
            FloatingBottomBarItem(
                onClick = { onTabSelected(tab) },
                selected = currentTab == tab,
                modifier = Modifier
                    .defaultMinSize(minWidth = 72.dp)
                    .semantics { contentDescription = tab.title }
            ) {
                // 选中态配色由底栏图层注入：液态玻璃档位下由滑块折射透出，
                // 降级档位下由滑块内的平移副本承载，槽位本身无需判断状态。
                val contentColor = LocalFloatingBottomBarContentColor.current
                Icon(
                    imageVector = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = null, // 外层 Tab 容器统一拥有语义 contentDescription
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.title,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    color = contentColor
                )
            }
        }
    }
}
