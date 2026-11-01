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

package com.yunx.app.ui.screens

/**
 * 网盘页「账号排序 / 隐藏未登录」的纯计算部分。
 *
 * 抽出来的原因（不是为好看）：这三段逻辑原先内联在 [DriveScreen] 的 Composable 里，
 * 夹在十几个 `collectAsState` 与 `remember` 中间，JVM 单测无法触达；
 * 而「保存名次为空时用默认顺序」「隐藏开关只能藏未登录项」「越界移动必须原样返回」
 * 都是会被后续改动轻易破坏的**规则**，必须由 [DriveOrderTest] 钉住。
 *
 * 保持既有行为不变（这次只是搬运，不是重写）：
 * - [rank] 与历史实现一致，重复 id 取**最后一次**出现的位置（`associate` 的覆盖语义）；
 * - [sort] 依赖 `sortedBy` 的稳定性，没保存名次的项收 Int.MAX_VALUE 后仍保持默认顺序；
 * - [move] 与原先的 `removeAt` + `add` 完全等价（含「先移除后插入」造成的下标位移）。
 */
internal object DriveOrder {

    /** 名次表：id 在保存顺序里的下标。未出现的 id 查不到（调用方按 Int.MAX_VALUE 处理）。 */
    fun rank(order: List<String>): Map<String, Int> =
        order.withIndex().associate { (index, id) -> id to index }

    /**
     * 按保存名次排序。没保存过名次的项（新增平台 / 从没排过）排在末尾，
     * 且保持它们在 [items] 里的原有前后关系。
     */
    fun <T> sort(items: List<T>, rank: Map<String, Int>, id: (T) -> String): List<T> =
        items.sortedBy { rank[id(it)] ?: Int.MAX_VALUE }

    /** 隐藏开关只影响「未登录」的项：打开时过滤未登录项，关闭时原样返回（顺序不变）。 */
    fun <T> visible(items: List<T>, hideUnlogged: Boolean, isLoggedIn: (T) -> Boolean): List<T> =
        if (hideUnlogged) items.filter(isLoggedIn) else items

    /** 尾部提示里的「已隐藏 N 个」计数；开关关闭时恒为 0。 */
    fun <T> hiddenCount(items: List<T>, hideUnlogged: Boolean, isLoggedIn: (T) -> Boolean): Int =
        if (hideUnlogged) items.count { !isLoggedIn(it) } else 0

    /** 上移 / 下移一项：越界或原地不动时原样返回，绝不抛异常（弹窗里的箭头可能被连点）。 */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}
