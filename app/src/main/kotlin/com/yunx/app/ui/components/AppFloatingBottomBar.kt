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
import com.yunx.app.ui.navigation.MainTab
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem

/**
 * Miuix 原生悬浮胶囊导航栏 (FloatingNavigationBar)
 */
@Composable
fun AppFloatingBottomBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingNavigationBar(
        modifier = modifier
    ) {
        MainTab.entries.forEach { tab ->
            FloatingNavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                label = tab.title
            )
        }
    }
}

