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

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

@Immutable
data class FloatingBottomBarColors(
    val containerColor: Color,
    val indicatorColor: Color,
    val contentColor: Color,
    val activeContentColor: Color
)

object FloatingBottomBarDefaults {
    @Composable
    fun colors(
        containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        activeContentColor: Color = MiuixTheme.colorScheme.primary
    ): FloatingBottomBarColors = FloatingBottomBarColors(
        containerColor = containerColor,
        indicatorColor = indicatorColor,
        contentColor = contentColor,
        activeContentColor = activeContentColor
    )
}

enum class FloatingBottomBarMode {
    LiquidGlass, // API >= 33 原生着色器与物理透镜
    Blur,        // 高斯模糊降级
    None         // 极速纯色/半透明降级
}

fun isRuntimeShaderSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val contentColor = LocalFloatingBottomBarContentColor.current

    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(
            top.yukonga.miuix.kmp.theme.LocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            content()
        }
    }
}

/**
 * 液态玻璃悬浮底栏 (FloatingBottomBar)。
 * 具备三态物理渲染管线、阻尼拖拽弹性手感、陀螺仪重力高光与速度拉伸形变。
 */
@Composable
fun FloatingBottomBar(
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    mode: FloatingBottomBarMode = if (isRuntimeShaderSupported()) FloatingBottomBarMode.LiquidGlass else FloatingBottomBarMode.Blur,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    content: @Composable RowScope.() -> Unit
) {
    val isInDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val isLiquidGlassMode = mode == FloatingBottomBarMode.LiquidGlass
    val isBlurMode = mode == FloatingBottomBarMode.Blur

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 5.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            LiquidPhysicsMath.calculateRubberBandOffset(offsetAnimation.value, totalWidthPx, rubberBandPx)
        }
    }

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
    }
    val holder = remember { DampedDragAnimationHolder() }

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
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight =
        if (isLiquidGlassMode && isRuntimeShaderSupported()) {
            remember(animationScope, tabWidthPx) {
                InteractiveHighlight(
                    animationScope = animationScope,
                    position = { size, _ ->
                        Offset(
                            if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                            else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                            size.height / 2f
                        )
                    }
                )
            }
        } else null

    // 陀螺仪旋转高光（外壳 -45° 偏移，滑块 +90° 偏移）
    val baseSpecularBrush by rememberGravitySpecularBrush(
        extraDegrees = -45f,
        baseColor = if (isInDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.45f)
    )
    val pillSpecularBrush by rememberGravitySpecularBrush(
        extraDegrees = 90f,
        baseColor = if (isInDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f),
        highlightAlpha = dampedDragAnimation.pressProgress
    )

    val containerColor = when {
        isLiquidGlassMode -> colors.containerColor.copy(alpha = if (isInDark) 0.60f else 0.72f)
        isBlurMode -> colors.containerColor.copy(alpha = if (isInDark) 0.75f else 0.85f)
        else -> colors.containerColor
    }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart
    ) {
        // Layer 1: 底栏外壳（基础层）
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                    tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer {
                    translationX = panelOffset
                    val s = lerp(1f, 1f + with(density) { 8.dp.toPx() } / totalWidthPx.coerceAtLeast(1f), dampedDragAnimation.pressProgress * 0.4f)
                    scaleX = s
                    scaleY = s
                }
                .shadow(
                    elevation = if (isLiquidGlassMode) 10.dp else 4.dp,
                    shape = pillShape,
                    ambientColor = Color.Black.copy(alpha = if (isInDark) 0.15f else 0.08f),
                    spotColor = Color.Black.copy(alpha = if (isInDark) 0.25f else 0.12f)
                )
                .clip(pillShape)
                .then(
                    if (isLiquidGlassMode || isBlurMode) {
                        Modifier.blur(if (isLiquidGlassMode) 16.dp else 25.dp)
                    } else Modifier
                )
                .background(containerColor, pillShape)
                .border(
                    width = 1.dp,
                    brush = baseSpecularBrush,
                    shape = pillShape
                )
                .then(if (isLiquidGlassMode && interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                .height(64.dp)
                .fillMaxHeight()
                .padding(4.dp)
        )

        // Layer 2: 弹性物理滑块指示器
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            val (stretchX, stretchY) = LiquidPhysicsMath.calculateVelocityStretch(dampedDragAnimation.velocity)

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        scaleX = dampedDragAnimation.scaleX * stretchX
                        scaleY = dampedDragAnimation.scaleY * stretchY
                    }
                    .then(interactiveHighlight?.gestureModifier ?: Modifier)
                    .then(dampedDragAnimation.modifier)
                    .shadow(
                        elevation = if (isLiquidGlassMode) 6.dp else 2.dp,
                        shape = pillShape,
                        ambientColor = colors.indicatorColor.copy(alpha = 0.12f),
                        spotColor = colors.indicatorColor.copy(alpha = 0.20f)
                    )
                    .clip(pillShape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.indicatorColor.copy(alpha = if (isLiquidGlassMode) 0.22f else 0.16f),
                                colors.indicatorColor.copy(alpha = if (isLiquidGlassMode) 0.12f else 0.08f)
                            )
                        ),
                        shape = pillShape
                    )
                    .border(
                        width = 1.dp,
                        brush = pillSpecularBrush,
                        shape = pillShape
                    )
                    // 内阴影与凹陷质感
                    .drawWithContent {
                        drawContent()
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0.01f) {
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.10f * progress),
                                size = size,
                                style = Stroke(width = with(density) { 3.dp.toPx() * progress }),
                                blendMode = BlendMode.SrcOver
                            )
                        }
                    }
                    .height(56.dp)
                    .width(tabWidthDp)
            )
        }

        // Layer 3: Tab 图标与文字内容
        CompositionLocalProvider(
            LocalFloatingBottomBarContentColor provides colors.contentColor,
            LocalFloatingBottomBarTabScale provides {
                lerp(1f, 1.15f, dampedDragAnimation.pressProgress * 0.6f)
            }
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}
