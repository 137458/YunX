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

package com.yunx.app.ui.theme

/** 宽窗口断点：容器可用宽度达到该值即视为大屏。 */
internal const val WIDE_SCREEN_MIN_WIDTH_DP = 600

/**
 * 悬浮底栏的宽度上限：胶囊按内容自适应，正常情况下够不到该值，只作为超长文案 / 大字号下的兜底。
 * 不设下限——铺满屏宽的观感偏「横向拉长」，窄屏同样按内容收紧。
 */
internal const val FLOATING_BAR_MAX_WIDTH_DP = 540

/**
 * 悬浮底栏每个 Tab 槽位的最小宽度（内容自适应时的实际下限）。
 *
 * 槽位宽度 = max(该值, 图标 + 文案实测宽)，所以文案变长或大字号时胶囊会自然撑宽，不会压字；
 * 该值只决定胶囊在窄屏上的收紧程度：4 个 Tab 时栏宽 ≈ 4 × 该值 + 8dp 内边距。
 */
internal const val FLOATING_BAR_ITEM_MIN_WIDTH_DP = 56

/**
 * 主界面列表为底部导航预留的内容避让（不含系统栏 inset，底栏外壳自己处理 inset）。
 *
 * 取固定值而不是实测栏高：避让只要「够大不遮住末项」即可，实测值会让布局多一轮
 * 测量-回传-重组，首帧还会闪一下。二级页面没有底栏，回落到 [com.yunx.app.ui.components.LocalScaffoldPadding] 的默认值。
 */
internal const val BOTTOM_BAR_CONTENT_PADDING_DP = 100

/**
 * 是否按宽窗口布局。
 *
 * 只看宽度、不看高度与横竖屏：旧判据里「紧凑高度的横向窗口也用侧栏」那一条会让矮横屏手机
 * 同时长出侧边栏与底栏，纵向空间被双重挤占。手机横屏仍然用底栏，正文宽度并没有宽到需要侧栏。
 */
internal fun isWideWindow(widthDp: Int): Boolean = widthDp >= WIDE_SCREEN_MIN_WIDTH_DP

/**
 * 是否显示侧边导航栏（NavigationRail）。
 *
 * 与 [shouldShowBottomBar] 严格互补，任何输入下二者有且仅有一个为真：
 * 只有「宽窗口 + 用户选了贴底标准栏」时侧边栏才取代底栏；
 * 宽窗口下用户选了悬浮形态则尊重该选择，侧边栏让位。
 */
internal fun shouldShowNavigationRail(isWideWindow: Boolean, useFloatingBottomBar: Boolean): Boolean =
    isWideWindow && !useFloatingBottomBar

/** 是否显示底部导航栏，判据见 [shouldShowNavigationRail]。 */
internal fun shouldShowBottomBar(isWideWindow: Boolean, useFloatingBottomBar: Boolean): Boolean =
    !isWideWindow || useFloatingBottomBar
