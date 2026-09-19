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

package com.yunx.app.ui.components.liquid

/**
 * 用户可选的底栏样式（在「主题与外观」页切换）。
 *
 * 持久化使用 [ordinal]：新增样式只能追加到末尾，不能插入或调序，否则会读错用户既有设置。
 * 读取一律经 [fromPersisted] 兜底，越界值回退 [Default]。
 */
enum class BottomBarStyle {

    /** 液态玻璃悬浮底栏：SDF 透镜折射 + 色散彩虹晕 + 双重背景采样 + 陀螺仪高光。 */
    LiquidGlass,

    /** 毛玻璃悬浮底栏：Backdrop 高斯模糊悬浮胶囊，不依赖运行时着色器。 */
    Blur,

    /** Miuix 原生标准底栏：贴底固定的 NavigationBar，无悬浮与背景采样开销。 */
    MiuixStandard;

    /** 是否为悬浮胶囊底栏（[MiuixStandard] 为贴底固定栏，不参与悬浮渲染）。 */
    val isFloating: Boolean
        get() = this != MiuixStandard

    /**
     * 该样式请求的悬浮底栏渲染模式；[MiuixStandard] 不走悬浮底栏，返回 null。
     */
    val requestedFloatingMode: FloatingBottomBarMode?
        get() = when (this) {
            LiquidGlass -> FloatingBottomBarMode.LiquidGlass
            Blur -> FloatingBottomBarMode.Blur
            MiuixStandard -> null
        }

    companion object {
        /** 默认样式：液态玻璃悬浮底栏。 */
        val Default: BottomBarStyle = LiquidGlass

        /** 从持久化序号还原样式，越界或非法值回退 [Default]。 */
        fun fromPersisted(value: Int): BottomBarStyle = entries.getOrElse(value) { Default }
    }
}

/**
 * 悬浮底栏渲染模式的运行时降级链。
 *
 * 液态玻璃依赖 AGSL 运行时着色器（API 33+）与可用 Backdrop：任一缺失即降级为毛玻璃；
 * 连 Backdrop 都没有（未挂载采样层）时退到纯色，避免渲染出错或黑边。
 */
internal fun resolveFloatingBottomBarMode(
    requested: FloatingBottomBarMode,
    hasBackdrop: Boolean,
    shaderSupported: Boolean,
): FloatingBottomBarMode = when (requested) {
    FloatingBottomBarMode.LiquidGlass -> when {
        hasBackdrop && shaderSupported -> FloatingBottomBarMode.LiquidGlass
        hasBackdrop -> FloatingBottomBarMode.Blur
        else -> FloatingBottomBarMode.None
    }
    FloatingBottomBarMode.Blur ->
        if (hasBackdrop) FloatingBottomBarMode.Blur else FloatingBottomBarMode.None
    FloatingBottomBarMode.None -> FloatingBottomBarMode.None
}
