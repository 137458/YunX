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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import top.yukonga.miuix.kmp.squircle.addSquircleRect

/**
 * Miuix 风格平滑连续曲率仿超椭圆形状（Squircle Shape）。
 * 替代标准的 RoundedCornerShape，提供更平滑柔和的圆角视觉。
 */
class SquircleShape(
    val corner: Dp,
    val squircleEnabled: Boolean = true
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cornerPx = with(density) { corner.toPx() }
        val path = Path().apply {
            addSquircleRect(
                width = size.width,
                height = size.height,
                cornerRadius = cornerPx,
                squircleEnabled = squircleEnabled
            )
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return corner == other.corner && squircleEnabled == other.squircleEnabled
    }

    override fun hashCode(): Int {
        var result = corner.hashCode()
        result = 31 * result + squircleEnabled.hashCode()
        return result
    }

    override fun toString(): String = "SquircleShape(corner=$corner, squircleEnabled=$squircleEnabled)"
}

fun SquircleShape(corner: Dp): SquircleShape = SquircleShape(corner, squircleEnabled = true)
