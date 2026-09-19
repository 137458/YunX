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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.LocalContentColor as MiuixLocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/** 底栏内容色：由各图层按「未选中 / 选中」分别注入，槽位内容无需自行判断状态。 */
val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }

/** 底栏内容缩放：按压时随阻尼进度放大，制造"被玻璃顶起"的手感。 */
val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

@Immutable
data class FloatingBottomBarColors(
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

/**
 * 悬浮底栏渲染模式。
 *
 * 用户侧只在 [BottomBarStyle] 里做三选一，这里保留三态是为了表达运行时降级结果。
 */
enum class FloatingBottomBarMode {
    /** API >= 33 原生着色器透镜折射 + 色散彩虹晕 + 双重 Backdrop 采样 */
    LiquidGlass,

    /** Backdrop 高斯模糊降级 */
    Blur,

    /** 极速纯色/半透明降级 */
    None
}

/**
 * 双光源泛光描边高光。
 * 主光承担陀螺仪旋转的镜面反射，副光补底部环境反射，避免旋转到背面时整片死黑。
 */
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

/** 高光参考点在归一化 UV 坐标系中的位置 */
private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

/**
 * 槽位式底栏项。
 * 语义、[Role.Tab]、键盘激活与按压缩放由本组件统一处理，槽位内容只管画图标与文字。
 */
@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalFloatingBottomBarTabScale.current
    val contentColor = LocalFloatingBottomBarContentColor.current

    Column(
        modifier = modifier
            .clip(CircleShape)
            .semantics {
                this.role = Role.Tab
                this.selected = selected
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // 遥控器 / 外接键盘：Enter 与空格激活（KeyUp 才触发，避免长按连发）
            .onKeyEvent { event ->
                val isActivationKey = event.key == Key.Enter ||
                    event.key == Key.NumPadEnter ||
                    event.key == Key.Spacebar
                if (isActivationKey) {
                    if (event.type == KeyEventType.KeyUp) onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(
            MiuixLocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

/**
 * 液态玻璃悬浮底栏（Liquid Glass Floating Bottom Bar）。
 *
 * 三层绘制管线（叠加在同一个 Box 上）：
 * 1. 外壳层：Backdrop 采样 + 活力增强 + 4dp 模糊 + 24dp SDF 透镜折射 + 陀螺仪旋转高光，
 *    并承载「未选中配色」的图标与文字；
 * 2. 离屏高亮层：把同一份内容以「选中配色」再画一遍并录进 tabsBackdrop（同样过一遍玻璃光学），
 *    供滑块做双重背景采样——这是滑块能"透过两层玻璃"看到高亮文字的关键；
 * 3. 滑块层：双重背景采样 + 10dp 透镜 + 0.5 色散彩虹晕 + 速度非对称形变 + 内阴影凹陷；
 *    降级档位下改为直接承载「选中配色内容」的平移副本，保证高亮文字始终跟着胶囊走。
 *
 * 渲染模式经 [resolveFloatingBottomBarMode] 降级：无运行时着色器退毛玻璃，无 Backdrop 退纯色。
 */
@Composable
fun FloatingBottomBar(
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    content: @Composable RowScope.() -> Unit,
) {
    // 用主题 surface 亮度判深浅，而非系统深色开关：应用内可能手动覆盖明暗模式
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val pillShape = remember { CircleShape }

    val actualMode = resolveFloatingBottomBarMode(
        requested = mode,
        hasBackdrop = backdrop != null,
        shaderSupported = isRuntimeShaderSupported(),
    )
    val isLiquidGlassMode = actualMode == FloatingBottomBarMode.LiquidGlass
    val isBlurMode = actualMode == FloatingBottomBarMode.Blur

    val containerColor = if (isLiquidGlassMode) {
        colors.containerColor.copy(alpha = if (isDark) 0.4f else 0.5f)
    } else {
        colors.containerColor.copy(alpha = if (isDark) 0.8f else 0.9f)
    }

    // 仅在真正需要离屏采样时创建，避免非液态玻璃档位白白多一层离屏缓冲
    val tabsBackdrop = if (isLiquidGlassMode) rememberLayerBackdrop() else null

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            LiquidPhysicsMath.calculateRubberBandOffset(
                dragOffset = offsetAnimation.value,
                totalWidth = totalWidthPx,
                rubberBandPx = rubberBandPx,
            )
        }
    }

    // 回调用 rememberUpdatedState 包一层，避免父级重组时把旧 lambda 闭进长驻的 pointerInput
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val updatedOnSelected by rememberUpdatedState(onSelected)

    class DampedDragAnimationHolder {
        var instance: DampedDragAnimation? = null
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
                // 原地轻点当前 Tab 也算一次选中（用于"回到顶部"这类页面内行为）
                val isTapOnSameTab = dropIndex == currentSelectedIndex() && totalDragPx < touchSlopPx
                val isSwitched = dropIndex != currentSelectedIndex()

                animateToValue(dropIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
                if (isSwitched || isTapOnSameTab) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    // 外部选中项变化（点击 Tab、代码跳转）→ 滑块归位；容差避免与拖拽动画互相打断
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentSelectedIndex() }.collectLatest { index ->
            val coercedIndex = index.coerceIn(0, tabsCount - 1)
            if (abs(dampedDragAnimation.value - coercedIndex.toFloat()) > 0.001f) {
                dampedDragAnimation.animateToValue(coercedIndex.toFloat())
            }
        }
    }

    // 聚光点跟随滑块几何中心，而不是原始触点：手指拖出底栏时光晕仍留在胶囊上
    val interactiveHighlight = if (isLiquidGlassMode) {
        remember(animationScope, dampedDragAnimation, tabWidthPx, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) {
                            (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                        },
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

    val combinedBackdrop = if (isLiquidGlassMode && backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        null
    }

    // 折射留白：透镜会把采样范围外扩 refractionAmount，不预留就会在边缘切断成黑边
    val lensPaddingPx = with(density) { 40.dp.toPx() }
    val blur4Px = with(density) { 4.dp.toPx() }
    val blur25Px = with(density) { 25.dp.toPx() }
    val lens24Px = with(density) { 24.dp.toPx() }
    val lensHeight10Px = with(density) { 10.dp.toPx() }
    val lensAmount14Px = with(density) { 14.dp.toPx() }
    val pressScalePx = with(density) { 16.dp.toPx() }

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ── 第 1 层：外壳（基础层，承载未选中态内容与折射底材） ──
        CompositionLocalProvider(LocalFloatingBottomBarContentColor provides colors.contentColor) {
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .onSizeChanged { coords ->
                        totalWidthPx = coords.width.toFloat()
                        val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                        tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                    }
                    .graphicsLayer { translationX = panelOffset }
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isDark) 0.2f else 0.1f,
                        ),
                    )
                    // 吞掉底栏空白处的点击，避免穿透到下层列表
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .then(
                        if (isLiquidGlassMode && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    padding = maxOf(padding, lensPaddingPx)
                                    vibrancy()
                                    blur(blur4Px, blur4Px)
                                    lens(
                                        refractionHeight = lens24Px,
                                        refractionAmount = lens24Px,
                                    )
                                },
                                highlight = { baseHighlight.value.copy(alpha = 0.75f) },
                                layerBlock = {
                                    val width = size.width.coerceAtLeast(1f)
                                    val s = lerp(1f, 1f + pressScalePx / width, dampedDragAnimation.pressProgress)
                                    scaleX = s
                                    scaleY = s
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                        } else if (isBlurMode && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    blur(blur25Px, blur25Px)
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                        } else {
                            Modifier.background(containerColor, pillShape)
                        },
                    )
                    .then(if (isLiquidGlassMode && interactiveHighlight != null) interactiveHighlight.modifier else Modifier)
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // ── 第 2 层：离屏高亮层（录制"选中配色 + 已折射底材"供滑块二次采样） ──
        // 注意：这一层必须同样过一遍 drawBackdrop，否则滑块采到的是原始页面内容，
        // 而不是"透过外壳玻璃看到的内容"，双重折射的层次感会丢失。
        if (isLiquidGlassMode && combinedBackdrop != null && tabsBackdrop != null && backdrop != null) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalFloatingBottomBarContentColor provides colors.activeContentColor,
            ) {
                Row(
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                padding = maxOf(padding, lensPaddingPx)
                                vibrancy()
                                blur(blur4Px, blur4Px)
                                lens(refractionHeight = lens24Px, refractionAmount = lens24Px)
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight?.modifier ?: Modifier)
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        // ── 第 3 层：弹性物理滑块 ──
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }

            if (isLiquidGlassMode && combinedBackdrop != null) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(interactiveHighlight?.gestureModifier ?: Modifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = lensHeight10Px * (0.6f + 0.4f * progress),
                                    refractionAmount = lensAmount14Px * (0.6f + 0.4f * progress),
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = { pillHighlight.value.copy(alpha = dampedDragAnimation.pressProgress) },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                // 速度形变必须在 Draw 阶段读取 velocity，否则拖拽期间每帧都会触发重组
                                val (stretchX, compressY) =
                                    LiquidPhysicsMath.calculateVelocityStretch(dampedDragAnimation.velocity)
                                scaleX *= stretchX
                                scaleY *= compressY
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        // 内阴影：按下时半径与浓度随压力增长，制造玻璃被压出的凹陷
                        .innerShadow(shape = pillShape) {
                            val progress = dampedDragAnimation.pressProgress
                            radius = 8.dp.toPx() * (0.5f + 0.5f * progress)
                            color = Color.Black
                            alpha = 0.15f * (0.5f + 0.5f * progress)
                        }
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            } else {
                // 降级档位滑块：无折射可用，改由滑块自己承载"选中配色内容"的平移副本，
                // 保证高亮文字始终跟着胶囊走，而不是只在原位变色。
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                        }
                        .then(dampedDragAnimation.modifier)
                        .clip(pillShape)
                        .background(colors.indicatorColor.copy(alpha = 0.15f), pillShape)
                        .height(56.dp)
                        .width(tabWidthDp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 降级档位没有离屏高亮层，缩放只能加在这里，
                    // 否则毛玻璃档位按下滑块膨胀而图标文字纹丝不动，丢掉按压反馈。
                    CompositionLocalProvider(
                        LocalFloatingBottomBarContentColor provides colors.activeContentColor,
                        LocalFloatingBottomBarTabScale provides {
                            lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .clearAndSetSemantics {}
                                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).coerceAtLeast(0f).toDp() })
                                .height(56.dp)
                                .graphicsLayer {
                                    val progressOffset = dampedDragAnimation.value * tabWidthPx
                                    translationX = if (isLtr) -progressOffset else progressOffset
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            content = content,
                        )
                    }
                }
            }
        }
    }
}
