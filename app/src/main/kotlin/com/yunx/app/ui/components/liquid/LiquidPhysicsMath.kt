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

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 液态玻璃物理与传感器数学计算工具类。
 * 抽离纯计算逻辑，确保核心阻尼、速度形变与 3° 量化重力角度可测且与平台渲染解耦。
 */
object LiquidPhysicsMath {

    private const val GRAVITY_DIR_THRESHOLD_SQ = 0.01f // |g_xy| > 0.1, 约 6° 倾斜
    private const val GRAVITY_ANGLE_STEP_RAD = (3.0 * PI / 180.0).toFloat() // 3° 量化步长

    /**
     * 对设备重力矢量 (gx, gy) 进行 3° 步长量化。
     * 当平放手机或倾斜角低于阈值时，固定朝向顶部 (-90°)。
     */
    fun quantizeGravityAngle(gx: Float, gy: Float): Float {
        val gMagSq = gx * gx + gy * gy
        return if (gMagSq > GRAVITY_DIR_THRESHOLD_SQ) {
            (atan2(gy, gx) / GRAVITY_ANGLE_STEP_RAD).roundToInt() * GRAVITY_ANGLE_STEP_RAD
        } else {
            (-PI / 2).toFloat()
        }
    }

    /**
     * 计算橡皮筋面板阻尼拖拽位移 (EaseOut 弹性曲线)。
     */
    fun calculateRubberBandOffset(dragOffset: Float, totalWidth: Float, rubberBandPx: Float): Float {
        if (totalWidth <= 0f) return 0f
        val fraction = (dragOffset / totalWidth).coerceIn(-1f, 1f)
        val progress = abs(fraction)
        // EaseOut cubic: 1 - (1 - t)^3
        val easeOut = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        return rubberBandPx * fraction.sign * easeOut
    }

    /**
     * 根据当前滑块运动速度计算流体非对称拉伸形变。
     * 返回 (scaleX, scaleY)：水平拉伸、垂直相应压缩以维持流体体积感。
     */
    fun calculateVelocityStretch(velocity: Float): Pair<Float, Float> {
        val v = velocity / 10f
        val sx = 1f / (1f - (v * 0.75f).coerceIn(-0.2f, 0.2f))
        val sy = 1f * (1f - (v * 0.25f).coerceIn(-0.2f, 0.2f))
        return Pair(sx, sy)
    }

    /**
     * 检测触控坐标是否在底栏有效物理响应范围内。
     */
    fun isTouchWithinBounds(touchX: Float, totalWidth: Float): Boolean {
        return touchX in 0f..totalWidth
    }

    /**
     * 计算交互聚光点的有效几何中心坐标，限制在底栏物理边界内。
     */
    fun calculateInteractiveHighlightCenter(
        touchOffset: androidx.compose.ui.geometry.Offset,
        containerWidth: Float,
        containerHeight: Float
    ): androidx.compose.ui.geometry.Offset {
        val cx = touchOffset.x.coerceIn(0f, containerWidth.coerceAtLeast(0f))
        val cy = touchOffset.y.coerceIn(0f, containerHeight.coerceAtLeast(0f))
        return androidx.compose.ui.geometry.Offset(cx, cy)
    }
}
