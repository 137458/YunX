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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme

/** AMOLED 深色方案中的容器灰阶：背景纯黑，只保留高/最高容器灰阶以维持卡片分组边界 */
private val AmoledSurfaceVariant = Color(0xFF121212)
private val AmoledSurfaceContainerHigh = Color(0xFF1E1E1E)
private val AmoledSurfaceContainerHighest = Color(0xFF2C2C2C)

/** 当前没有对应的设置项：构建控制器时固定使用默认调色板风格与 2021 色彩规范 */
private const val DEFAULT_PALETTE_STYLE_INDEX = 0
private const val DEFAULT_USE_SPEC_2025 = false

/**
 * AMOLED 纯黑覆盖：把背景/表面压到纯黑，仅保留容器高层的灰阶用于区分分组。
 * 以传入的基准方案（默认深色或动态取色结果）为底，只替换表面相关角色，
 * 主色/强调色/文字色保持不变，因此不会因为纯黑而丢失主题色。
 */
fun amoledColors(base: Colors): Colors = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = AmoledSurfaceVariant,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = AmoledSurfaceContainerHighest
)

/**
 * 解析明暗模式与色彩模式对应的 Miuix 调色板模式。
 *
 * colorMode 0=动态色彩（Monet，从系统壁纸取色）；1=默认固定调色板；2=自定义种子色。
 * **自定义种子色必须走 Monet 分支**：miuix 只在 Monet* 模式下消费 keyColor，
 * 走 Light/Dark/System 时种子色会被直接忽略（这是「自定义主题色不生效」的根因）。
 */
fun resolveColorSchemeMode(darkMode: Int, colorMode: Int): ColorSchemeMode {
    val monet = colorMode == 0 || colorMode == 2
    return when (darkMode) {
        1 -> if (monet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        2 -> if (monet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (monet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
}

/**
 * 构建 Miuix 主题控制器（纯函数，无 Compose 上下文，便于单元测试）：
 * - paletteStyleIndex 越界（脏持久化数据）时回退到默认 TonalSpot，绝不抛异常；
 * - colorMode=2 时把种子色交给控制器（同时按 resolveColorSchemeMode 走 Monet 分支才生效）；
 *   （非 Monet 模式下 ThemeController 会直接采用该 darkColors）。
 */
fun buildMiuixThemeController(
    darkMode: Int,
    colorMode: Int,
    seedColor: Long,
    isAmoled: Boolean,
    paletteStyleIndex: Int,
    useSpec2025: Boolean
): ThemeController {
    val paletteStyle = ThemePaletteStyle.entries.getOrNull(paletteStyleIndex)
        ?: ThemePaletteStyle.TonalSpot
    val colorSpec = if (useSpec2025) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021
    val keyColor = if (colorMode == 2) Color(seedColor) else null
    return ThemeController(
        colorSchemeMode = resolveColorSchemeMode(darkMode, colorMode),
        keyColor = keyColor,
        colorSpec = colorSpec,
        paletteStyle = paletteStyle,
        darkColors = if (isAmoled) amoledColors(miuixDarkColorScheme()) else miuixDarkColorScheme()
    )
}

/**
 * 应用全局主题入口：
 * - 以 MiuixTheme 为核心主题根节点；
 * - 动态映射构建 Material 3 ColorScheme，保证残留 MD3 组件与 Miuix 色彩严格一致；
 * - 响应 AppThemeManager 状态（明暗模式、色彩模式、自定义种子色、AMOLED 纯黑）。
 */
@Composable
fun ComposeEmptyActivityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    AppThemeManager.init(context)
    // 订阅设置写入版本号：设置页绕过 AppThemeManager 直接写 SettingsRepository 时，
    // 这里会重新同步内存状态并触发重组，避免「设置写完主题不刷新」。
    AppThemeManager.syncFromSettings(context)

    val isDark = when (AppThemeManager.darkMode) {
        1 -> false
        2 -> true
        else -> darkTheme
    }

    // 1. Miuix 调色板模式与控制器配置（越界参数由 buildMiuixThemeController 兜底）
    val darkMode = AppThemeManager.darkMode
    val colorMode = AppThemeManager.colorMode
    val seedColor = AppThemeManager.seedColor
    val isAmoled = AppThemeManager.isAmoled
    val miuixController = remember(darkMode, colorMode, seedColor, isAmoled) {
        buildMiuixThemeController(
            darkMode = darkMode,
            colorMode = colorMode,
            seedColor = seedColor,
            isAmoled = isAmoled,
            paletteStyleIndex = DEFAULT_PALETTE_STYLE_INDEX,
            useSpec2025 = DEFAULT_USE_SPEC_2025
        )
    }

    // 2. 将 MiuixTheme 作为唯一色彩根，并派生兼容 MaterialTheme
    MiuixTheme(controller = miuixController) {
        val navOwner = rememberNavigationEventDispatcherOwner(parent = null)
        // 控制器解析后的实际方案（动态取色与种子色都在这一步生效）
        val baseColors = MiuixTheme.colorScheme
        // AMOLED：Monet 动态取色模式下 ThemeController 会忽略传入的 darkColors，
        // 所以这里基于实际方案再叠一层纯黑，并用公开的 MiuixTheme(colors = ...) 重载重新注入。
        // 注意以「实际取到的颜色值」而不是 Colors 对象身份做 key：MiuixTheme 内部的 Colors
        // 是原地更新的（身份不变、内容会变），用对象身份会缓存到过期快照。
        val colors = remember(
            baseColors.background,
            baseColors.surface,
            baseColors.surfaceVariant,
            baseColors.surfaceContainer,
            baseColors.surfaceContainerHigh,
            baseColors.surfaceContainerHighest,
            isAmoled,
            isDark
        ) {
            if (isAmoled && isDark) amoledColors(baseColors) else baseColors
        }
        MiuixTheme(colors = colors) {
            val md3Colors = remember(colors, isDark) {
                val primary = colors.primary
                val onPrimary = colors.onPrimary
                val surface = colors.surface
                val onSurface = colors.onSurface
                val background = colors.background
                val onBackground = colors.onBackground
                val surfaceVariant = colors.surfaceVariant
                val onSurfaceVariant = colors.onSurfaceSecondary
                val outline = colors.dividerLine
                val error = colors.error
                val onError = colors.onError

                if (isDark) {
                    darkColorScheme(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = colors.primaryContainer,
                        onPrimaryContainer = colors.onPrimaryContainer,
                        secondary = primary,
                        onSecondary = onPrimary,
                        surface = surface,
                        onSurface = onSurface,
                        surfaceVariant = surfaceVariant,
                        onSurfaceVariant = onSurfaceVariant,
                        background = background,
                        onBackground = onBackground,
                        outline = outline,
                        error = error,
                        onError = onError
                    )
                } else {
                    lightColorScheme(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = colors.primaryContainer,
                        onPrimaryContainer = colors.onPrimaryContainer,
                        secondary = primary,
                        onSecondary = onPrimary,
                        surface = surface,
                        onSurface = onSurface,
                        surfaceVariant = surfaceVariant,
                        onSurfaceVariant = onSurfaceVariant,
                        background = background,
                        onBackground = onBackground,
                        outline = outline,
                        error = error,
                        onError = onError
                    )
                }
            }

            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navOwner
            ) {
                MaterialTheme(
                    colorScheme = md3Colors,
                    typography = Typography,
                    content = content
                )
            }
        }
    }
}
