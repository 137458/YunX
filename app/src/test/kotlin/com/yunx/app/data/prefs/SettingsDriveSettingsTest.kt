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

package com.yunx.app.data.prefs

import com.yunx.app.data.download.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 账号页排序 / 隐藏未登录 / 列表缩略图等设置项的存储契约。
 *
 * 这些键是 DriveOrder（排序与过滤的唯一输入）与 AppThemeManager（运行时内存态）的数据源：
 * 键名写错、或排序的序列化格式被改，表现是「重启后账号顺序无声丢失」，
 * 界面上不报任何错，所以用单测把键名、默认值与往返格式钉住。
 *
 * 注意：本组键直写 prefs、不推进 [SettingsRepository.changeVersion]，
 * 属既有设计（这三个开关的即时刷新由 AppThemeManager 自己的状态驱动），不要顺手改成 write()。
 */
class SettingsDriveSettingsTest {

    @Test
    fun accountPageDefaultsAreEmptyOrderAndNoHiding() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        assertEquals(emptyList<String>(), repo.driveAccountOrder)
        assertFalse(repo.hideUnloggedDrives)
        assertTrue(repo.cloudThumbnails)
    }

    @Test
    fun driveAccountOrderRoundTripsInSavedOrder() {
        val prefs = InMemorySharedPreferences()
        val saved = listOf(
            DownloadPlatform.PAN123,
            DownloadPlatform.QUARK,
            DownloadPlatform.PAN115,
            DownloadPlatform.LANZOU
        )

        SettingsRepository(prefs).driveAccountOrder = saved

        assertEquals(saved, SettingsRepository(prefs).driveAccountOrder)
    }

    @Test
    fun driveAccountOrderDropsBlankSegmentsFromStoredValue() {
        val prefs = InMemorySharedPreferences()
        prefs.edit().putString("drive_account_order", ",quark,,baidu,").apply()

        assertEquals(
            listOf(DownloadPlatform.QUARK, DownloadPlatform.BAIDU),
            SettingsRepository(prefs).driveAccountOrder
        )
    }

    @Test
    fun driveAccountOrderSurvivesReorderAndClearsWhenEmptied() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.driveAccountOrder = listOf(DownloadPlatform.UC, DownloadPlatform.C139)
        repo.driveAccountOrder = listOf(DownloadPlatform.C139, DownloadPlatform.UC)
        assertEquals(
            listOf(DownloadPlatform.C139, DownloadPlatform.UC),
            SettingsRepository(prefs).driveAccountOrder
        )

        repo.driveAccountOrder = emptyList()
        assertEquals(emptyList<String>(), SettingsRepository(prefs).driveAccountOrder)
    }

    @Test
    fun booleanSwitchesRoundTripBothValues() {
        val prefs = InMemorySharedPreferences()
        val repo = SettingsRepository(prefs)

        repo.hideUnloggedDrives = true
        repo.cloudThumbnails = false
        repo.clipboardSuggestEnabled = false
        repo.acceptPrereleaseUpdate = true

        val reloaded = SettingsRepository(prefs)
        assertTrue(reloaded.hideUnloggedDrives)
        assertFalse(reloaded.cloudThumbnails)
        assertFalse(reloaded.clipboardSuggestEnabled)
        assertTrue(reloaded.acceptPrereleaseUpdate)

        repo.hideUnloggedDrives = false
        repo.cloudThumbnails = true
        repo.clipboardSuggestEnabled = true
        repo.acceptPrereleaseUpdate = false

        val back = SettingsRepository(prefs)
        assertFalse(back.hideUnloggedDrives)
        assertTrue(back.cloudThumbnails)
        assertTrue(back.clipboardSuggestEnabled)
        assertFalse(back.acceptPrereleaseUpdate)
    }
}
