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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.components.liquid.FloatingBottomBar
import com.yunx.app.ui.components.liquid.FloatingBottomBarDefaults
import com.yunx.app.ui.components.liquid.FloatingBottomBarItem
import com.yunx.app.ui.components.liquid.FloatingBottomBarMode
import com.yunx.app.ui.components.liquid.LocalFloatingBottomBarContentColor
import com.yunx.app.ui.components.liquid.isRuntimeShaderSupported
import com.yunx.app.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 生产级液态玻璃悬浮底栏 (AppFloatingBottomBar)。
 * 融合 SDF 透镜折射、陀螺仪重力感应旋转高光、阻尼拖拽弹性手感与三态平滑降级。
 */
@Composable
fun AppFloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    mode: FloatingBottomBarMode = if (isRuntimeShaderSupported()) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.Blur
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
        mode = mode,
        colors = colors
    ) {
        tabs.forEach { tab ->
            val isSelected = currentTab == tab
            FloatingBottomBarItem(
                onClick = { onTabSelected(tab) },
                modifier = Modifier
                    .defaultMinSize(minWidth = 72.dp)
                    .semantics { selected = isSelected }
            ) {
                val itemColor = if (isSelected) colors.activeContentColor else LocalFloatingBottomBarContentColor.current
                Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = null, // 外层 Tab 容器统一拥有无障碍 Role.Tab 与 title
                    tint = itemColor,
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
                    color = itemColor
                )
            }
        }
    }
}
