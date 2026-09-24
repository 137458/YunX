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

package com.yunx.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** AdaptiveLayout 纯函数边界测试。 */
class AdaptiveLayoutTest {

    // ---- gridColumns ----

    /** 常规宽度：按最小列宽取整列数，不超过用户配置上限。 */
    @Test
    fun gridColumns_usesFitCountWithinConfiguredLimit() {
        // 1000 / 180 = 5（向下取整），上限 6 -> 5
        assertEquals(5, AdaptiveLayout.gridColumns(1000, configuredColumns = 6))
        // 上限 3 时被钳制
        assertEquals(3, AdaptiveLayout.gridColumns(1000, configuredColumns = 3))
    }

    /** 0 宽（尚未测量）：按 1 列处理，不能出现 0 列导致空网格。 */
    @Test
    fun gridColumns_zeroWidthFallsBackToOneColumn() {
        assertEquals(1, AdaptiveLayout.gridColumns(0, configuredColumns = 6))
    }

    /** 负宽（异常测量值）：同样回退为 1 列。 */
    @Test
    fun gridColumns_negativeWidthFallsBackToOneColumn() {
        assertEquals(1, AdaptiveLayout.gridColumns(-500, configuredColumns = 6))
    }

    /** 极小宽：放不下一列时仍保留 1 列（宁可列窄，也不能没有列）。 */
    @Test
    fun gridColumns_tinyWidthKeepsOneColumn() {
        assertEquals(1, AdaptiveLayout.gridColumns(1, configuredColumns = 6))
        assertEquals(1, AdaptiveLayout.gridColumns(179, configuredColumns = 6))
    }

    /** 恰好等于最小列宽：刚好放 1 列。 */
    @Test
    fun gridColumns_exactlyMinWidthFitsOneColumn() {
        assertEquals(1, AdaptiveLayout.gridColumns(180, configuredColumns = 6))
        assertEquals(2, AdaptiveLayout.gridColumns(360, configuredColumns = 6))
    }

    /** 超大宽：受 configuredColumns 限制，不会无限增列。 */
    @Test
    fun gridColumns_hugeWidthIsCappedByConfiguredColumns() {
        assertEquals(8, AdaptiveLayout.gridColumns(Int.MAX_VALUE, configuredColumns = 8))
    }

    /** configuredColumns 越界（0 / 负数）：回退为 1 列。 */
    @Test
    fun gridColumns_invalidConfiguredColumnsFallsBackToOne() {
        assertEquals(1, AdaptiveLayout.gridColumns(1000, configuredColumns = 0))
        assertEquals(1, AdaptiveLayout.gridColumns(1000, configuredColumns = -3))
    }

    /** minColumnWidthDp 越界（0 / 负数）：不得抛除零异常。 */
    @Test
    fun gridColumns_invalidMinWidthDoesNotDivideByZero() {
        assertEquals(4, AdaptiveLayout.gridColumns(4, configuredColumns = 8, minColumnWidthDp = 0))
        assertEquals(4, AdaptiveLayout.gridColumns(4, configuredColumns = 8, minColumnWidthDp = -10))
    }

    // ---- readableWidth ----

    /** 紧凑宽度：返回 null，表示铺满可用宽度。 */
    @Test
    fun readableWidth_compactReturnsNull() {
        assertNull(AdaptiveLayout.readableWidth(360))
        assertNull(AdaptiveLayout.readableWidth(600))
        assertNull(AdaptiveLayout.readableWidth(760))
    }

    /** 0 / 负宽：尚未测量，返回 null。 */
    @Test
    fun readableWidth_nonPositiveReturnsNull() {
        assertNull(AdaptiveLayout.readableWidth(0))
        assertNull(AdaptiveLayout.readableWidth(-100))
    }

    /** 中间档（compactMaxDp < w < wideMinDp）：返回可用宽度本身，不做限宽。 */
    @Test
    fun readableWidth_betweenThresholdsReturnsAvailableWidth() {
        // 默认参数下 compactMaxDp(760) > wideMinDp(600)，中间档不可达；
        // 用自定义阈值构造 compactMaxDp < w < wideMinDp 验证该分支。
        assertEquals(
            700,
            AdaptiveLayout.readableWidth(700, wideMinDp = 800, compactMaxDp = 600)
        )
    }

    /** 宽屏：钉在 wideMaxDp。 */
    @Test
    fun readableWidth_wideIsCappedAtMax() {
        assertEquals(900, AdaptiveLayout.readableWidth(1200))
        assertEquals(900, AdaptiveLayout.readableWidth(Int.MAX_VALUE))
    }

    /** 宽屏但可用宽度小于上限：返回可用宽度，不放大。 */
    @Test
    fun readableWidth_wideNeverExceedsAvailable() {
        assertEquals(
            850,
            AdaptiveLayout.readableWidth(850, wideMinDp = 800, wideMaxDp = 900)
        )
    }

    /** 宽屏档上限被配成非正数：返回 null（不限宽），不能把 0 当作宽度上限。 */
    @Test
    fun readableWidth_nonPositiveMaxFallsBackToNull() {
        // 500 > compactMaxDp(0) 且 500 >= wideMinDp(0)，会走到宽屏档
        assertNull(AdaptiveLayout.readableWidth(500, wideMinDp = 0, compactMaxDp = 0, wideMaxDp = 0))
        assertNull(AdaptiveLayout.readableWidth(1200, wideMinDp = 0, compactMaxDp = 0, wideMaxDp = -1))
    }

    /** 阈值全为 0 且内容宽度为 0：未测量，直接返回 null。 */
    @Test
    fun readableWidth_zeroWidthWithZeroThresholdsReturnsNull() {
        assertNull(AdaptiveLayout.readableWidth(0, wideMinDp = 0, compactMaxDp = 0, wideMaxDp = 0))
    }
}
