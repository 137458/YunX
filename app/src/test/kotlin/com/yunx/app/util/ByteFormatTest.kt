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

package com.yunx.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    @Test
    fun formatsSizeAcrossUnits() {
        assertEquals("0 B", ByteFormat.size(0))
        assertEquals("0 B", ByteFormat.size(-5))
        assertEquals("512.0 B", ByteFormat.size(512))
        assertEquals("1.0 KB", ByteFormat.size(1024))
        assertEquals("1.5 MB", ByteFormat.size((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", ByteFormat.size(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun formatsSpeedWithPerSecondSuffix() {
        assertEquals("0 B/s", ByteFormat.speed(0))
        assertEquals("0 B/s", ByteFormat.speed(-1))
        assertEquals("1.0 KB/s", ByteFormat.speed(1024))
        assertEquals("2.0 MB/s", ByteFormat.speed(2L * 1024 * 1024))
    }

    @Test
    fun capsAtTerabytesInsteadOfOverflowingUnitTable() {
        // 超过 TB 后不再进位，避免数组越界；数值继续放大但仍标 TB
        assertEquals("1024.0 TB", ByteFormat.size(1024L * 1024 * 1024 * 1024 * 1024))
    }
}
