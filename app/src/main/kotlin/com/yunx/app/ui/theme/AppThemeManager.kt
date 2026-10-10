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

package com.yunx.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yunx.app.data.prefs.SettingsRepository

/**
 * 应用本地主题设置管理器（单例）：
 * - 内存中持有主题设置（mutableStateOf），修改后 Compose 自动重组、主题即时生效；
 * - 读写同时同步 SharedPreferences 持久化；
 * - 由 ComposeEmptyActivityTheme 初始化（幂等），确保任何入口（主界面/崩溃页）都能读到；
 * - 订阅 SettingsRepository.changeVersion，任何一处设置写入都会重新同步内存状态。
 */
object AppThemeManager {

    /** 深色模式：0=跟随系统，1=浅色，2=深色 */
    var darkMode by mutableStateOf(0)
        private set

    /** 主题色模式：0=动态色彩，1=默认蓝色，2=自定义种子色 */
    var colorMode by mutableStateOf(0)
        private set

    /** 自定义主题种子色（ARGB） */
    var seedColor by mutableStateOf(SettingsRepository.DEFAULT_SEED_COLOR)
        private set

    /** 底部导航栏样式序号（对应 BottomBarStyle.ordinal） */
    var bottomBarStyle by mutableStateOf(SettingsRepository.DEFAULT_BOTTOM_BAR_STYLE)
        private set

    /** AMOLED 纯黑深色（仅深色模式生效） */
    var isAmoled by mutableStateOf(false)
        private set

    /** 文件名是否多行显示 */
    var fileNameMultiLine by mutableStateOf(false)
        private set

    /** 下载调试：开启后下载页长按任务可查看「调试信息」（默认关闭；关闭会清空全部调试日志） */
    var downloadDebug by mutableStateOf(false)
        private set

    private var initialized = false

    /** 复用同一个仓储实例，避免每次读写都重建 SharedPreferences 包装 */
    private var repository: SettingsRepository? = null

    /** 最近一次同步时的设置版本号；与 changeVersion 不一致说明有外部写入 */
    private var syncedVersion = -1

    private fun repo(context: Context): SettingsRepository =
        repository ?: SettingsRepository(context).also { repository = it }

    /** 从持久化存储加载（幂等；首次调用有效） */
    fun init(context: Context) {
        if (initialized) return
        reload(repo(context))
        initialized = true
    }

    /**
     * 订阅设置写入版本号（在 Composable 中调用）：
     * 读取 SettingsRepository.changeVersion 建立 Compose 订阅；一旦版本号变化
     * （例如设置页绕过本管理器直接写 SettingsRepository），就重新加载内存状态，
     * 消除「设置页写完后主题不刷新」的隐患。
     */
    fun syncFromSettings(context: Context) {
        init(context)
        if (SettingsRepository.changeVersion != syncedVersion) {
            reload(repo(context))
        }
    }

    /** 从仓储重新加载全部主题相关设置，并记录已同步的版本号 */
    private fun reload(s: SettingsRepository) {
        darkMode = s.darkMode
        colorMode = s.themeColorMode
        seedColor = s.themeSeedColor
        bottomBarStyle = s.bottomBarStyle
        isAmoled = s.isAmoled
        fileNameMultiLine = s.fileNameMultiLine
        downloadDebug = s.downloadDebug
        syncedVersion = SettingsRepository.changeVersion
    }

    /** 本管理器自己写入后立即标记已同步，避免下一帧多做一次无意义的重载 */
    private fun markSynced() {
        syncedVersion = SettingsRepository.changeVersion
    }

    /** 设置深色模式并持久化 */
    fun setDarkMode(context: Context, value: Int) {
        darkMode = value.coerceIn(0, 2)
        repo(context).darkMode = darkMode
        markSynced()
    }

    /** 设置主题色模式并持久化（0=动态 / 1=默认 / 2=自定义） */
    fun setColorMode(context: Context, value: Int) {
        colorMode = value.coerceIn(0, 2)
        repo(context).themeColorMode = colorMode
        markSynced()
    }

    /** 设置自定义种子色（自动切到自定义模式）并持久化 */
    fun setSeedColor(context: Context, argb: Long) {
        seedColor = argb
        colorMode = 2
        repo(context).apply {
            themeSeedColor = argb
            themeColorMode = 2
        }
        markSynced()
    }

    /** 设置底部导航栏样式并持久化 */
    fun setBottomBarStyle(context: Context, value: Int) {
        val coerced = value.coerceIn(0, SettingsRepository.BOTTOM_BAR_STYLE_COUNT - 1)
        bottomBarStyle = coerced
        repo(context).bottomBarStyle = coerced
        markSynced()
    }

    /** 设置 AMOLED 纯黑并持久化（仅深色模式有意义） */
    fun setAmoled(context: Context, value: Boolean) {
        isAmoled = value
        repo(context).isAmoled = value
        markSynced()
    }

    /** 设置文件名多行显示并持久化 */
    fun setFileNameMultiLine(context: Context, value: Boolean) {
        fileNameMultiLine = value
        repo(context).fileNameMultiLine = value
        markSynced()
    }

    /**
     * 设置下载调试开关并持久化。**关闭会清空全部调试日志**，所以清空与提示由调用方负责
     * （设置页关开关前会先弹二次确认，确认后调用 `DownloadDebugLog.setEnabled`）。
     */
    fun setDownloadDebug(context: Context, value: Boolean) {
        downloadDebug = value
        repo(context).downloadDebug = value
        markSynced()
    }
}
