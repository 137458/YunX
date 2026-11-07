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

package com.yunx.app.ui.viewmodel

import com.yunx.app.data.network.model.ShareFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CloudMultiSelect] 的规则测试。这套规则原先在 10 个网盘 ViewModel 里逐份复制，
 * 改坏任何一份都会表现为「长按选了却批量删不掉」或「全选点了没反应」这类难察觉的问题。
 */
class CloudMultiSelectTest {

    private fun file(name: String) =
        ShareFile(
            fid = name,
            fname = name,
            fsize = 0L,
            isdir = false,
            pdirFid = "",
            fidToken = "",
        )

    private val a = file("a")
    private val b = file("b")
    private val c = file("c")

    @Test
    fun enter_activatesAndKeepsOnlyThePressedFile() {
        val state = CloudMultiSelect()
        state.enter(a)
        state.toggle(b)

        state.enter(c)

        assertTrue(state.isActive)
        assertEquals(listOf("c"), state.selected.map { it.fname })
    }

    @Test
    fun toggle_addsThenRemovesTheSameFile() {
        val state = CloudMultiSelect()
        state.enter(a)

        state.toggle(b)
        assertEquals(listOf("a", "b"), state.selected.map { it.fname })

        state.toggle(a)
        assertEquals(listOf("b"), state.selected.map { it.fname })
    }

    @Test
    fun toggle_returnsToOriginalSelectionWhenToggledTwice() {
        val state = CloudMultiSelect()
        state.enter(a)

        state.toggle(b)
        state.toggle(b)

        assertEquals(listOf("a"), state.selected.map { it.fname })
    }

    @Test
    fun toggleAll_selectsEverythingWhenNotFullySelected() {
        val state = CloudMultiSelect()
        state.enter(a)

        state.toggleAll(listOf(a, b, c))

        assertEquals(listOf("a", "b", "c"), state.selected.map { it.fname })
    }

    @Test
    fun toggleAll_clearsWhenSelectionCountMatches() {
        val state = CloudMultiSelect()
        state.enter(a)
        state.toggleAll(listOf(a, b, c))

        state.toggleAll(listOf(a, b, c))

        assertEquals(emptyList<String>(), state.selected.map { it.fname })
    }

    @Test
    fun toggleAll_onEmptyListClearsSelection() {
        val state = CloudMultiSelect()
        state.enter(a)

        state.toggleAll(emptyList())

        assertEquals(0, state.selected.size)
    }

    @Test
    fun exit_deactivatesAndDropsSelection() {
        val state = CloudMultiSelect()
        state.enter(a)
        state.toggle(b)

        state.exit()

        assertFalse(state.isActive)
        assertEquals(0, state.selected.size)
    }
}
