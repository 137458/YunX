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

package com.yunx.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 侧边导航栏与底部导航栏的显示判据（纯逻辑，不碰 Compose 与 android.jar）。
 *
 * 核心不变式是「二者严格互斥且必有其一」：宽屏只在用户选了贴底标准栏时才让位给侧边栏，
 * 其余情况一律显示底栏。历史缺陷是两者共存——矮横屏手机会同时长出侧边栏与悬浮底栏，
 * 纵向空间被双重挤占，所以互斥性单独列一个用例钉住，而不只测真值表。
 */
class WindowLayoutTest {

    @Test
    fun wideWindowBreakpointIsInclusiveAt600() {
        assertFalse(isWideWindow(599))
        assertTrue(isWideWindow(600))
        assertTrue(isWideWindow(601))
        assertTrue(isWideWindow(1280))
        // 竖屏手机与矮横屏手机都算窄窗口：横屏不再是侧边栏的触发条件
        assertFalse(isWideWindow(360))
        assertFalse(isWideWindow(480))
    }

    @Test
    fun wideWindowBreakpointMatchesConstant() {
        assertEquals(600, WIDE_SCREEN_MIN_WIDTH_DP)
        assertFalse(isWideWindow(WIDE_SCREEN_MIN_WIDTH_DP - 1))
        assertTrue(isWideWindow(WIDE_SCREEN_MIN_WIDTH_DP))
    }

    @Test
    fun shortLandscapePhoneKeepsBottomBarInsteadOfRail() {
        // 560×320 这类矮横屏手机：旧判据有一条「高度 < 480 且横向」的分支会让它长出侧边栏，
        // 与悬浮底栏共存后纵向被双重挤占。现在只看宽度，560 < 600 所以仍用底栏。
        assertFalse(isWideWindow(560))
        assertFalse(shouldShowNavigationRail(isWideWindow(560), useFloatingBottomBar = true))
        assertTrue(shouldShowBottomBar(isWideWindow(560), useFloatingBottomBar = true))
        assertFalse(shouldShowNavigationRail(isWideWindow(560), useFloatingBottomBar = false))
        assertTrue(shouldShowBottomBar(isWideWindow(560), useFloatingBottomBar = false))
    }

    @Test
    fun navigationRailOnlyReplacesStandardBarOnWideWindow() {
        // 窄窗口：无论哪种底栏样式都不出侧边栏
        assertFalse(shouldShowNavigationRail(isWideWindow = false, useFloatingBottomBar = false))
        assertFalse(shouldShowNavigationRail(isWideWindow = false, useFloatingBottomBar = true))
        // 宽窗口 + 贴底标准栏：侧边栏取代底栏
        assertTrue(shouldShowNavigationRail(isWideWindow = true, useFloatingBottomBar = false))
        // 宽窗口 + 悬浮栏：用户明确要悬浮形态，侧边栏让位
        assertFalse(shouldShowNavigationRail(isWideWindow = true, useFloatingBottomBar = true))
    }

    @Test
    fun bottomBarShowsInEveryCaseExceptWideWindowWithStandardBar() {
        assertTrue(shouldShowBottomBar(isWideWindow = false, useFloatingBottomBar = false))
        assertTrue(shouldShowBottomBar(isWideWindow = false, useFloatingBottomBar = true))
        assertFalse(shouldShowBottomBar(isWideWindow = true, useFloatingBottomBar = false))
        assertTrue(shouldShowBottomBar(isWideWindow = true, useFloatingBottomBar = true))
    }

    @Test
    fun railAndBottomBarAreMutuallyExclusiveAndExhaustive() {
        for (isWide in listOf(false, true)) {
            for (useFloating in listOf(false, true)) {
                val rail = shouldShowNavigationRail(isWide, useFloating)
                val bar = shouldShowBottomBar(isWide, useFloating)
                assertFalse("isWide=$isWide useFloating=$useFloating 不应同时显示侧边栏与底栏", rail && bar)
                assertTrue("isWide=$isWide useFloating=$useFloating 必须至少显示其一", rail || bar)
            }
        }
    }
}
