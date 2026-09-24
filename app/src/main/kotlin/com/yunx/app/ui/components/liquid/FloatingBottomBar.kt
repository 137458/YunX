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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
import top.yukonga.miuix.kmp.blur.noiseDither
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

/**
 * 深色下大面积模糊的抗色带噪点系数。
 * 量级参考 miuix BlurDefaults.noiseCoefficient（0.0045），只用于打散暗部色带，不引入可见颗粒。
 */
private const val DARK_NOISE_COEFFICIENT = 0.006f

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
                // 原地轻点当前 Tab 也算一次选中（用于"回到顶部"这类页面内行为）
                val isTapOnSameTab = dropIndex == currentSelectedIndex() && totalDragPx < touchSlopPx
                val isSwitched = dropIndex != currentSelectedIndex()

                animateToValue(dropIndex.toFloat())
                holder.offsetJob?.cancel()
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
                    holder.offsetJob?.cancel()
                    holder.offsetJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
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
                    // 外壳投影保留但减弱：它是 Blur / None 降级档位唯一的悬浮感来源，
                    // 液态玻璃档位下"按压浮起"的层次改由滑块自身的投影承担，避免两层阴影叠加发脏。
                    .dropShadow(
                        shape = pillShape,
                        shadow = Shadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isDark) 0.14f else 0.07f,
                        ),
                    )
                    // 吞掉底栏空白处的手势，避免穿透到下层列表。
                    // 不用 clickable(onClick = {})：空的 clickable 会生成一个无标签的 clickable 语义节点，
                    // TalkBack 会把它读成一个空按钮。pointerInput 不产生任何语义节点。
                    // Main 传递顺序是「子 → 父」，requireUnconsumed = true 保证 Tab 子项已消费的按下
                    // 不会走到这里，所以不会吞掉 Tab 的点击；同时只消费「按下/抬起」而不消费移动事件，
                    // 避免打断滑块自身的拖拽。
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            down.consume()
                            waitForUpOrCancellation()
                        }
                    }
                    .then(
                        if (isLiquidGlassMode && backdrop != null) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    padding = maxOf(padding, lensPaddingPx)
                                    vibrancy()
                                    blur(blur4Px, blur4Px)
                                    // 深色下大面积模糊会把暗部量化成色带，用极小系数噪点打散
                                    if (isDark) noiseDither(DARK_NOISE_COEFFICIENT)
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
                                    // 降级档位模糊半径更大，暗部色带更明显，同样需要噪点打散
                                    if (isDark) noiseDither(DARK_NOISE_COEFFICIENT)
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

        // ── 第 2 层：离屏高亮层（仅录制"选中配色 Tab 图标与文字"，供滑块二次采样） ──
        // 移除多余的 drawBackdrop，避免与滑块的 CombinedBackdrop 发生双重模糊和底色二次叠加致暗
        if (isLiquidGlassMode && combinedBackdrop != null && tabsBackdrop != null) {
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
                        // 参考实现把滑块阴影的 alpha 绑定到按压力度：静止时滑块贴合外壳，
                        // 按下才浮起投影。miuix 的 drawBackdrop 没有 shadow 参数，因此改用独立的
                        // dropShadow；它在 Draw 阶段读取 pressProgress，不会引起重组。
                        .dropShadow(shape = pillShape) {
                            val progress = dampedDragAnimation.pressProgress
                            radius = 10.dp.toPx()
                            color = Color.Black
                            alpha = 0.22f * progress
                            offset = Offset(0f, 3.dp.toPx())
                        }
                        .then(interactiveHighlight?.gestureModifier ?: Modifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                // 与外壳同一套光学语言：折射内容同样做饱和度增强，
                                // 否则滑块内的画面比外壳更灰，看起来像一层灰色遮罩
                                vibrancy()
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
                                // 静止面色只留极薄一层：再叠加内阴影会把折射压成不透明的灰面，
                                // 按压力度才继续加深，保证折射始终透出来
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (!isDark) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.05f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        // 内阴影：按压力度驱动，静止时滑块贴合外壳不显凹陷，按下才出现玻璃被压出的边缘
                        .innerShadow(shape = pillShape) {
                            val progress = dampedDragAnimation.pressProgress
                            radius = 8.dp.toPx() * progress
                            color = Color.Black
                            alpha = 0.15f * progress
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
                        // 降级档位没有透镜与内阴影，按压反馈全靠缩放与投影，
                        // 因此这里同样按 pressProgress 淡入滑块投影。
                        .dropShadow(shape = pillShape) {
                            val progress = dampedDragAnimation.pressProgress
                            radius = 10.dp.toPx()
                            color = Color.Black
                            alpha = 0.2f * progress
                            offset = Offset(0f, 3.dp.toPx())
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
