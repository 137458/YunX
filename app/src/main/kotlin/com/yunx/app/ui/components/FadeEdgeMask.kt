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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 羽化遮罩的贴边位置。 */
enum class FadeEdge {
    /** 贴顶：实色在顶部，向下渐隐。 */
    Top,

    /** 贴底：实色在底部，向上渐隐。 */
    Bottom
}

/**
 * 列表与悬浮 UI 交界处的羽化遮罩。
 *
 * 直接用「实色 → 全透明」两段渐变，中段会出现一条可感知的灰带（线性插值在深色底上尤其明显）。
 * 这里用三段式停靠点：0f 实色、0.75f 保留 90% 不透明度、1f 全透明，
 * 让不透明区间更长、收尾更陡，视觉上更接近系统原生的淡出边缘。
 *
 * 颜色默认取 background：遮罩的作用是让内容"融进"页面底色，
 * 若页面底色不是 background，调用方应显式传入对应颜色。
 */
@Composable
fun BoxScope.FadeEdgeMask(
    edge: FadeEdge,
    height: Dp = 40.dp,
    color: Color = MiuixTheme.colorScheme.background,
    modifier: Modifier = Modifier
) {
    val solid = color
    val almostSolid = color.copy(alpha = color.alpha * 0.9f)
    val transparent = color.copy(alpha = 0f)

    // Top 从上到下渐隐，Bottom 反向；用显式停靠点而不是两端插值
    val colors = when (edge) {
        FadeEdge.Top -> arrayOf(0f to solid, 0.75f to almostSolid, 1f to transparent)
        FadeEdge.Bottom -> arrayOf(0f to transparent, 0.25f to almostSolid, 1f to solid)
    }

    Box(
        modifier = modifier
            .align(if (edge == FadeEdge.Top) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colorStops = colors))
    )
}
