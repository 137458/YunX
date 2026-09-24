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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.download.DownloadPlatform

/**
 * 应用设置（SharedPreferences 持久化）。
 *
 * 所有写入都经过 [write]，在落盘的同时推进 [changeVersion]，
 * 让主题层（AppThemeManager）能感知到任何一处设置写入并刷新。
 */
class SettingsRepository internal constructor(
    private val prefs: SharedPreferences
) {

    /** 应用设置仓储：绑定应用级 SharedPreferences 文件 */
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    ) {
        migrateLegacyPrefs(
            context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        )
    }

    /**
     * 迁移历史上直接写在 `yunx_prefs` 里的两个键（引导页已展示、已忽略版本号）。
     * 只搬运新文件里还没有的值，幂等；不推进 [changeVersion]（与主题无关）。
     */
    private fun migrateLegacyPrefs(legacy: SharedPreferences) {
        val editor = prefs.edit()
        var dirty = false
        if (!prefs.contains(KEY_ONBOARDING_SHOWN) && legacy.contains(KEY_ONBOARDING_SHOWN)) {
            editor.putBoolean(KEY_ONBOARDING_SHOWN, legacy.getBoolean(KEY_ONBOARDING_SHOWN, false))
            dirty = true
        }
        if (!prefs.contains(KEY_IGNORED_VERSION) && legacy.contains(KEY_IGNORED_VERSION)) {
            editor.putString(KEY_IGNORED_VERSION, legacy.getString(KEY_IGNORED_VERSION, ""))
            dirty = true
        }
        if (dirty) editor.apply()
    }

    /**
     * 执行一次设置写入：先提交到 SharedPreferences，再推进全局设置版本号。
     * 抽成统一入口是为了保证「任何一个 setter 写入都会让版本号自增」不会被遗漏。
     */
    private fun write(block: SharedPreferences.Editor.() -> SharedPreferences.Editor) {
        prefs.edit().block().apply()
        changeVersion++
    }

    /** 下载线程数（通用/手动添加，分片并发数），默认 32，上限 512 */
    var downloadThreads: Int
        get() = downloadThreadsFor(DownloadPlatform.GENERIC)
        set(value) = setDownloadThreads(DownloadPlatform.GENERIC, value)

    /** 获取指定平台的下载线程数；迅雷固定 8，其余默认 32、上限 512 */
    fun downloadThreadsFor(platform: String): Int {
        if (platform == DownloadPlatform.XUNLEI) return XUNLEI_DOWNLOAD_THREADS
        return prefs.getInt(prefsKey(platform), DEFAULT_DOWNLOAD_THREADS)
            .coerceIn(1, MAX_DOWNLOAD_THREADS)
    }

    /** 设置指定平台的下载线程数；迅雷不可修改（不写入也不推进版本号） */
    fun setDownloadThreads(platform: String, value: Int) {
        if (platform == DownloadPlatform.XUNLEI) return
        write { putInt(prefsKey(platform), value.coerceIn(1, MAX_DOWNLOAD_THREADS)) }
    }

    private fun prefsKey(platform: String): String =
        if (platform.isBlank() || platform == DownloadPlatform.GENERIC) "download_threads"
        else "download_threads_$platform"

    /** 自定义下载保存目录（SAF tree Uri，content://...）；null/空 = 系统默认 Download 目录 */
    var downloadDirUri: String?
        get() = prefs.getString("download_dir_uri", null)
        set(value) {
            write { putString("download_dir_uri", value) }
        }

    /** 最大同时下载任务数（默认 1：前台任务吃满带宽，其余排队；参考 IDM 默认单任务满速） */
    var maxConcurrentDownloads: Int
        get() = prefs.getInt("max_concurrent_downloads", DEFAULT_MAX_CONCURRENT_DOWNLOADS)
        set(value) {
            write { putInt("max_concurrent_downloads", value.coerceIn(1, 10)) }
        }

    /** 下载速度限制（字节/秒；0 = 不限速） */
    var downloadSpeedLimit: Long
        get() = prefs.getLong("download_speed_limit", 0L)
        set(value) {
            write { putLong("download_speed_limit", value.coerceAtLeast(0L)) }
        }

    /** 下载失败后自动重试次数（默认 3，范围 0-10） */
    var downloadRetryCount: Int
        get() = prefs.getInt("download_retry_count", DEFAULT_DOWNLOAD_RETRY_COUNT)
        set(value) {
            write { putInt("download_retry_count", value.coerceIn(0, 10)) }
        }

    /** 锁屏后保持下载：开启后下载时获取 WakeLock，并可引导加入「忽略电池优化」白名单（默认开启） */
    var keepDownloadWhenLocked: Boolean
        get() = prefs.getBoolean("keep_download_when_locked", true)
        set(value) {
            write { putBoolean("keep_download_when_locked", value) }
        }

    /** 通知栏进度样式：true=完整通知（进度条+下载速度）；false=仅显示通知（隐藏速度） */
    var notificationShowSpeed: Boolean
        get() = prefs.getBoolean("notification_show_speed", true)
        set(value) {
            write { putBoolean("notification_show_speed", value) }
        }

    /** 桌面图标样式：0=经典图标(icon)，1=新图标(icon2)；切换经 activity-alias 动态生效 */
    var appIconVariant: Int
        get() = prefs.getInt("app_icon_variant", 0)
        set(value) {
            write { putInt("app_icon_variant", value.coerceIn(0, 1)) }
        }

    /** 忽略 SSL 证书校验（抓包调试用，隐藏菜单开启；默认关闭） */
    var ignoreSslCert: Boolean
        get() = prefs.getBoolean("ignore_ssl_cert", false)
        set(value) {
            write { putBoolean("ignore_ssl_cert", value) }
        }

    /** 百度网盘大文件限速提示：是否已选择「不再显示」 */
    var baiduLimitHintDismissed: Boolean
        get() = prefs.getBoolean("baidu_limit_hint_dismissed", false)
        set(value) {
            write { putBoolean("baidu_limit_hint_dismissed", value) }
        }

    /** 首次启动引导页是否已展示（历史上写在 yunx_prefs，已由构造函数迁移过来） */
    var onboardingShown: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SHOWN, false)
        set(value) {
            write { putBoolean(KEY_ONBOARDING_SHOWN, value) }
        }

    /** 已选择「忽略本次」的更新版本号（空串 = 未忽略任何版本） */
    var ignoredUpdateVersion: String
        get() = prefs.getString(KEY_IGNORED_VERSION, "").orEmpty()
        set(value) {
            write { putString(KEY_IGNORED_VERSION, value) }
        }

    /** 深色模式：0=跟随系统，1=浅色，2=深色 */
    var darkMode: Int
        get() = prefs.getInt("dark_mode", 0)
        set(value) {
            write { putInt("dark_mode", value.coerceIn(0, 2)) }
        }

    /** 主题色模式：0=动态色彩（Android12+ 壁纸取色，低版本回退默认蓝），1=默认蓝色，2=自定义种子色 */
    var themeColorMode: Int
        get() = prefs.getInt("theme_color_mode", 0)
        set(value) {
            write { putInt("theme_color_mode", value.coerceIn(0, 2)) }
        }

    /** 自定义主题种子色（ARGB 值） */
    var themeSeedColor: Long
        get() = prefs.getLong("theme_seed_color", DEFAULT_SEED_COLOR)
        set(value) {
            write { putLong("theme_seed_color", value) }
        }

    /** AMOLED 纯黑：深色模式下把背景/表面压到纯黑（默认关闭） */
    var isAmoled: Boolean
        get() = prefs.getBoolean("is_amoled", false)
        set(value) {
            write { putBoolean("is_amoled", value) }
        }

    /**
     * 底部导航栏样式：0=液态玻璃悬浮，1=毛玻璃悬浮，2=Miuix 标准底栏。
     * 与 BottomBarStyle 的 ordinal 一一对应（UI 层用 BottomBarStyle.fromPersisted 解码兜底）。
     */
    var bottomBarStyle: Int
        get() = prefs.getInt("bottom_bar_style", DEFAULT_BOTTOM_BAR_STYLE)
        set(value) {
            write { putInt("bottom_bar_style", value.coerceIn(0, BOTTOM_BAR_STYLE_COUNT - 1)) }
        }

    companion object {
        /** SharedPreferences 文件名（与 MainActivity/CrashActivity 的窗口主题预读保持一致） */
        const val PREFS_NAME = "yunx_settings"

        /** 早期 UI 状态文件：仅为一次性迁移保留读取，不再写入 */
        private const val LEGACY_PREFS_NAME = "yunx_prefs"
        private const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        private const val KEY_IGNORED_VERSION = "ignored_version"

        /**
         * 设置写入版本号（所有实例共享的 Compose 状态）：
         * 任意一个 setter 写入后自增，AppThemeManager 读取它建立订阅，
         * 从而在设置被任何地方写入后重新同步主题状态。
         */
        var changeVersion by mutableIntStateOf(0)
            private set

        const val DEFAULT_DOWNLOAD_THREADS = 32
        const val MAX_DOWNLOAD_THREADS = 512
        const val XUNLEI_DOWNLOAD_THREADS = 8
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 1
        const val DEFAULT_DOWNLOAD_RETRY_COUNT = 3

        /** 默认主题种子色：Material Blue（与内置默认方案一致） */
        const val DEFAULT_SEED_COLOR = 0xFF415F91L

        /** 底栏样式可选数量（液态玻璃 / 毛玻璃 / Miuix 标准底栏） */
        const val BOTTOM_BAR_STYLE_COUNT = 3

        /** 默认底栏样式：液态玻璃悬浮底栏 */
        const val DEFAULT_BOTTOM_BAR_STYLE = 0
    }
}
