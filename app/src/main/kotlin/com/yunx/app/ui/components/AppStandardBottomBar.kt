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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yunx.app.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

/**
 * Miuix 原生标准底栏（贴底固定 NavigationBar）。
 *
 * 作为悬浮底栏的对照档位：无背景采样、无离屏图层、无着色器，低端设备与省电场景的首选。
 * 导航栏 inset 由 [NavigationBar] 自身的 defaultWindowInsetsPadding 处理。
 */
@Composable
fun AppStandardBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember { MainTab.entries }
    // 普通点击用轻触感：LongPress 是强震动，用在切 Tab 上过重
    val haptics = rememberHaptics()

    NavigationBar(modifier = modifier.fillMaxWidth()) {
        tabs.forEach { tab ->
            val isSelected = currentTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    haptics.tick()
                    onTabSelected(tab)
                },
                icon = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                label = tab.title
            )
        }
    }
}
