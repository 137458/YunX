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

import android.content.SharedPreferences
import com.yunx.app.data.download.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SettingsRepository.changeVersion 是主题刷新的订阅信号：
 * 任何一次设置写入都必须推进版本号，否则「设置写完后主题不刷新」。
 * 用内存版 SharedPreferences 构造仓储，使该业务逻辑无需 Android 运行时即可测试。
 */
class SettingsRepositoryChangeVersionTest {

    @Test
    fun everySetterAdvancesChangeVersion() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        assertAdvancedByOne(repo) { repo.darkMode = 2 }
        assertAdvancedByOne(repo) { repo.themeColorMode = 2 }
        assertAdvancedByOne(repo) { repo.themeSeedColor = 0xFF112233L }
        assertAdvancedByOne(repo) { repo.isAmoled = true }
        assertAdvancedByOne(repo) { repo.bottomBarStyle = 1 }
        assertAdvancedByOne(repo) { repo.maxConcurrentDownloads = 4 }
        assertAdvancedByOne(repo) { repo.downloadSpeedLimit = 1024L }
        assertAdvancedByOne(repo) { repo.downloadRetryCount = 5 }
        assertAdvancedByOne(repo) { repo.keepDownloadWhenLocked = false }
        assertAdvancedByOne(repo) { repo.notificationShowSpeed = false }
        assertAdvancedByOne(repo) { repo.appIconVariant = 1 }
        assertAdvancedByOne(repo) { repo.ignoreSslCert = true }
        assertAdvancedByOne(repo) { repo.baiduLimitHintDismissed = true }
        assertAdvancedByOne(repo) { repo.downloadDirUri = "content://yunx/downloads" }
        assertAdvancedByOne(repo) { repo.downloadThreads = 64 }
        assertAdvancedByOne(repo) { repo.setDownloadThreads(DownloadPlatform.QUARK, 128) }
    }

    @Test
    fun xunleiThreadSetterDoesNotWriteNorAdvanceVersion() {
        val repo = SettingsRepository(InMemorySharedPreferences())
        val before = SettingsRepository.changeVersion

        repo.setDownloadThreads(DownloadPlatform.XUNLEI, 64)

        assertEquals(before, SettingsRepository.changeVersion)
        assertEquals(SettingsRepository.XUNLEI_DOWNLOAD_THREADS, repo.downloadThreadsFor(DownloadPlatform.XUNLEI))
    }

    @Test
    fun changeVersionIsSharedAcrossRepositoryInstances() {
        val first = SettingsRepository(InMemorySharedPreferences())
        val second = SettingsRepository(InMemorySharedPreferences())
        val start = SettingsRepository.changeVersion

        first.darkMode = 1
        assertEquals(start + 1, SettingsRepository.changeVersion)

        // 设置页自己 new 出来的仓储实例写入后，主题层订阅的版本号同样要推进
        second.isAmoled = true
        assertEquals(start + 2, SettingsRepository.changeVersion)
    }

    @Test
    fun writesRoundTripAndClampOutOfRangeValues() {
        val repo = SettingsRepository(InMemorySharedPreferences())

        repo.darkMode = 9
        assertEquals(2, repo.darkMode)
        repo.themeColorMode = -3
        assertEquals(0, repo.themeColorMode)
        repo.bottomBarStyle = 99
        assertEquals(SettingsRepository.BOTTOM_BAR_STYLE_COUNT - 1, repo.bottomBarStyle)
        repo.downloadThreads = 9999
        assertEquals(SettingsRepository.MAX_DOWNLOAD_THREADS, repo.downloadThreads)
        repo.setDownloadThreads(DownloadPlatform.BAIDU, 0)
        assertEquals(1, repo.downloadThreadsFor(DownloadPlatform.BAIDU))
        repo.isAmoled = true
        assertTrue(repo.isAmoled)
        repo.downloadDirUri = "content://yunx/downloads"
        assertEquals("content://yunx/downloads", repo.downloadDirUri)
        repo.downloadDirUri = null
        assertNull(repo.downloadDirUri)
    }

    private fun assertAdvancedByOne(repo: SettingsRepository, write: () -> Unit) {
        val before = SettingsRepository.changeVersion
        write()
        assertEquals(before + 1, SettingsRepository.changeVersion)
    }
}

/** 内存版 SharedPreferences：只实现仓储真正使用到的读写语义 */
private class InMemorySharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = InMemoryEditor(values)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
}

/** 内存版 Editor：apply/commit 时把待写入内容合并进底层 Map */
private class InMemoryEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {

    private val pending = mutableMapOf<String, Any?>()
    private val removed = mutableSetOf<String>()

    override fun putString(key: String, value: String?): SharedPreferences.Editor = stage(key, value)

    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = stage(key, values)

    override fun putInt(key: String, value: Int): SharedPreferences.Editor = stage(key, value)

    override fun putLong(key: String, value: Long): SharedPreferences.Editor = stage(key, value)

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = stage(key, value)

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = stage(key, value)

    override fun remove(key: String): SharedPreferences.Editor {
        pending.remove(key)
        removed += key
        return this
    }

    override fun clear(): SharedPreferences.Editor {
        pending.clear()
        removed += values.keys
        return this
    }

    override fun commit(): Boolean {
        apply()
        return true
    }

    override fun apply() {
        removed.forEach { values.remove(it) }
        removed.clear()
        values.putAll(pending)
        pending.clear()
    }

    private fun stage(key: String, value: Any?): SharedPreferences.Editor {
        removed -= key
        pending[key] = value
        return this
    }
}
