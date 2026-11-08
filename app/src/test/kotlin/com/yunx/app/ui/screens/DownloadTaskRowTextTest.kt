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

import com.yunx.app.R
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.util.AppStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** 任务行副标题的推导规则：两种密度共用一份文案，不在界面里各拼一套 */
class DownloadTaskRowTextTest {

    private val mb = 1_048_576L
    private val total = 100L * mb

    /**
     * 文案已走资源表，JVM 单测没有 Context：注入一张与模板一致的替身表，
     * 断言仍然逐字校验状态、速度、剩余、线程的拼接结果。
     */
    @Before
    fun installStringTable() {
        val table = mapOf(
            R.string.ed_status_pending to "等待中",
            R.string.ed_status_downloading to "下载中",
            R.string.ed_status_paused to "已暂停",
            R.string.ed_status_completed to "已完成",
            R.string.ed_status_failed to "失败",
            R.string.ed_status_unknown to "未知",
        )
        AppStrings.installResolverForTest { resId, args ->
            when (resId) {
                R.string.dl_remaining -> "剩余 ${args[0]}"
                R.string.dl_threads -> "${args[0]} 线程"
                R.string.dl_dur_seconds -> "${args[0]}秒"
                else -> table[resId] ?: ""
            }
        }
    }

    @After
    fun resetStringTable() {
        AppStrings.installResolverForTest(null)
    }

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
    fun stalledDownloadKeepsOnlyStatusBecauseProgressLineCarriesSizes() {
        // 大小与百分比由底部 progressText 统一展示，副标题再拼一遍就是重复
        assertEquals("下载中", taskRowSubtitle(task(), DownloadStats(speed = 0L)))
    }

    @Test
    fun completedKeepsOnlyStatusBecauseBottomLineCarriesAverageAndSize() {
        assertEquals(
            "已完成",
            taskRowSubtitle(
                task(status = DownloadTaskEntity.STATUS_COMPLETED, downloaded = total, avgSpeed = 2L * mb),
                stats = null,
            ),
        )
        assertEquals(
            "已完成",
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
