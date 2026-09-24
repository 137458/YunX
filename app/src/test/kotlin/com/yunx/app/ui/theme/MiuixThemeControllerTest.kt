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

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme

/** 主题控制器构建的纯函数逻辑：模式映射、脏数据兜底、AMOLED 纯黑方案。 */
class MiuixThemeControllerTest {

    @Test
    fun mapsDarkModeAndColorModeToPaletteMode() {
        // colorMode 0 = 动态色彩，走 Monet 模式；1 = 默认固定调色板；
        // 2 = 自定义种子色，也必须走 Monet 模式（miuix 只在 Monet* 下消费 keyColor）
        assertEquals(ColorSchemeMode.MonetSystem, resolveColorSchemeMode(darkMode = 0, colorMode = 0))
        assertEquals(ColorSchemeMode.MonetLight, resolveColorSchemeMode(darkMode = 1, colorMode = 0))
        assertEquals(ColorSchemeMode.MonetDark, resolveColorSchemeMode(darkMode = 2, colorMode = 0))
        assertEquals(ColorSchemeMode.System, resolveColorSchemeMode(darkMode = 0, colorMode = 1))
        assertEquals(ColorSchemeMode.Light, resolveColorSchemeMode(darkMode = 1, colorMode = 1))
        assertEquals(ColorSchemeMode.Dark, resolveColorSchemeMode(darkMode = 2, colorMode = 1))
        assertEquals(ColorSchemeMode.MonetSystem, resolveColorSchemeMode(darkMode = 0, colorMode = 2))
        assertEquals(ColorSchemeMode.MonetLight, resolveColorSchemeMode(darkMode = 1, colorMode = 2))
        assertEquals(ColorSchemeMode.MonetDark, resolveColorSchemeMode(darkMode = 2, colorMode = 2))
        // 越界的明暗取值按「跟随系统」处理，不能崩
        assertEquals(ColorSchemeMode.System, resolveColorSchemeMode(darkMode = 7, colorMode = 1))
        assertEquals(ColorSchemeMode.System, resolveColorSchemeMode(darkMode = -1, colorMode = 1))
    }

    @Test
    fun outOfRangePaletteStyleFallsBackToTonalSpot() {
        assertEquals(
            ThemePaletteStyle.TonalSpot,
            buildMiuixThemeController(2, 1, 0xFF415F91L, false, -1, false).paletteStyle
        )
        assertEquals(
            ThemePaletteStyle.TonalSpot,
            buildMiuixThemeController(2, 1, 0xFF415F91L, false, 99, false).paletteStyle
        )
        // 合法下标必须映射到对应枚举，兜底不能把有效值一起吃掉
        assertEquals(
            ThemePaletteStyle.Neutral,
            buildMiuixThemeController(2, 1, 0xFF415F91L, false, 1, false).paletteStyle
        )
        assertEquals(
            ThemePaletteStyle.entries.last(),
            buildMiuixThemeController(2, 1, 0xFF415F91L, false, ThemePaletteStyle.entries.size - 1, false).paletteStyle
        )
    }

    @Test
    fun colorSpecFollowsUseSpec2025() {
        assertEquals(
            ThemeColorSpec.Spec2021,
            buildMiuixThemeController(1, 1, 0xFF415F91L, false, 0, false).colorSpec
        )
        assertEquals(
            ThemeColorSpec.Spec2025,
            buildMiuixThemeController(1, 1, 0xFF415F91L, false, 0, true).colorSpec
        )
    }

    @Test
    fun seedColorIsOnlyPassedForCustomColorMode() {
        assertNull(buildMiuixThemeController(2, 0, 0xFF415F91L, false, 0, false).keyColor)
        assertNull(buildMiuixThemeController(2, 1, 0xFF415F91L, false, 0, false).keyColor)
        assertEquals(
            Color(0xFF415F91L),
            buildMiuixThemeController(2, 2, 0xFF415F91L, false, 0, false).keyColor
        )
    }

    @Test
    fun amoledFlagSwitchesTheDarkScheme() {
        val defaultContainerHigh = darkColorScheme().surfaceContainerHigh
        val normal = buildMiuixThemeController(2, 1, 0xFF415F91L, false, 0, false).darkColors
        val amoled = buildMiuixThemeController(2, 1, 0xFF415F91L, true, 0, false).darkColors

        assertEquals(defaultContainerHigh, normal.surfaceContainerHigh)
        assertEquals(Color(0xFF1E1E1E), amoled.surfaceContainerHigh)
    }

    @Test
    fun amoledDarkSchemeKeepsGroupingGraysAndAccent() {
        val base = buildMiuixThemeController(2, 1, 0xFF415F91L, false, 0, false).darkColors
        val amoled = buildMiuixThemeController(2, 1, 0xFF415F91L, true, 0, false).darkColors

        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceContainer)
        // 高层容器保留灰阶，用于维持卡片分组边界
        assertEquals(Color(0xFF1E1E1E), amoled.surfaceContainerHigh)
        assertEquals(Color(0xFF2C2C2C), amoled.surfaceContainerHighest)
        // 主色与文字色跟随基准方案，不因纯黑而丢失主题色
        assertEquals(base.primary, amoled.primary)
        assertEquals(base.onSurface, amoled.onSurface)
        assertEquals(base.onSurfaceSecondary, amoled.onSurfaceSecondary)
    }

    @Test
    fun amoledColorsOnlyOverridesSurfacesAndDoesNotMutateTheBase() {
        val base = darkColorScheme(
            background = Color(0xFF222222),
            surface = Color(0xFF232323),
            surfaceContainer = Color(0xFF242424),
            surfaceContainerHigh = Color(0xFF252525),
            surfaceContainerHighest = Color(0xFF262626),
            primary = Color(0xFF112233)
        )
        val amoled = amoledColors(base)

        // 只替换表面相关角色
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceContainer)
        assertEquals(Color(0xFF1E1E1E), amoled.surfaceContainerHigh)
        assertEquals(Color(0xFF2C2C2C), amoled.surfaceContainerHighest)
        assertEquals(Color(0xFF112233), amoled.primary)

        // 基准方案本身不被就地修改
        assertEquals(Color(0xFF222222), base.background)
        assertEquals(Color(0xFF252525), base.surfaceContainerHigh)
    }
}
