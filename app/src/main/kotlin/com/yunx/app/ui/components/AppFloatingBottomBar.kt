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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.components.liquid.FloatingBottomBar
import com.yunx.app.ui.components.liquid.FloatingBottomBarDefaults
import com.yunx.app.ui.components.liquid.FloatingBottomBarDirectSelector
import com.yunx.app.ui.components.liquid.FloatingBottomBarItem
import com.yunx.app.ui.components.liquid.FloatingBottomBarMode
import com.yunx.app.ui.components.liquid.LocalFloatingBottomBarContentColor
import com.yunx.app.ui.navigation.MainTab
import com.yunx.app.ui.theme.FLOATING_BAR_ITEM_MIN_WIDTH_DP
import com.yunx.app.ui.theme.FLOATING_BAR_MAX_WIDTH_DP
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 悬浮式底部导航栏（液态玻璃 / 毛玻璃）。
 *
 * 具体渲染档位由 [style] 决定，运行时不支持时在 [FloatingBottomBar] 内部继续降级。
 * 震动走「外层单一派发」：拖拽落点经 [FloatingBottomBar] 的 onSelected 回调、槽位点击走自家 onClick，
 * 两条路径各派发一次，底栏内部不再重复触发。
 *
 * **本组件自带外壳**：系统栏 inset 避让、左右边距、宽度上限与贴底居中全部在这里处理，
 * 宽度**按内容自适应**（不设下限，也不再铺满屏宽）：每个 Tab 槽位取
 * [FLOATING_BAR_ITEM_MIN_WIDTH_DP] 与「图标 + 文案实测宽」中的较大者，
 * 胶囊因此在窄屏上同样收紧；[FLOATING_BAR_MAX_WIDTH_DP] 只作为超长文案 / 大字号下的兜底上限。
 * 所以它不依赖 `Scaffold` 的任何槽位，可以作为内容 Box 的兄弟节点直接
 * `Modifier.align(Alignment.BottomCenter)` 叠上去（内容层挂 `blurBackdropSource` 当采样源）。
 * 也因此不需要把实测栏高回传给外层算避让——避让是固定常量，见
 * [com.yunx.app.ui.theme.BOTTOM_BAR_CONTENT_PADDING_DP]。
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
    val haptics = rememberHaptics()
    val directSelector = remember { FloatingBottomBarDirectSelector() }
    val colors = FloatingBottomBarDefaults.colors(
        containerColor = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor = MiuixTheme.colorScheme.primary,
        contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        activeContentColor = MiuixTheme.colorScheme.primary
    )

    val navBarBottomPadding = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()
    // 有系统导航栏时贴着它上方留 8dp；没有（全面屏手势 / 平板 / 桌面）时留 20dp 视觉间距
    val bottomPaddingValue = if (navBarBottomPadding != 0.dp) 8.dp + navBarBottomPadding else 20.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPaddingValue, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            // 只保留上限，宽度交给 FloatingBottomBar 的 IntrinsicSize.Min 按内容决定；
            // 一旦在这里写 fillMaxWidth，测量约束会被钉成定值，内容自适应随即失效
            modifier = Modifier
                .widthIn(max = FLOATING_BAR_MAX_WIDTH_DP.dp),
            contentAlignment = Alignment.Center
        ) {
            FloatingBottomBar(
                selectedIndex = { currentTab.ordinal },
                onSelected = { index ->
                    haptics.tick()
                    onTabSelected(tabs[index.coerceIn(0, tabs.lastIndex)])
                },
                tabsCount = tabs.size,
                backdrop = backdrop,
                mode = style.requestedFloatingMode ?: FloatingBottomBarMode.LiquidGlass,
                colors = colors,
                directSelector = directSelector,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = currentTab == tab
                    FloatingBottomBarItem(
                        onClick = {
                            directSelector(index)
                            haptics.tick()
                            onTabSelected(tab)
                        },
                        // 选中语义注入槽位：底栏不为每个 Tab 生成独立语义节点
                        // 最小宽度是内容自适应的实际下限：文案更长 / 字号更大时槽位自己撑宽，不压字
                        modifier = Modifier
                            .defaultMinSize(minWidth = FLOATING_BAR_ITEM_MIN_WIDTH_DP.dp)
                            .semantics { selected = isSelected }
                    ) {
                        // 选中态配色由底栏图层注入：液态玻璃档位下由滑块折射透出，
                        // 降级档位下由滑块内的平移副本承载，槽位本身无需判断状态。
                        val contentColor = LocalFloatingBottomBarContentColor.current
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = null, // 装饰性图标：标签由同容器内的 Text 提供
                            tint = contentColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
