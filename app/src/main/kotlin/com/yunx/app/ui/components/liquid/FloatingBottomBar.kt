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

/*
 * 液态玻璃悬浮底栏实现整体移植自 pixez-flutter-MIUIX（compose-miuix / FloatingBottomBar.kt），
 * 上游版权与许可：
 *   Copyright 2026, compose-miuix-ui contributors
 *   SPDX-License-Identifier: Apache-2.0
 *   Adapted from InstallerX-Revived (com.rosan.installer.ui.library.FloatingBottomBar)
 *   and compose-miuix-ui official example (component.liquid.LiquidGlassNavigationBar)
 *   with core physics and shader pipeline from Kyant0/AndroidLiquidGlass (Apache 2.0).
 *
 * 本文件持有全部状态与动画并编排三层图层（外壳 / 离屏高亮层 / 透镜滑块），
 * 图层实现见 FloatingBottomBarLayers.kt，槽位项见 FloatingBottomBarItems.kt。
 * 与上游的两处保留差异：纯物理计算继续走 LiquidPhysicsMath（保留单测），
 * 拖拽跟手沿用 CoroutineStart.UNDISPATCHED 与 offsetJob 取消。
 */

package com.yunx.app.ui.components.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 悬浮底栏的最小高度。
 *
 * 胶囊高度不写死：由槽位内容（图标 + 文字 + 上下留白）撑开，内容再矮也不低于此值，
 * 保证与 Miuix 标准底栏同一观感；内容偏高（大字号 / 大图标）时胶囊按需长高。
 */
val FloatingBottomBarMinHeight = 64.dp

/** 胶囊本体上下内边距（每侧 4dp）；滑块高度 = 胶囊高度 - 2 * 该值。 */
internal val FloatingBottomBarVerticalPadding = 4.dp

@Immutable
class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color,
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        activeContentColor: Color = indicatorColor,
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor,
    )
}

enum class FloatingBottomBarMode {
    LiquidGlass,
    Blur,
    None,
}

private val iosIndicatorSpecular: Highlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

/** 高光参考点在归一化 UV 坐标系中的位置：偏下，让镜面反射落在胶囊的中下部。 */
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/**
 * 量化后的平面内重力方向角（弧度）。返回 State，把读取推迟到 Draw 阶段，
 * 避免 50Hz 传感器刷新触发全量重组。
 */
@Composable
private fun rememberQuantizedGravityAngle(): State<Float> {
    val tiltState = rememberDeviceTilt()
    return remember(tiltState) {
        derivedStateOf {
            val tilt = tiltState.value
            LiquidPhysicsMath.quantizeGravityAngle(tilt.gravityX, tilt.gravityY)
        }
    }
}

/**
 * 跟随重力角旋转的高光光源配置；请在 Draw 阶段闭包（如 `highlight = { ... }`）中读取 `.value`。
 */
