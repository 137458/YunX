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

package com.yunx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SnackbarControllerTest {

    @Test
    fun show_emits_event_with_incremented_seq() {
        SnackbarController.show("消息1")
        val event1 = SnackbarController.events.value
        assertNotNull(event1)
        assertEquals("消息1", event1?.message)

        SnackbarController.show("消息2")
        val event2 = SnackbarController.events.value
        assertNotNull(event2)
        assertEquals("消息2", event2?.message)
        assertEquals(event1!!.seq + 1, event2!!.seq)
    }

    @Test
    fun consume_clears_event_only_if_seq_matches() {
        SnackbarController.show("待消费消息")
        val event = SnackbarController.events.value
        assertNotNull(event)
        val seq = event!!.seq

        // 用错误的 seq 消费，不应该清空
        SnackbarController.consume(seq - 1)
        assertEquals(event, SnackbarController.events.value)

        // 用正确的 seq 消费，应该清空
        SnackbarController.consume(seq)
        assertNull(SnackbarController.events.value)
    }

    @Test
    fun consume_does_not_clear_newly_arrived_event() {
        SnackbarController.show("第一条消息")
        val firstSeq = SnackbarController.events.value!!.seq

        // 新消息到达
        SnackbarController.show("第二条新消息")
        val secondSeq = SnackbarController.events.value!!.seq

        // 尝试消费第一条消息的 seq，不应覆盖第二条新消息
        SnackbarController.consume(firstSeq)
        assertNotNull(SnackbarController.events.value)
        assertEquals(secondSeq, SnackbarController.events.value!!.seq)
        assertEquals("第二条新消息", SnackbarController.events.value!!.message)

        // 正常消费第二条消息
        SnackbarController.consume(secondSeq)
        assertNull(SnackbarController.events.value)
    }
}
