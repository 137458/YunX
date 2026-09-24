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

package com.yunx.app.ui.util

/**
 * 自适应布局的纯计算部分（无 Compose 依赖，可直接单测）。
 */
object AdaptiveLayout {

    /**
     * 计算网格列数。
     *
     * 传入的必须是**网格自身可用的宽度**，不是窗口宽度：
     * 页面左右内边距、侧边导航栏、分栏详情面板都会吃掉宽度，
     * 用窗口宽度算会让列宽小于预期、在大屏分栏时尤其明显。
     *
     * 为什么不直接对 GridCells.Adaptive(minColumnWidth) 反推最小列宽：
     * Adaptive 在"能放 n 列"时按 (宽度 - 间距) / n 分配，列宽会随宽度连续变化，
     * 而这里的 configuredColumns 是用户设置的上限，必须被尊重。
     * 反推最小列宽既拿不到这个上限，也无法在超宽屏上把列宽钉在 [minColumnWidthDp, 2 * minColumnWidthDp) 区间内。
     *
     * @param availableWidthDp 网格可用宽度；<= 0（尚未测量）时按 1 列处理
     * @param configuredColumns 用户设置的最大列数；<= 0 时回退为 1
     * @param minColumnWidthDp 单列最小宽度；<= 0 时回退为 1，避免除零
     */
    fun gridColumns(
        availableWidthDp: Int,
        configuredColumns: Int,
        minColumnWidthDp: Int = 180
    ): Int {
        val columns = if (configuredColumns <= 0) 1 else configuredColumns
        if (availableWidthDp <= 0) return 1
        val minWidth = if (minColumnWidthDp <= 0) 1 else minColumnWidthDp
        // 向下取整：放不下 n 列时宁可少一列，也不让列宽低于下限
        val fit = availableWidthDp / minWidth
        return fit.coerceIn(1, columns)
    }

    /**
     * 计算阅读区宽度上限。
     *
     * 长文本每行超过约 40~50 个汉字后，换行回扫的成本会明显上升；大屏上把正文限宽居中
     * 比铺满整屏更易读。返回 null 表示无需限宽（紧凑宽度直接用可用宽度）。
     *
     * - 紧凑档（<= compactMaxDp）：返回 null，铺满；
     * - 中间档（< wideMinDp）：返回可用宽度，不做限宽；
     * - 宽屏档：钉在 wideMaxDp。
     *
     * @param contentWidthDp 内容可用宽度；<= 0（尚未测量）时返回 null
     */
    fun readableWidth(
        contentWidthDp: Int,
        wideMinDp: Int = 600,
        compactMaxDp: Int = 760,
        wideMaxDp: Int = 900
    ): Int? {
        if (contentWidthDp <= 0) return null
        if (contentWidthDp <= compactMaxDp) return null
        if (contentWidthDp < wideMinDp) return contentWidthDp
        // 上限被配置成非正数时不可能得到可用宽度：返回 null（调用方按不限宽处理），
        // 而不是把 0 当成"宽度上限"传出去，那样会布局出一个零宽正文。
        val capped = wideMaxDp.coerceAtMost(contentWidthDp)
        return if (capped <= 0) null else capped
    }
}
