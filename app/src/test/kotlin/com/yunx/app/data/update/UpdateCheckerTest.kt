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

package com.yunx.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun release(assets: List<UpdateChecker.Asset>) = UpdateChecker.Release(
        tagName = "v1.2.4",
        body = "## 更新内容\n- 修复下载",
        assets = assets,
        publishedAt = "2026-03-01T10:00:00Z",
        htmlUrl = "https://github.com/137458/YunX/releases/tag/v1.2.4"
    )

    @Test
    fun comparesVersionSegmentsNumericallyNotLexically() {
        // "1.10" 必须大于 "1.9"：按字符串比会得出相反结论
        assertTrue(UpdateChecker.compareVersions("v1.10", "1.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.9", "v1.10") < 0)
    }

    @Test
    fun treatsMissingSegmentsAsZero() {
        assertEquals(0, UpdateChecker.compareVersions("v1.2", "1.2.0"))
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0)
    }

    @Test
    fun ignoresLeadingVAndComparesEqualVersions() {
        assertEquals(0, UpdateChecker.compareVersions("v2.0.0", "2.0.0"))
    }

    @Test
    fun picksFirstApkAssetIgnoringCaseAndOtherFiles() {
        val apk = release(
            listOf(
                UpdateChecker.Asset("checksums.txt", "https://x/checksums.txt"),
                UpdateChecker.Asset("YunX-1.2.4.APK", "https://x/app.apk", size = 1024L)
            )
        ).apkAsset
        assertEquals("YunX-1.2.4.APK", apk?.name)
        assertEquals(1024L, apk?.size)
    }

    @Test
    fun returnsNullWhenNoApkAssetPublished() {
        assertNull(release(listOf(UpdateChecker.Asset("source.zip", "https://x/src.zip"))).apkAsset)
        assertNull(release(emptyList()).apkAsset)
    }

    @Test
    fun buildsMirrorUrlWithoutDroppingQuery() {
        assertEquals(
            UpdateChecker.MIRROR_PREFIX + "https://github.com/a/b/releases/download/v1/app.apk?token=t",
            UpdateChecker.mirrorUrl("https://github.com/a/b/releases/download/v1/app.apk?token=t")
        )
    }
}
