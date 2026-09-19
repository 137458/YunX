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

package com.yunx.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 带有底色保护与平台着色器能力检测的 Backdrop 采样源。
 *
 * 1. 运行时熔断：若硬件/平台不支持 RuntimeShader (Android < API 33) 或主动禁用模糊，返回 null；
 * 2. 底色注入：在 onDraw 最底层绘制 surfaceColor，确保采样源在透明区域有不透明背景支撑，杜绝高斯模糊边缘采样透明导致的暗黑伪影与噪点。
 *
 * @param enableBlur 是否启用模糊采样
 */
@Composable
fun rememberBlurBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/**
 * 安全挂载 layerBackdrop 采样源的扩展 Modifier。
 * 当 backdrop 为非 null 时注册为 Backdrop 图层采样源，为 null 时保持原样。
 */
fun Modifier.blurBackdropSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this

/**
 * Xiaomi HyperOS / MIUIX 规范沉浸式顶部渐变毛玻璃包装器。
 *
 * 采用组合包装机制包裹官方原生 TopAppBar 或 SmallTopAppBar：
 * 1. 动态尺寸自适应：使用 matchParentSize() 自动适应顶栏展开（大标题）与折叠（小标题）时的实时高度；
 * 2. 动态滚动涌现：初始停靠在页面顶部时透明度为 0，随着页面滚动在 48dp 阈值内平滑淡入毛玻璃（在 graphicsLayer 内部消费 Density 与避免重组）；
 * 3. 渐进式遮罩衰减：利用离屏渲染与 DstIn 垂直渐变，顶部状态栏 100% 满额模糊，沿 Y 轴向下平滑衰减，底部边缘无任何硬切断；
 * 4. 优雅降级：在不支持模糊的环境或 backdrop 为 null 时，自动降级为普通顶栏。
 *
 * @param backdrop 跨图层采样的 Backdrop，若为 null 则自动降级不显示毛玻璃
 * @param modifier 外部修饰符
 * @param blurEnabled 是否启用毛玻璃效果
 * @param scrollBehavior 顶栏滚动行为控制器，用于根据 contentOffset 动态驱动毛玻璃平滑淡入
 * @param content 顶栏主体内容（通常为设置了透明背景色的 TopAppBar 或 SmallTopAppBar）
 */
@Composable
fun BlurredBar(
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = true,
    scrollBehavior: ScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        if (backdrop != null && blurEnabled) {
            val surfaceColor = MiuixTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = scrollBehavior?.state
                            ?.let { (-it.contentOffset / 48.dp.toPx()).coerceIn(0f, 1f) }
                            ?: 1f
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        // 顶部 100% 模糊，向下平滑衰减，消除硬边缘
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Black,
                                0.7f to Color.Black.copy(alpha = 0.85f),
                                1.0f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 15f,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = surfaceColor.copy(alpha = 0.4f)),
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}