@Composable
private fun rememberGravityRotatedHighlight(
    base: Highlight,
    extraDegrees: Float = 0f,
): State<Highlight> {
    val gravityAngle = rememberQuantizedGravityAngle()
    return remember(gravityAngle, base, extraDegrees) {
        derivedStateOf {
            val baseStyle = base.style as BloomStroke
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

/**
 * 液态玻璃悬浮底栏（Liquid Glass Floating Bottom Bar）。
 *
 * 三层渲染由 [BottomBarBaseLayer] / [BottomBarActiveTabsLayer] / [BottomBarIndicatorLayer] 承载，
 * 本函数仅负责状态持有与图层编排：双重背景采样透镜折射、色散彩虹晕、阻尼拖拽弹性、
 * 陀螺仪镜面高光、触摸聚光点，以及 LiquidGlass / Blur / None 三态降级。
 */
@Composable
fun FloatingBottomBar(
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    mode: FloatingBottomBarMode = if (isRuntimeShaderSupported() && backdrop != null) {
        FloatingBottomBarMode.LiquidGlass
    } else {
        FloatingBottomBarMode.Blur
    },
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    onBarHeightChanged: ((Dp) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val pillShape = remember { CircleShape }
    val actualMode = resolveFloatingBottomBarMode(
        requested = mode,
        hasBackdrop = backdrop != null,
        shaderSupported = isRuntimeShaderSupported(),
    )
    val isLiquidGlassMode = actualMode == FloatingBottomBarMode.LiquidGlass
    val isBlurMode = actualMode == FloatingBottomBarMode.Blur
    val containerColor = if (isLiquidGlassMode) colors.containerColor.copy(alpha = 0.4f) else colors.containerColor

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    val updatedOnBarHeightChanged by rememberUpdatedState(onBarHeightChanged)

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffsetState = remember(rubberBandPx) {
        derivedStateOf {
            LiquidPhysicsMath.calculateRubberBandOffset(
                dragOffset = offsetAnimation.value,
                totalWidth = totalWidthPx,
                rubberBandPx = rubberBandPx,
            )
        }
    }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
        var offsetJob: Job? = null
    }

    val holder = remember { DampedDragAnimationHolder() }
    var totalDragPx by remember { mutableFloatStateOf(0f) }
    val touchSlopPx = with(density) { 8.dp.toPx() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false

                val currentValue = anim.value
                val indicatorX = currentValue * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                LiquidPhysicsMath.isTouchWithinBounds(globalTouchX, totalWidthPx)
            },
            onDragStarted = {
                totalDragPx = 0f
            },
            onDragStopped = {
                val dropIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                val isTapOnSameTab = (dropIndex == currentSelectedIndex()) && (totalDragPx < touchSlopPx)
                val isSwitched = dropIndex != currentSelectedIndex()

                animateToValue(dropIndex.toFloat())
                holder.offsetJob?.cancel()
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
                if (isSwitched || isTapOnSameTab) {
                    updatedOnSelected(dropIndex)
                }
            },
            onDrag = { _, dragAmount ->
                totalDragPx += abs(dragAmount.x)
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    holder.offsetJob?.cancel()
                    holder.offsetJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentSelectedIndex() }.collectLatest { index ->
            val coercedIndex = index.coerceIn(0, tabsCount - 1)
            if (abs(dampedDragAnimation.value - coercedIndex.toFloat()) > 0.001f) {
                dampedDragAnimation.animateToValue(coercedIndex.toFloat())
            }
        }
    }

    val interactiveHighlight =
        if (isLiquidGlassMode) {
            remember(animationScope, tabWidthPx, isLtr) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffsetState.value
                            else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffsetState.value,
                            size.height / 2f,
                        )
                    },
                )
            }
        } else {
            null
        }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }

    // 滑块高度跟随外壳实测高度（未测量时退回最小高度），保证与胶囊内区始终等高对齐。
    val indicatorHeight =
        (with(density) { barHeightPx.toDp() } - FloatingBottomBarVerticalPadding * 2)
            .coerceAtLeast(FloatingBottomBarMinHeight - FloatingBottomBarVerticalPadding * 2)

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ── 1. Base Layer（未选中状态底层外壳） ──
        BottomBarBaseLayer(
            colors = colors,
            pillShape = pillShape,
            panelOffsetState = panelOffsetState,
            isDark = isDark,
            isLiquidGlassMode = isLiquidGlassMode,
            isBlurMode = isBlurMode,
            containerColor = containerColor,
            backdrop = backdrop,
            dampedDragAnimation = dampedDragAnimation,
            baseHighlight = baseHighlight,
            interactiveHighlight = interactiveHighlight,
            onSizeChanged = { coords ->
                totalWidthPx = coords.width.toFloat()
                val measuredHeightPx = coords.height.toFloat()
                if (measuredHeightPx > 0f && measuredHeightPx != barHeightPx) {
                    barHeightPx = measuredHeightPx
                    updatedOnBarHeightChanged?.invoke(with(density) { measuredHeightPx.toDp() })
                }
                val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
            },
            content = content,
        )

        // ── 2. Active Tabs Layer（激活状态隐藏层，供 tabsBackdrop 录制高亮状态） ──
        BottomBarActiveTabsLayer(
            isLiquidGlassMode = isLiquidGlassMode,
            backdrop = backdrop,
            pillShape = pillShape,
            panelOffsetState = panelOffsetState,
            dampedDragAnimation = dampedDragAnimation,
            colors = colors,
            containerColor = containerColor,
            interactiveHighlight = interactiveHighlight,
            tabsBackdrop = tabsBackdrop,
            content = content,
        )

        // ── 3. Indicator Layer（双重背景采样透镜折射滑块） ──
        BottomBarIndicatorLayer(
            tabWidthPx = tabWidthPx,
            totalWidthPx = totalWidthPx,
            density = density,
            indicatorHeight = indicatorHeight,
            isLiquidGlassMode = isLiquidGlassMode,
            combinedBackdrop = combinedBackdrop,
            dampedDragAnimation = dampedDragAnimation,
            panelOffsetState = panelOffsetState,
            interactiveHighlight = interactiveHighlight,
            pillHighlight = pillHighlight,
            pillShape = pillShape,
            colors = colors,
            isDark = isDark,
            isLtr = isLtr,
            content = content,
        )
    }
}