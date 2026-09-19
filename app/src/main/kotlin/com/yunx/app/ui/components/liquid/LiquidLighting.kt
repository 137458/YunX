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

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class DeviceTilt(
    val gravityX: Float = 0f,
    val gravityY: Float = 0f,
    val gravityZ: Float = 9.8f
)

/**
 * 监听设备重力/加速度计传感器，生命周期感知（后台 ON_STOP 时自动注销，防止功耗泄漏）。
 */
@Composable
fun rememberDeviceTilt(): State<DeviceTilt> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltState = remember { mutableStateOf(DeviceTilt()) }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose {}
        }

        var isRegistered = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.let { v ->
                    if (v.size >= 3) {
                        tiltState.value = DeviceTilt(
                            gravityX = v[0] / SensorManager.GRAVITY_EARTH,
                            gravityY = v[1] / SensorManager.GRAVITY_EARTH,
                            gravityZ = v[2] / SensorManager.GRAVITY_EARTH
                        )
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        fun register() {
            if (!isRegistered) {
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                isRegistered = true
            }
        }

        fun unregister() {
            if (isRegistered) {
                sensorManager.unregisterListener(listener)
                isRegistered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> unregister()
                Lifecycle.Event.ON_DESTROY -> unregister()
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return tiltState
}

/**
 * 对传感器重力方向进行 3° 步长量化。
 * 包装为 State，仅供 Draw 阶段闭包中延迟解构，彻底避免 Composition 重组风暴。
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
                                x = 0.5f + cos(rad),
                                y = 0.7f + sin(rad),
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
 * 在用户手指按下与滑动时，根据真实物理触控坐标产生动态聚光晕光斑。
 */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private val spotShader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(SPOT_SHADER) }.getOrNull()
        } else null

    val modifier: Modifier = Modifier.drawWithContent {
        drawContent()
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(
                color = Color.White.copy(alpha = 0.05f * progress),
                blendMode = BlendMode.Plus,
            )
            val center = LiquidPhysicsMath.calculateInteractiveHighlightCenter(
                touchOffset = positionAnimation.value,
                containerWidth = size.width,
                containerHeight = size.height
            )
            val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
            val spotColor = Color.White.copy(alpha = 0.16f * progress)
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
                            0.5f to spotColor.copy(alpha = spotColor.alpha * 0.5f),
                            1.0f to Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(down.position) }
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
        }
    }
}

private const val SPOT_SHADER = """
    layout(color) uniform half4 color;
    uniform float radius;
    uniform float2 position;

    half4 main(float2 coord) {
        float dist = distance(coord, position);
        float intensity = smoothstep(radius, radius * 0.4, dist);
        return color * intensity;
    }
"""
