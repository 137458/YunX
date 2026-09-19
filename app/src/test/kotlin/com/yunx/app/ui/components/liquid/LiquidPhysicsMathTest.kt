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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class LiquidPhysicsMathTest {

    @Test
    fun quantizeGravityAngle_whenFlat_returnsDefaultTopAngle() {
        // 平放时 (gx, gy 接近 0)，默认固定朝向顶部 (-90°)
        val angle = LiquidPhysicsMath.quantizeGravityAngle(0.01f, 0.01f)
        assertEquals((-PI / 2).toFloat(), angle, 1e-4f)
    }

    @Test
    fun quantizeGravityAngle_whenTilted_quantizesToThreeDegrees() {
        // 倾斜向右下：atan2(1.0, 1.0) = 45° = 0.785398 rad
        // 45° 是 3° 的整数倍 (15 * 3° = 45°)
        val angle = LiquidPhysicsMath.quantizeGravityAngle(1.0f, 1.0f)
        val expected = (45.0 * PI / 180.0).toFloat()
        assertEquals(expected, angle, 1e-3f)

        // 稍微偏移 46° (0.80285 rad) 应量化舍入到 45°
        val angleTilted = LiquidPhysicsMath.quantizeGravityAngle(cosDeg(46f), sinDeg(46f))
        assertEquals(expected, angleTilted, 1e-3f)
    }

    @Test
    fun calculateRubberBandOffset_limitsWithinMaxRubberBand() {
        val maxRubberBand = 12f
        val totalWidth = 300f

        // 拖动为 0 时偏移为 0
        assertEquals(0f, LiquidPhysicsMath.calculateRubberBandOffset(0f, totalWidth, maxRubberBand), 1e-4f)

        // 向右拖拽，偏移为正且小于等于 maxRubberBand
        val rightOffset = LiquidPhysicsMath.calculateRubberBandOffset(150f, totalWidth, maxRubberBand)
        assertTrue(rightOffset > 0f)
        assertTrue(rightOffset <= maxRubberBand)

        // 向左拖拽，偏移为负且大于等于 -maxRubberBand
        val leftOffset = LiquidPhysicsMath.calculateRubberBandOffset(-150f, totalWidth, maxRubberBand)
        assertTrue(leftOffset < 0f)
        assertTrue(leftOffset >= -maxRubberBand)
    }

    @Test
    fun calculateVelocityStretch_producesAsymmetricStretchAndCompression() {
        // 静止状态 (v = 0)
        val (sx0, sy0) = LiquidPhysicsMath.calculateVelocityStretch(0f)
        assertEquals(1.0f, sx0, 1e-4f)
        assertEquals(1.0f, sy0, 1e-4f)

        // 高速滑动时 (v > 0)
        val (sxFast, syFast) = LiquidPhysicsMath.calculateVelocityStretch(15f)
        // 水平拉伸，垂直压缩以保持流体体积感
        assertTrue("sxFast should be greater than 1.0", sxFast > 1.0f)
        assertTrue("syFast should be less than 1.0", syFast < 1.0f)
    }

    @Test
    fun isTouchWithinBounds_correctlyIdentifiesHitArea() {
        val totalWidth = 360f
        assertTrue(LiquidPhysicsMath.isTouchWithinBounds(0f, totalWidth))
        assertTrue(LiquidPhysicsMath.isTouchWithinBounds(180f, totalWidth))
        assertTrue(LiquidPhysicsMath.isTouchWithinBounds(360f, totalWidth))
        assertFalse(LiquidPhysicsMath.isTouchWithinBounds(-1f, totalWidth))
        assertFalse(LiquidPhysicsMath.isTouchWithinBounds(361f, totalWidth))
    }

    @Test
    fun calculateInteractiveHighlightCenter_clampsWithinBounds() {
        val width = 300f
        val height = 64f

        val inside = LiquidPhysicsMath.calculateInteractiveHighlightCenter(
            androidx.compose.ui.geometry.Offset(120f, 32f),
            width,
            height
        )
        assertEquals(120f, inside.x, 1e-4f)
        assertEquals(32f, inside.y, 1e-4f)

        val outMin = LiquidPhysicsMath.calculateInteractiveHighlightCenter(
            androidx.compose.ui.geometry.Offset(-20f, -10f),
            width,
            height
        )
        assertEquals(0f, outMin.x, 1e-4f)
        assertEquals(0f, outMin.y, 1e-4f)

        val outMax = LiquidPhysicsMath.calculateInteractiveHighlightCenter(
            androidx.compose.ui.geometry.Offset(350f, 90f),
            width,
            height
        )
        assertEquals(300f, outMax.x, 1e-4f)
        assertEquals(64f, outMax.y, 1e-4f)
    }

    private fun cosDeg(deg: Float): Float = kotlin.math.cos(deg * PI / 180.0).toFloat()
    private fun sinDeg(deg: Float): Float = kotlin.math.sin(deg * PI / 180.0).toFloat()
}
