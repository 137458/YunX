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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidOpticsTest {

    @Test
    fun capCornerRadii_preservesValuesWithinBounds() {
        // 四角半径均小于短边一半时原样保留，顺序为 [左上, 右上, 右下, 左下]
        val radii = floatArrayOf(8f, 12f, 16f, 20f)
        val capped = capCornerRadii(radii, maxRadius = 28f)

        assertArrayEquals(floatArrayOf(8f, 12f, 16f, 20f), capped, 1e-4f)
    }

    @Test
    fun capCornerRadii_capsValuesAboveHalfMinDimension() {
        // 胶囊 50% 圆角与超大圆角都会被短边一半封顶，避免着色器取样越界
        val radii = floatArrayOf(20f, 40f, 60f, 80f)
        val capped = capCornerRadii(radii, maxRadius = 50f)

        assertArrayEquals(floatArrayOf(20f, 40f, 50f, 50f), capped, 1e-4f)
    }

    @Test
    fun capCornerRadii_clampsNegativeValuesToZero() {
        // 非法负半径归零，其余角仍按上界裁剪
        val radii = floatArrayOf(-4f, 12f, 999f, 3f)
        val capped = capCornerRadii(radii, maxRadius = 100f)

        assertArrayEquals(floatArrayOf(0f, 12f, 100f, 3f), capped, 1e-4f)
        assertEquals(4, capped.size)
    }

    @Test
    fun capCornerRadii_whenMaxRadiusInvalid_returnsAllZero() {
        // 短边一半非法（0 或负）时全部归零，而不是抛出异常
        assertArrayEquals(floatArrayOf(0f, 0f), capCornerRadii(floatArrayOf(10f, 20f), maxRadius = 0f), 1e-4f)
        assertArrayEquals(floatArrayOf(0f, 0f), capCornerRadii(floatArrayOf(10f, 20f), maxRadius = -5f), 1e-4f)
    }
}