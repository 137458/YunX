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

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 高光参考点在归一化 UV 坐标系中的位置：偏下，让镜面反射落在胶囊的中下部。 */
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/**
 * 对传感器重力方向进行 3° 步长量化。
 * 器件角度取自 miuix-blur 的 [rememberDeviceTilt]（自带生命周期注销与平滑），
 * 这里只做量化并包装为 State，仅供 Draw 阶段闭包中延迟解构，彻底避免 Composition 重组风暴。
 */
@Composable
fun rememberQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            LiquidPhysicsMath.quantizeGravityAngle(tilt.gravityX, tilt.gravityY)
        }
    }
}

/**
 * 依据量化重力角旋转的高光反射点配置（仅供 Draw 阶段 highlight 闭包延迟调用）。
 */
@Composable
fun rememberGravityRotatedHighlight(
    base: Highlight = Highlight.Default,
    extraDegrees: Float = 0f,
): State<Highlight> {
    val gravityAngle = rememberQuantizedGravityAngle()
    return remember(gravityAngle, base, extraDegrees) {
        derivedStateOf {
            val baseStyle = base.style as? BloomStroke
            if (baseStyle == null) {
                base
            } else {
                val basePrimary = baseStyle.primaryLight
                val rad = gravityAngle.value + (extraDegrees * PI / 180.0).toFloat()
                base.copy(
                    style = baseStyle.copy(
                        primaryLight = basePrimary.copy(
                            position = LightPosition(
                                x = LIGHT_REF_X + cos(rad),
                                y = LIGHT_REF_Y + sin(rad),
                                z = basePrimary.position.z,
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

/**
 * 触摸聚光点交互控制器。
 * 在用户手指按下与滑动时产生动态聚光光斑，光斑绘制在内容之下（玻璃表面打光，而非盖在图标上）。
 *
 * [position] 允许把光斑中心重定向到滑块几何位置而非原始触点，使光晕始终跟随液态滑块。
 */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    /** 手势起始触点：抬手时聚光点回落到起点，避免停在滑动终点的突兀感 */
    private var startPosition = Offset.Zero

    private val spotShader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(SPOT_SHADER) }.getOrNull()
        } else null

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(
                color = Color.White.copy(alpha = 0.06f * progress),
                blendMode = BlendMode.Plus,
            )
            val center = LiquidPhysicsMath.calculateInteractiveHighlightCenter(
                touchOffset = position(size, positionAnimation.value),
                containerWidth = size.width,
                containerHeight = size.height
            )
            val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
            val spotColor = Color.White.copy(alpha = 0.12f * progress)
            val shader = spotShader
            if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                shader.setColorUniform("color", android.graphics.Color.valueOf(spotColor.red, spotColor.green, spotColor.blue, spotColor.alpha))
                shader.setFloatUniform("radius", radius)
                shader.setFloatUniform("position", center.x, center.y)
                drawRect(
                    brush = ShaderBrush(shader),
                    blendMode = BlendMode.Plus,
                )
            } else {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to spotColor,
                            0.5f to spotColor,
                            1.0f to Color.White.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = radius,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        // 内容后绘：聚光点作为玻璃底材的打光，不被图标遮挡
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }
}

private const val SPOT_SHADER = """
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.5, dist);
        return color * intensity;
    }
"""
