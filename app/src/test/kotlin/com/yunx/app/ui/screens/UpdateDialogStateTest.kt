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
import org.junit.Test

/**
 * 引擎任务状态 → 弹窗四态的规则。
 *
 * 重点钉住两件事：状态数量不随引擎扩张（暂停、排队都不能衍生出第五态），
 * 以及完成态必须带着落盘路径交给上层做完整性校验。
 */
class UpdateDialogStateTest {

    private fun map(
        status: Int?,
        downloaded: Long = 0L,
        total: Long = 0L,
        speed: Long = 0L,
        savePath: String = "",
        errorMsg: String = "",
    ) = updateDialogStateOf(status, downloaded, total, speed, savePath, errorMsg)

    @Test
    fun noTaskIsIdle() {
        assertEquals(UpdateDialogState.Idle, map(null))
    }

    @Test
    fun pendingAndRunningAreDownloadingWithProgress() {
        assertEquals(
            UpdateDialogState.Downloading(downloaded = 10L, total = 100L, speed = 2048L),
            map(DownloadTaskEntity.STATUS_PENDING, 10L, 100L, 2048L),
        )
        assertEquals(
            UpdateDialogState.Downloading(downloaded = 60L, total = 100L, speed = 4096L),
            map(DownloadTaskEntity.STATUS_DOWNLOADING, 60L, 100L, 4096L),
        )
    }

    @Test
    fun pausedCollapsesIntoDownloadingWithoutFifthState() {
        assertEquals(
            UpdateDialogState.Downloading(downloaded = 30L, total = 100L, speed = 0L),
            map(DownloadTaskEntity.STATUS_PAUSED, 30L, 100L, 0L),
        )
    }

    @Test
    fun completedCarriesSavePathForVerification() {
        assertEquals(
            UpdateDialogState.Done("/sdcard/Download/yunx.apk"),
            map(DownloadTaskEntity.STATUS_COMPLETED, savePath = "/sdcard/Download/yunx.apk"),
        )
    }

    @Test
    fun failedCarriesEngineMessageWithFallback() {
        assertEquals(
            UpdateDialogState.Failed("连接超时"),
            map(DownloadTaskEntity.STATUS_FAILED, errorMsg = "连接超时"),
        )
        assertEquals(
            UpdateDialogState.Failed(UPDATE_DOWNLOAD_FAILED_MESSAGE),
            map(DownloadTaskEntity.STATUS_FAILED, errorMsg = "   "),
        )
    }

    @Test
    fun unknownStatusIsIdle() {
        assertEquals(UpdateDialogState.Idle, map(status = 99))
    }

    @Test
    fun fractionClampsAndDegradesWhenTotalUnknown() {
        assertEquals(0.5f, updateDownloadFraction(downloaded = 50L, total = 100L), 0.0001f)
        assertEquals(1.0f, updateDownloadFraction(downloaded = 120L, total = 100L), 0.0001f)
        assertEquals(0f, updateDownloadFraction(downloaded = 10L, total = 0L), 0.0001f)
    }
}
