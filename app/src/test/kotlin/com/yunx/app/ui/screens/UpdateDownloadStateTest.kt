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

import com.yunx.app.data.db.DownloadTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDownloadStateTest {

    private fun state(status: Int, downloaded: Long = 0L, total: Long = 0L) =
        UpdateDownloadState(status, downloaded, total, speed = 0L, savePath = "")

    @Test
    fun downloadingAndPendingCountAsActive() {
        assertTrue(state(DownloadTaskEntity.STATUS_DOWNLOADING, 50, 100).isActive)
        assertTrue(state(DownloadTaskEntity.STATUS_PENDING).isActive)
        assertFalse(state(DownloadTaskEntity.STATUS_COMPLETED, 100, 100).isActive)
        assertFalse(state(DownloadTaskEntity.STATUS_FAILED, 10, 100).isActive)
    }

    @Test
    fun pausedIsItsOwnStateNotActiveOrFinished() {
        // 暂停曾落进「其余」分支，弹窗显示「正在下载」却停在固定百分比且给不出继续入口。
        // 这里钉住暂停与下载中/已完成互斥，防止再次退化。
        val paused = state(DownloadTaskEntity.STATUS_PAUSED, 50, 100)
        assertTrue(paused.isPaused)
        assertFalse(paused.isActive)
        assertFalse(paused.isFinished)
        assertFalse(paused.isFailed)
    }

    @Test
    fun terminalStatesAreMutuallyExclusive() {
        assertTrue(state(DownloadTaskEntity.STATUS_COMPLETED, 100, 100).isFinished)
        assertTrue(state(DownloadTaskEntity.STATUS_FAILED, 10, 100).isFailed)
        assertFalse(state(DownloadTaskEntity.STATUS_COMPLETED, 100, 100).isFailed)
        assertFalse(state(DownloadTaskEntity.STATUS_FAILED, 10, 100).isFinished)
    }

    @Test
    fun fractionIsZeroWhenTotalSizeUnknown() {
        // 服务器未给 Content-Length：不能伪造百分比，进度条由调用方隐藏
        val unknown = state(DownloadTaskEntity.STATUS_DOWNLOADING, 4096, 0)
        assertEquals(0f, unknown.fraction, 0.0001f)
        assertTrue(unknown.isActive)
    }

    @Test
    fun fractionIsClampedWhenServerOverreports() {
        val overshoot = state(DownloadTaskEntity.STATUS_DOWNLOADING, 200, 100)
        assertEquals(1f, overshoot.fraction, 0.0001f)
    }
}
