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

import com.yunx.app.data.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDialogPlanTest {

    private fun release(
        tagName: String = "v1.3.0",
        body: String = "## 更新内容\n- 支持更多网盘\n- 修复若干问题",
        publishedAt: String = "2026-11-01T08:00:00Z",
        withApk: Boolean = true,
    ) = UpdateChecker.Release(
        tagName = tagName,
        body = body,
        assets = if (withApk) {
            listOf(
                UpdateChecker.Asset(
                    name = "yunx.apk",
                    downloadUrl = "https://example.com/yunx.apk",
                    size = 104_857_600L,
                    digest = "sha256:abc",
                ),
            )
        } else {
            emptyList()
        },
        publishedAt = publishedAt,
    )

    @Test
    fun summaryIsOneLineVersionJumpWithPublishDate() {
        assertEquals(
            "当前 1.2.3 → 最新 1.3.0 · 2026-11-01",
            updateDialogSummary("1.2.3", release()),
        )
    }

    @Test
    fun summaryStripsLeadingVAndSkipsBlankDate() {
        assertEquals(
            "当前 1.2.3 → 最新 1.3.0",
            updateDialogSummary("v1.2.3", release(publishedAt = "")),
        )
        assertEquals(
            "当前 1.2.3 → 最新 1.3.0 · 2026-11-01",
            updateDialogSummary("1.2.3", release(tagName = "1.3.0")),
        )
    }

    @Test
    fun notesCollapseAtTwelveLinesAndExpandRestoresFullText() {
        val body = (1..20).joinToString("\n") { "- 第 $it 行" }
        val folded = updateDialogNotesState(body, expanded = false, previewLines = UPDATE_NOTES_PREVIEW_LINES)
        assertEquals(12, folded.visibleMarkdown.lines().size)
        assertEquals(8, folded.hiddenLineCount)

        val expanded = updateDialogNotesState(body, expanded = true, previewLines = UPDATE_NOTES_PREVIEW_LINES)
        assertEquals(20, expanded.visibleMarkdown.lines().size)
        assertEquals(0, expanded.hiddenLineCount)
    }

    @Test
    fun shortOrBlankNotesNeverOfferExpand() {
        assertEquals(0, updateDialogNotesState("- 一行说明", expanded = false, previewLines = 12).hiddenLineCount)
        assertEquals(0, updateDialogNotesState("   ", expanded = false, previewLines = 12).hiddenLineCount)
        assertEquals("", updateDialogNotesState("   ", expanded = false, previewLines = 12).visibleMarkdown)
    }

    @Test
    fun idlePlanKeepsDownloadPrimaryAndIgnoreAsTextLink() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(),
            downloadState = UpdateDialogState.Idle,
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals(UpdatePrimaryAction.DOWNLOAD, plan.primaryAction)
        assertEquals("立即下载", plan.primaryText)
        assertTrue(plan.primaryEnabled)
        assertEquals("稍后", plan.dismissText)
        assertFalse(plan.showProgress)
        assertFalse(plan.showCancelDownload)
        assertFalse(plan.showOpenDownloadPage)
        assertTrue(plan.showIgnore)
        assertTrue(plan.showMirror)
        assertTrue(plan.hasNotes)
    }

    @Test
    fun downloadingPlanDisablesPrimaryShowsPercentAndEngineActions() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(),
            downloadState = UpdateDialogState.Downloading(
                downloaded = 50_331_648L,
                total = 104_857_600L,
                speed = 2_097_152L,
            ),
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals(UpdatePrimaryAction.DOWNLOAD, plan.primaryAction)
        assertEquals("下载中 48%", plan.primaryText)
        assertFalse(plan.primaryEnabled)
        assertEquals("后台下载", plan.dismissText)
        assertTrue(plan.showProgress)
        assertEquals(0.48f, plan.progressFraction!!, 0.0001f)
        assertEquals("48.0 MB / 100.0 MB · 2.0 MB/s", plan.progressDetail)
        assertTrue(plan.showCancelDownload)
        assertTrue(plan.showOpenDownloadPage)
        assertFalse(plan.showIgnore)
        assertFalse(plan.showMirror)
    }

    @Test
    fun downloadingWithoutTotalFallsBackToIndeterminateProgress() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(),
            downloadState = UpdateDialogState.Downloading(downloaded = 1_048_576L, total = 0L, speed = 0L),
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals("下载中", plan.primaryText)
        assertNull(plan.progressFraction)
        assertEquals("1.0 MB · 大小未知", plan.progressDetail)
    }

    @Test
    fun donePlanSwitchesPrimaryToInstallAndKeepsDownloadTaskEntry() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(),
            downloadState = UpdateDialogState.Done("/sdcard/Download/yunx.apk"),
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals(UpdatePrimaryAction.INSTALL, plan.primaryAction)
        assertEquals("安装更新", plan.primaryText)
        assertTrue(plan.primaryEnabled)
        assertEquals("稍后", plan.dismissText)
        assertFalse(plan.showProgress)
        assertFalse(plan.showCancelDownload)
        assertTrue(plan.showOpenDownloadPage)
        assertFalse(plan.showIgnore)
        assertFalse(plan.showMirror)
    }

    @Test
    fun failedPlanOffersRetryAndMirrorFallback() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(),
            downloadState = UpdateDialogState.Failed("网络中断"),
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals(UpdatePrimaryAction.DOWNLOAD, plan.primaryAction)
        assertEquals("重试", plan.primaryText)
        assertTrue(plan.primaryEnabled)
        assertEquals("稍后", plan.dismissText)
        assertFalse(plan.showProgress)
        assertFalse(plan.showCancelDownload)
        assertTrue(plan.showOpenDownloadPage)
        assertTrue(plan.showMirror)
        assertFalse(plan.showIgnore)
    }

    @Test
    fun releaseWithoutApkOpensReleasePageInsteadOfDownloading() {
        val plan = buildUpdateDialogPlan(
            currentVersion = "1.2.3",
            release = release(withApk = false, body = ""),
            downloadState = UpdateDialogState.Idle,
            canIgnore = true,
            canCancelDownload = true,
            canMirror = true,
        )
        assertEquals(UpdatePrimaryAction.OPEN_RELEASE, plan.primaryAction)
        assertEquals("前往 Releases 页面", plan.primaryText)
        assertFalse(plan.hasNotes)
    }
}
