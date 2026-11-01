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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * [DriveOrder] 的规则测试：这些规则原本内联在 [DriveScreen] 的 Composable 里无法单测，
 * 而它们决定了「排序能不能持久生效」「隐藏开关会不会把项弄丢」，
 * 一旦改坏，用户看到的是「排序白排了」「网盘怎么消失了」，不是崩溃，很难被发现。
 */
class DriveOrderTest {

    private data class Item(val id: String, val loggedIn: Boolean)

    private val a = Item("a", loggedIn = true)
    private val b = Item("b", loggedIn = false)
    private val c = Item("c", loggedIn = false)
    private val d = Item("d", loggedIn = true)

    private fun List<Item>.idsOf() = map { it.id }

    @Test
    fun rankUsesPositionInSavedOrder() {
        assertEquals(mapOf("c" to 0, "a" to 1, "d" to 2), DriveOrder.rank(listOf("c", "a", "d")))
        assertEquals(emptyMap<String, Int>(), DriveOrder.rank(emptyList()))
    }

    @Test
    fun rankKeepsLastOccurrenceOfDuplicatedId() {
        // 与搬运前的 associate 语义一致：重复 id 取最后一次出现的位置。
        // 设置被写坏时不能悄悄改变名次含义，否则顺序会莫名跳变。
        assertEquals(mapOf("b" to 1, "a" to 2), DriveOrder.rank(listOf("a", "b", "a")))
    }

    @Test
    fun sortPutsSavedItemsFirstAndUnknownItemsAtTailStably() {
        val items = listOf(a, b, c, d)
        val rank = DriveOrder.rank(listOf("c", "a"))

        assertEquals(listOf("c", "a", "b", "d"), DriveOrder.sort(items, rank, Item::id).idsOf())

        // 第二组差异化数据：b 排到最前，其余未命名次项保持默认顺序（d、a 的相对顺序不能变）
        val rank2 = DriveOrder.rank(listOf("b"))
        assertEquals(listOf("b", "a", "c", "d"), DriveOrder.sort(items, rank2, Item::id).idsOf())
    }

    @Test
    fun sortWithEmptyOrStaleOrderKeepsOriginalSequence() {
        val items = listOf(a, b, c, d)

        assertEquals(listOf("a", "b", "c", "d"), DriveOrder.sort(items, DriveOrder.rank(emptyList()), Item::id).idsOf())

        // 保存的 id 全是已删除的旧账号：不能把它们当成「有名次」，也不能打乱现存项
        val stale = DriveOrder.rank(listOf("zzz", "yyy"))
        assertEquals(listOf("a", "b", "c", "d"), DriveOrder.sort(items, stale, Item::id).idsOf())
    }

    @Test
    fun sortOfEmptyListStaysEmpty() {
        assertEquals(emptyList<String>(), DriveOrder.sort(emptyList(), DriveOrder.rank(listOf("a")), Item::id).idsOf())
    }

    @Test
    fun visibleFiltersUnloggedItemsOnlyWhenEnabled() {
        val items = listOf(a, b, c, d)

        assertEquals(
            listOf("a", "d"),
            DriveOrder.visible(items, hideUnlogged = true, isLoggedIn = Item::loggedIn).idsOf()
        )
        assertEquals(
            listOf("a", "b", "c", "d"),
            DriveOrder.visible(items, hideUnlogged = false, isLoggedIn = Item::loggedIn).idsOf()
        )
    }

    @Test
    fun visibleKeepsSequenceWhenNothingIsHidden() {
        val allLoggedIn = listOf(a, d)

        assertEquals(
            listOf("a", "d"),
            DriveOrder.visible(allLoggedIn, hideUnlogged = true, isLoggedIn = Item::loggedIn).idsOf()
        )
        assertEquals(
            listOf("a", "d"),
            DriveOrder.visible(allLoggedIn, hideUnlogged = false, isLoggedIn = Item::loggedIn).idsOf()
        )
    }

    @Test
    fun hiddenCountTracksTheSwitch() {
        val items = listOf(a, b, c, d)

        assertEquals(2, DriveOrder.hiddenCount(items, hideUnlogged = true, isLoggedIn = Item::loggedIn))
        assertEquals(0, DriveOrder.hiddenCount(items, hideUnlogged = false, isLoggedIn = Item::loggedIn))
        // 全部已登录时计数为 0，不能因为开关开着就报「已隐藏 0 个」以外的值
        assertEquals(0, DriveOrder.hiddenCount(listOf(a, d), hideUnlogged = true, isLoggedIn = Item::loggedIn))
    }

    @Test
    fun moveReordersLikeRemoveThenInsert() {
        val items = listOf("a", "b", "c")

        // 与搬运前「removeAt(from) → add(to, moved)」完全等价
        assertEquals(listOf("b", "c", "a"), DriveOrder.move(items, from = 0, to = 2))
        assertEquals(listOf("c", "a", "b"), DriveOrder.move(items, from = 2, to = 0))
        assertEquals(listOf("a", "c", "b"), DriveOrder.move(items, from = 1, to = 2))
    }

    @Test
    fun moveIgnoresOutOfRangeAndNoOp() {
        val items = listOf("a", "b", "c")

        assertSame(items, DriveOrder.move(items, from = -1, to = 1))
        assertSame(items, DriveOrder.move(items, from = 0, to = -1))
        assertSame(items, DriveOrder.move(items, from = 1, to = 9))
        assertSame(items, DriveOrder.move(items, from = 9, to = 0))
        assertSame(items, DriveOrder.move(items, from = 1, to = 1))
    }
}
