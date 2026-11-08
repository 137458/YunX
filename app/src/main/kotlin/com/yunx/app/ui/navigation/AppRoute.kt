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

package com.yunx.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 应用路由表（Navigation Compose 类型安全路由）。
 *
 * 设计约定：
 * - 全屏页面一律是 NavHost 的 destination，系统返回手势由 NavHost 的 `PredictiveBackHandler`
 *   接管（可预测返回的前提是返回动作不被页面自建的布尔覆盖层与 `BackHandler` 提前消费）。
 * - 参数只放「打开页面时必须知道」的最小值；页面内部状态由 ViewModel / `rememberSaveable`
 *   自行保存，进程重建后由导航恢复 route 参数并重建页面。
 * - 需要跨页面传递的临时请求（如在线预览的 MediaPreviewRequest）不塞进 route，继续用进程内
 *   事件通道，避免路由里出现不可序列化的对象。
 *
 * 迁移分批推进；本文件先收录主框架与「设置类」全屏页，其余页面随批次补齐。
 */
@Serializable
sealed interface AppRoute

/** 主框架：顶栏 + 底部/侧边导航 + 四个 Tab 内容。 */
@Serializable
data object MainRoute : AppRoute

/** 首次启动引导页。 */
@Serializable
data object OnboardingRoute : AppRoute

/** 关于云析。 */
@Serializable
data object AboutRoute : AppRoute

/** 支持开发。 */
@Serializable
data object SupportRoute : AppRoute

/** 主题与外观。 */
@Serializable
data object ThemeRoute : AppRoute

/** 语言。 */
@Serializable
data object LanguageRoute : AppRoute

/** 链接打开方式。 */
@Serializable
data object LinkOpenRoute : AppRoute

/** 多账号下载设置。 */
@Serializable
data object MultiAccountDownloadRoute : AppRoute

/** 收藏网盘链接。 */
@Serializable
data object BookmarkRoute : AppRoute

/** 应用内公告整页；[initialDetailId] 非空时直接打开详情，否则先看列表。 */
@Serializable
data class AnnouncementRoute(val initialDetailId: String? = null) : AppRoute

/**
 * 网盘账号登录页（各平台共用同一个 destination，按 [platform] 分派）。
 *
 * [platform] 取 `AccountLoginPlatform` 的枚举名；[addAccount] = 「添加账号」入口进来的一次登录，
 * 成功后落成新的一行（标记由本页在离开时清理，避免影响日后的普通重新登录）。
 */
@Serializable
data class AccountLoginRoute(
    val platform: String,
    val addAccount: Boolean = false
) : AppRoute

/** [AccountLoginRoute.platform] 的取值。 */
enum class AccountLoginPlatform {
    QUARK, UC, XUNLEI, XUNLEI_WEB, BAIDU, C139, PAN123, PAN115, GUANGYA, ILANZOU, LANZOU
}

/** 迅雷设备验证页（应用内 WebView 承载）；[verifyUrl] 与 [deviceId] 由登录页给出。 */
@Serializable
data class XunleiVerifyRoute(val verifyUrl: String, val deviceId: String) : AppRoute

/** 视频平台登录页（多平台共用一页）；[platformName] 为 `VideoPlatform.name`，[newAccount] 为「添加账号」而非覆盖主账号。 */
@Serializable
data class VideoLoginRoute(val platformName: String, val newAccount: Boolean = false) : AppRoute

/** 视频在线预览页。预览请求本身走进程内事件通道（不可序列化），路由只表达「预览页在栈顶」。 */
@Serializable
data object PreviewRoute : AppRoute
