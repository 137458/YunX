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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏样式持久化映射与悬浮底栏渲染模式降级链的纯逻辑测试。
 */
class BottomBarStyleTest {

    // ---------- 持久化映射 ----------

    @Test
    fun fromPersisted_returnsMatchingStyleForEachValidOrdinal() {
        assertEquals(BottomBarStyle.LiquidGlass, BottomBarStyle.fromPersisted(0))
        assertEquals(BottomBarStyle.Blur, BottomBarStyle.fromPersisted(1))
        assertEquals(BottomBarStyle.MiuixStandard, BottomBarStyle.fromPersisted(2))
    }

    @Test
    fun fromPersisted_fallsBackToDefaultWhenOutOfRange() {
        val default = BottomBarStyle.Default
        assertEquals(default, BottomBarStyle.fromPersisted(-1))
        assertEquals(default, BottomBarStyle.fromPersisted(3))
        assertEquals(default, BottomBarStyle.fromPersisted(Int.MAX_VALUE))
        assertEquals(default, BottomBarStyle.fromPersisted(Int.MIN_VALUE))
    }

    @Test
    fun default_isLiquidGlass() {
        assertEquals(BottomBarStyle.LiquidGlass, BottomBarStyle.Default)
    }

    // ---------- 样式属性 ----------

    @Test
    fun isFloating_isTrueForBothFloatingStylesOnly() {
        assertTrue(BottomBarStyle.LiquidGlass.isFloating)
        assertTrue(BottomBarStyle.Blur.isFloating)
        assertFalse(BottomBarStyle.MiuixStandard.isFloating)
    }

    @Test
    fun requestedFloatingMode_mapsFloatingStylesAndIsNullForStandardBar() {
        assertEquals(
            FloatingBottomBarMode.LiquidGlass,
            BottomBarStyle.LiquidGlass.requestedFloatingMode
        )
        assertEquals(FloatingBottomBarMode.Blur, BottomBarStyle.Blur.requestedFloatingMode)
        assertNull(BottomBarStyle.MiuixStandard.requestedFloatingMode)
    }

    // ---------- 渲染模式降级链 ----------

    @Test
    fun resolve_liquidGlassStaysWhenBackdropAndShaderAvailable() {
        assertEquals(
            FloatingBottomBarMode.LiquidGlass,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.LiquidGlass,
                hasBackdrop = true,
                shaderSupported = true
            )
        )
    }

    @Test
    fun resolve_liquidGlassDegradesToBlurWithoutRuntimeShader() {
        // 防御分支：有采样源但没有 AGSL 运行时着色器时退到毛玻璃。
        // 注意：实际运行中这条分支当前不可达——rememberBlurBackdrop() 在
        // !isRuntimeShaderSupported() 时直接返回 null，所以 hasBackdrop 必然为 false，
        // 真实链路见 resolve_noBackdropOnApiBelow33_degradesToNone。
        assertEquals(
            FloatingBottomBarMode.Blur,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.LiquidGlass,
                hasBackdrop = true,
                shaderSupported = false
            )
        )
    }

    @Test
    fun resolve_noBackdropOnApiBelow33_degradesToNone() {
        // API < 33：rememberBlurBackdrop() 返回 null（miuix 的 blur() 首指令即
        // isRuntimeShaderSupported() 早退），因此液态玻璃与毛玻璃两档都只能退到纯色胶囊。
        for (requested in listOf(FloatingBottomBarMode.LiquidGlass, FloatingBottomBarMode.Blur)) {
            assertEquals(
                FloatingBottomBarMode.None,
                resolveFloatingBottomBarMode(
                    requested = requested,
                    hasBackdrop = false,
                    shaderSupported = false
                )
            )
        }
    }

    @Test
    fun resolve_degradesToNoneWhenNoBackdropAvailable() {
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.LiquidGlass,
                hasBackdrop = false,
                shaderSupported = true
            )
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.LiquidGlass,
                hasBackdrop = false,
                shaderSupported = false
            )
        )
        assertEquals(
            FloatingBottomBarMode.None,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.Blur,
                hasBackdrop = false,
                shaderSupported = true
            )
        )
    }

    @Test
    fun resolve_blurStaysWhenBackdropAvailable() {
        assertEquals(
            FloatingBottomBarMode.Blur,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.Blur,
                hasBackdrop = true,
                shaderSupported = true
            )
        )
        // 档位保持 Blur，但注意 miuix 的 blur() 在无运行时着色器时直接早退，
        // 实际渲染结果是半透明纯色胶囊（不是真模糊）
        assertEquals(
            FloatingBottomBarMode.Blur,
            resolveFloatingBottomBarMode(
                requested = FloatingBottomBarMode.Blur,
                hasBackdrop = true,
                shaderSupported = false
            )
        )
    }

    @Test
    fun resolve_noneNeverUpgrades() {
        for (hasBackdrop in listOf(false, true)) {
            for (shaderSupported in listOf(false, true)) {
                assertEquals(
                    FloatingBottomBarMode.None,
                    resolveFloatingBottomBarMode(
                        requested = FloatingBottomBarMode.None,
                        hasBackdrop = hasBackdrop,
                        shaderSupported = shaderSupported
                    )
                )
            }
        }
    }
}
