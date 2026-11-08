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
import com.yunx.app.data.download.DownloadStats
import org.junit.Assert.assertEquals
import org.junit.Test

/** 任务行副标题的推导规则：两种密度共用一份文案，不在界面里各拼一套 */
class DownloadTaskRowTextTest {

    private val mb = 1_048_576L
    private val total = 100L * mb

    private fun task(
        status: Int = DownloadTaskEntity.STATUS_DOWNLOADING,
        downloaded: Long = 50L * mb,
        totalSize: Long = total,
        avgSpeed: Long = 0L,
    ) = DownloadTaskEntity(
        url = "https://example.com/file.bin",
        fileName = "file.bin",
        totalSize = totalSize,
        downloadedSize = downloaded,
        status = status,
        avgSpeed = avgSpeed,
    )

    @Test
    fun downloadingShowsStatusSpeedRemainingAndThreads() {
        val stats = DownloadStats(speed = 2L * mb, remainMillis = 30_000L, chunkCount = 8)
        assertEquals(
            "下载中 · 2.0 MB/s · 剩余 30秒 · 8 线程",
            taskRowSubtitle(task(), stats),
        )
    }

    @Test
    fun unknownRemainingAndThreadCountAreOmitted() {
        val stats = DownloadStats(speed = 2L * mb, remainMillis = -1L, chunkCount = 0)
        assertEquals("下载中 · 2.0 MB/s", taskRowSubtitle(task(), stats))
    }

    @Test
    fun stalledDownloadFallsBackToStatusAndProgress() {
        assertEquals(
            "下载中 · 50.0 MB / 100.0 MB",
            taskRowSubtitle(task(), DownloadStats(speed = 0L)),
        )
    }

    @Test
    fun completedUsesAverageSpeedOrPlainSize() {
        assertEquals(
            "已完成 · 平均 2.0 MB/s",
            taskRowSubtitle(
                task(status = DownloadTaskEntity.STATUS_COMPLETED, downloaded = total, avgSpeed = 2L * mb),
                stats = null,
            ),
        )
        assertEquals(
            "已完成 · 100.0 MB / 100.0 MB",
            taskRowSubtitle(task(status = DownloadTaskEntity.STATUS_COMPLETED, downloaded = total), stats = null),
        )
    }

    @Test
    fun failedWithoutSizeStaysOnStatusOnly() {
        assertEquals(
            "失败",
            taskRowSubtitle(
                task(status = DownloadTaskEntity.STATUS_FAILED, downloaded = 0L, totalSize = 0L),
                stats = null,
            ),
        )
    }
}
