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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用全局主题入口：
 * - 以 MiuixTheme 为核心主题根节点；
 * - 动态映射构建 Material 3 ColorScheme，保证残留 MD3 组件与 Miuix 色彩严格一致；
 * - 响应 AppThemeManager 状态（明暗模式、色彩模式、自定义种子色）。
 */
@Composable
fun ComposeEmptyActivityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    AppThemeManager.init(context)

    val isDark = when (AppThemeManager.darkMode) {
        1 -> false
        2 -> true
        else -> darkTheme
    }

    // 1. Miuix 调色板模式与控制器配置
    val miuixMode = when (AppThemeManager.colorMode) {
        0 -> when (AppThemeManager.darkMode) {
            1 -> ColorSchemeMode.MonetLight
            2 -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetSystem
        }
        else -> when (AppThemeManager.darkMode) {
            1 -> ColorSchemeMode.Light
            2 -> ColorSchemeMode.Dark
            else -> ColorSchemeMode.System
        }
    }
    val miuixKeyColor = if (AppThemeManager.colorMode == 2) Color(AppThemeManager.seedColor) else null
    val miuixController = remember(miuixMode, miuixKeyColor) {
        ThemeController(
            colorSchemeMode = miuixMode,
            keyColor = miuixKeyColor
        )
    }

    // 2. 将 MiuixTheme 作为唯一色彩根，并派生兼容 MaterialTheme
    MiuixTheme(controller = miuixController) {
        val miuixColors = MiuixTheme.colorScheme
        val md3Colors = remember(miuixColors, isDark) {
            val primary = miuixColors.primary
            val onPrimary = miuixColors.onPrimary
            val surface = miuixColors.surface
            val onSurface = miuixColors.onSurface
            val background = miuixColors.background
            val onBackground = miuixColors.onBackground
            val surfaceVariant = miuixColors.surfaceVariant
            val onSurfaceVariant = miuixColors.onSurfaceSecondary
            val outline = miuixColors.dividerLine
            val error = miuixColors.error
            val onError = miuixColors.onError

            if (isDark) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = onPrimary,
                    primaryContainer = miuixColors.primaryContainer,
                    onPrimaryContainer = miuixColors.onPrimaryContainer,
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
                    primaryContainer = miuixColors.primaryContainer,
                    onPrimaryContainer = miuixColors.onPrimaryContainer,
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

        MaterialTheme(
            colorScheme = md3Colors,
            typography = Typography,
            content = content
        )
    }
}