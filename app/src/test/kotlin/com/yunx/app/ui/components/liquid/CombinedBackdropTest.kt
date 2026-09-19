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

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class CombinedBackdropTest {

    private class TestBackdrop(
        override val isCoordinatesDependent: Boolean = false,
        override val offsetResidualX: Float = 0f,
        override val offsetResidualY: Float = 0f,
    ) : Backdrop {
        var callCount = 0

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Int,
        ) {
            callCount++
        }
    }

    @Test
    fun isCoordinatesDependent_returnsTrueIfEitherIsDependent() {
        val fFalse = TestBackdrop(isCoordinatesDependent = false)
        val sFalse = TestBackdrop(isCoordinatesDependent = false)
        val bothFalse = CombinedBackdrop(fFalse, sFalse)
        assertFalse(bothFalse.isCoordinatesDependent)

        val fTrue = TestBackdrop(isCoordinatesDependent = true)
        val oneTrue = CombinedBackdrop(fTrue, sFalse)
        assertTrue(oneTrue.isCoordinatesDependent)

        val sTrue = TestBackdrop(isCoordinatesDependent = true)
        val otherTrue = CombinedBackdrop(fFalse, sTrue)
        assertTrue(otherTrue.isCoordinatesDependent)
    }

    @Test
    fun offsetResidual_delegatesToFirstBackdrop() {
        val first = TestBackdrop(offsetResidualX = 12.5f, offsetResidualY = 4.2f)
        val second = TestBackdrop(offsetResidualX = 99.0f, offsetResidualY = 88.0f)
        val combined = CombinedBackdrop(first, second)

        assertEquals(12.5f, combined.offsetResidualX, 1e-4f)
        assertEquals(4.2f, combined.offsetResidualY, 1e-4f)
    }
}
