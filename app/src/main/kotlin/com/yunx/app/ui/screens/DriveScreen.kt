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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.GuangYaAccountEntity
import com.yunx.app.data.db.ILanzouAccountEntity
import com.yunx.app.data.db.LanzouAccountEntity
import com.yunx.app.data.db.Pan115AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.VideoAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.items.drivePlatformLogoRes
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.GuangYaCloudViewModel
import com.yunx.app.ui.viewmodel.ILanzouCloudViewModel
import com.yunx.app.ui.viewmodel.LanzouCloudViewModel
import com.yunx.app.ui.viewmodel.MultiAccountViewModel
import com.yunx.app.ui.viewmodel.Pan115CloudViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 网盘账号展示模型。
 */
private data class DriveAccount(
    val id: String,
    val name: String,
    val description: String,
    val avatarText: String,
    val isLoggedIn: Boolean = false
)

/**
 * 账号页（原「网盘」页）：
 * - 上半区「网盘账号」：各网盘登录态、空间概览与账号管理（主账号 / 备注 / 退出 / 添加）；
 * - 已登录点击进入对应云盘浏览；未登录点击进入对应登录；
 * - 下半区「视频解析」：视频平台账号登录入口（见 [VideoAccountSection]）。
 */
@Composable
fun DriveScreen(
    scrollBehavior: ScrollBehavior,
    quarkAccount: QuarkAccountEntity?,
    ucAccount: UCAccountEntity?,
    xunleiAccount: XunleiAccountEntity?,
    baiduAccount: BaiduAccountEntity?,
    c139Account: C139AccountEntity?,
    pan123Account: Pan123AccountEntity?,
    pan115Account: Pan115AccountEntity?,
    guangyaAccount: GuangYaAccountEntity?,
    ilanzouAccount: ILanzouAccountEntity?,
    lanzouAccount: LanzouAccountEntity?,
    /** 夸克云盘浏览 ViewModel（网盘 Tab 内切换展示，非全屏） */
    quarkCloudViewModel: QuarkCloudViewModel,
    /** UC 网盘云盘浏览 ViewModel */
    ucCloudViewModel: UCCoudViewModel,
    /** 迅雷网盘云盘浏览 ViewModel */
    xunleiCloudViewModel: XunleiCloudViewModel,
    /** 百度网盘云盘浏览 ViewModel */
    baiduCloudViewModel: BaiduCloudViewModel,
    /** 139 网盘云盘浏览 ViewModel */
    c139CloudViewModel: C139CloudViewModel,
    /** 123 云盘浏览 ViewModel */
    pan123CloudViewModel: Pan123CloudViewModel,
    /** 115 网盘浏览 ViewModel */
    pan115CloudViewModel: Pan115CloudViewModel,
    /** 光鸭云盘浏览 ViewModel */
    guangyaCloudViewModel: GuangYaCloudViewModel,
    /** 蓝奏云优享版浏览 ViewModel */
    ilanzouCloudViewModel: ILanzouCloudViewModel,
    /** 蓝奏云浏览 ViewModel */
    lanzouCloudViewModel: LanzouCloudViewModel,
    /** 网盘空间详情 ViewModel（顶部空间总览） */
    driveQuotaViewModel: DriveQuotaViewModel,
    /** 多账号：按平台列出账号 / 设主账号 / 改备注 / 退出单个账号 */
    multiAccountViewModel: MultiAccountViewModel,
    onQuarkLogin: () -> Unit,
    /** 「添加账号」：先给对应平台登录页打上「新增」标记，再打开它的登录页 */
    onAddAccount: (String) -> Unit,
    /** 夸克云盘下载入队后切换到「下载」Tab */
    onDownloadStarted: () -> Unit = {},
    onUCLogin: () -> Unit,
    onXunleiLogin: () -> Unit,
    onBaiduLogin: () -> Unit,
    onC139Login: () -> Unit,
    onPan123Login: () -> Unit,
    onPan115Login: () -> Unit,
    onGuangYaLogin: () -> Unit,
    onILanzouLogin: () -> Unit,
    onLanzouLogin: () -> Unit,
    /** GitHub：已配置 Token 时卡片可进入「我的主页」，未配置时点击配置 Token */
    githubHasToken: Boolean = false,
    onGitHubTokenClick: () -> Unit = {},
    onGitHubBrowseHome: () -> Unit = {},
    onGitHubClearToken: () -> Unit = {},
    /** 视频解析：需要登录的平台 → 当前主账号（未登录的平台不在 map 里） */
    videoLoginState: Map<VideoPlatform, VideoAccountEntity> = emptyMap(),
    /** 视频解析：全部已登录视频账号（含同平台多账号） */
    videoAccounts: List<VideoAccountEntity> = emptyList(),
    onVideoLogin: (VideoPlatform) -> Unit = {},
    onVideoAddAccount: (VideoPlatform) -> Unit = {},
    onVideoSetPrimary: (VideoPlatform, String) -> Unit = { _, _ -> },
    onVideoRename: (String, String) -> Unit = { _, _ -> },
    onVideoRemove: (String) -> Unit = {},
    onVideoRemoveAll: (VideoPlatform) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showQuarkSheet by remember { mutableStateOf(false) }
    var showUCSheet by remember { mutableStateOf(false) }
    var showXunleiSheet by remember { mutableStateOf(false) }
    var showBaiduSheet by remember { mutableStateOf(false) }
    var showC139Sheet by remember { mutableStateOf(false) }
    var showPan123Sheet by remember { mutableStateOf(false) }
    var showPan115Sheet by remember { mutableStateOf(false) }
    var showGuangYaSheet by remember { mutableStateOf(false) }
    var showILanzouSheet by remember { mutableStateOf(false) }
    var showLanzouSheet by remember { mutableStateOf(false) }
    // GitHub Token 更多菜单
    var showGitHubSheet by remember { mutableStateOf(false) }
    // 视频平台账号弹窗：非空 = 打开对应平台的账号管理
    var showVideoSheet by remember { mutableStateOf<VideoPlatform?>(null) }
    // 10 大网盘云盘浏览：网盘 Tab 内切换（非全屏），切 Tab 再回来仍保留
    var showCloud by rememberSaveable { mutableStateOf(false) }
    var showUCCloud by rememberSaveable { mutableStateOf(false) }
    var showXunleiCloud by rememberSaveable { mutableStateOf(false) }
    var showBaiduCloud by rememberSaveable { mutableStateOf(false) }
    var showC139Cloud by rememberSaveable { mutableStateOf(false) }
    var showPan123Cloud by rememberSaveable { mutableStateOf(false) }
    var showPan115Cloud by rememberSaveable { mutableStateOf(false) }
    var showGuangYaCloud by rememberSaveable { mutableStateOf(false) }
    var showILanzouCloud by rememberSaveable { mutableStateOf(false) }
    var showLanzouCloud by rememberSaveable { mutableStateOf(false) }

    val quark = DriveAccount(
        id = "quark",
        name = "夸克网盘",
        description = quarkAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "夸",
        isLoggedIn = quarkAccount != null
    )
    val uc = DriveAccount(
        id = "uc",
        name = "UC网盘",
        description = ucAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "UC",
        isLoggedIn = ucAccount != null
    )
    val xunlei = DriveAccount(
        id = "xunlei",
        name = "迅雷网盘",
        description = xunleiAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "迅",
        isLoggedIn = xunleiAccount != null
    )
    val baidu = DriveAccount(
        id = "baidu",
        name = "百度网盘",
        description = baiduAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "度",
        isLoggedIn = baiduAccount != null
    )
    val c139 = DriveAccount(
        id = "c139",
        name = "139网盘",
        description = c139Account?.nickname ?: "点击登录，支持解析下载",
        avatarText = "139",
        isLoggedIn = c139Account != null
    )
    val pan123 = DriveAccount(
        id = "pan123",
        name = "123云盘",
        description = pan123Account?.nickname ?: "点击登录，支持解析下载",
        avatarText = "123",
        isLoggedIn = pan123Account != null
    )
    val pan115 = DriveAccount(
        id = "pan115",
        name = "115网盘",
        description = pan115Account?.nickname ?: "点击登录，支持解析下载",
        avatarText = "115",
        isLoggedIn = pan115Account != null
    )
    val guangya = DriveAccount(
        id = "guangya",
        name = "光鸭云盘",
        description = guangyaAccount?.nickname ?: "点击登录，支持解析下载",
        avatarText = "光",
        isLoggedIn = guangyaAccount != null
    )
    val ilanzou = DriveAccount(
        id = "ilanzou",
        name = "蓝奏云优享版",
        description = ilanzouAccount?.nickname?.let { maskAccount(it) } ?: "点击登录，支持解析下载",
        avatarText = "蓝优",
        isLoggedIn = ilanzouAccount != null
    )
    val lanzou = DriveAccount(
        id = "lanzou",
        name = "蓝奏云",
        description = lanzouAccount?.nickname?.let { maskAccount(it) } ?: "点击登录，支持解析下载",
        avatarText = "蓝",
        isLoggedIn = lanzouAccount != null
    )
    // GitHub：把 GitHub 当网盘浏览下载；此处仅做 Token 管理（提升 API 限额），浏览入口在解析页
    val github = DriveAccount(
        id = "github",
        name = "GitHub",
        description = if (githubHasToken) "已配置 Token" else "点击配置 Token",
        avatarText = "GH",
        isLoggedIn = githubHasToken
    )
    val others = remember { emptyList<DriveAccount>() }

    // 进入网盘页加载空间详情（仅已登录平台）
    LaunchedEffect(Unit) {
        driveQuotaViewModel.loadAll()
    }
    val isRefreshing by driveQuotaViewModel.loading.collectAsState()
    // 必须在 Composable 上下文里收集：LazyColumn 的 LazyListScope lambda 不是 @Composable
    val quotaError by driveQuotaViewModel.quotaError.collectAsState()

    // 账号列表 ↔ 各网盘浏览：平滑过渡（淡入 + 轻微缩放，保持本地 Miuix 动效）
    AnimatedContent(
        targetState = when {
            showCloud -> 1
            showUCCloud -> 2
            showXunleiCloud -> 3
            showBaiduCloud -> 4
            showC139Cloud -> 5
            showPan123Cloud -> 6
            showPan115Cloud -> 7
            showGuangYaCloud -> 8
            showILanzouCloud -> 9
            showLanzouCloud -> 10
            else -> 0
        },
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f))
                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.98f))
        },
        label = "driveContent"
    ) { target ->
        when (target) {
            1 -> CloudDriveScreen(
                viewModel = quarkCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            2 -> UCCoudScreen(
                viewModel = ucCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showUCCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            3 -> XunleiCloudScreen(
                viewModel = xunleiCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showXunleiCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            4 -> BaiduCloudScreen(
                viewModel = baiduCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showBaiduCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            5 -> C139CloudScreen(
                viewModel = c139CloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showC139Cloud = false },
                onDownloadStarted = onDownloadStarted
            )
            6 -> Pan123CloudScreen(
                viewModel = pan123CloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showPan123Cloud = false },
                onDownloadStarted = onDownloadStarted
            )
            7 -> Pan115CloudScreen(
                viewModel = pan115CloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showPan115Cloud = false },
                onDownloadStarted = onDownloadStarted
            )
            8 -> GuangYaCloudScreen(
                viewModel = guangyaCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showGuangYaCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            9 -> ILanzouCloudScreen(
                viewModel = ilanzouCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showILanzouCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            10 -> LanzouCloudScreen(
                viewModel = lanzouCloudViewModel,
                scrollBehavior = scrollBehavior,
                onExit = { showLanzouCloud = false },
                onDownloadStarted = onDownloadStarted
            )
            else -> {
                val scaffoldPadding = LocalScaffoldPadding.current
                val layoutDirection = LocalLayoutDirection.current
                PullToRefresh(
                    pullToRefreshState = rememberPullToRefreshState(),
                    isRefreshing = isRefreshing,
                    onRefresh = { driveQuotaViewModel.loadAll() },
                    contentPadding = scaffoldPadding,
                    topAppBarScrollBehavior = scrollBehavior,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
                            top = 16.dp + scaffoldPadding.calculateTopPadding(),
                            end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                            bottom = 16.dp + scaffoldPadding.calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            AccountSectionLabel("网盘账号")
                        }
                        item {
                            Text(
                                text = "登录后即可自动携带凭证解析与下载",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        // 空间信息加载失败提示：失败时卡片只是没有进度条，
                        // 用户无法区分「未登录」与「请求失败」，这里给出明确提示与重试入口
                        quotaError?.let { message ->
                            item(key = "quota_error") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.defaultColors(
                                        color = MiuixTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = message,
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onErrorContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(
                                            text = "重试",
                                            onClick = { driveQuotaViewModel.loadAll() },
                                            textStyle = MiuixTheme.textStyles.button.copy(
                                                color = MiuixTheme.colorScheme.onErrorContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        item(key = quark.id) {
                            DriveAccountCard(
                                account = quark,
                                quota = driveQuotaViewModel.quarkQuota.collectAsState().value,
                                onClick = if (quark.isLoggedIn) {
                                    { showCloud = true }
                                } else {
                                    onQuarkLogin
                                },
                                onMoreClick = if (quark.isLoggedIn) {
                                    { showQuarkSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = uc.id) {
                            DriveAccountCard(
                                account = uc,
                                quota = driveQuotaViewModel.ucQuota.collectAsState().value,
                                onClick = if (uc.isLoggedIn) {
                                    { showUCCloud = true }
                                } else {
                                    onUCLogin
                                },
                                onMoreClick = if (uc.isLoggedIn) {
                                    { showUCSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = xunlei.id) {
                            DriveAccountCard(
                                account = xunlei,
                                quota = driveQuotaViewModel.xunleiQuota.collectAsState().value,
                                onClick = if (xunlei.isLoggedIn) {
                                    { showXunleiCloud = true }
                                } else {
                                    onXunleiLogin
                                },
                                onMoreClick = if (xunlei.isLoggedIn) {
                                    { showXunleiSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = baidu.id) {
                            DriveAccountCard(
                                account = baidu,
                                quota = driveQuotaViewModel.baiduQuota.collectAsState().value,
                                onClick = if (baidu.isLoggedIn) {
                                    { showBaiduCloud = true }
                                } else {
                                    onBaiduLogin
                                },
                                onMoreClick = if (baidu.isLoggedIn) {
                                    { showBaiduSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = c139.id) {
                            DriveAccountCard(
                                account = c139,
                                quota = driveQuotaViewModel.c139Quota.collectAsState().value,
                                onClick = if (c139.isLoggedIn) {
                                    { showC139Cloud = true }
                                } else {
                                    onC139Login
                                },
                                onMoreClick = if (c139.isLoggedIn) {
                                    { showC139Sheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = pan123.id) {
                            DriveAccountCard(
                                account = pan123,
                                quota = driveQuotaViewModel.pan123Quota.collectAsState().value,
                                onClick = if (pan123.isLoggedIn) {
                                    { showPan123Cloud = true }
                                } else {
                                    onPan123Login
                                },
                                onMoreClick = if (pan123.isLoggedIn) {
                                    { showPan123Sheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = pan115.id) {
                            DriveAccountCard(
                                account = pan115,
                                quota = driveQuotaViewModel.pan115Quota.collectAsState().value,
                                onClick = if (pan115.isLoggedIn) {
                                    { showPan115Cloud = true }
                                } else {
                                    onPan115Login
                                },
                                onMoreClick = if (pan115.isLoggedIn) {
                                    { showPan115Sheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = guangya.id) {
                            DriveAccountCard(
                                account = guangya,
                                quota = driveQuotaViewModel.guangyaQuota.collectAsState().value,
                                onClick = if (guangya.isLoggedIn) {
                                    { showGuangYaCloud = true }
                                } else {
                                    onGuangYaLogin
                                },
                                onMoreClick = if (guangya.isLoggedIn) {
                                    { showGuangYaSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = ilanzou.id) {
                            DriveAccountCard(
                                account = ilanzou,
                                quota = driveQuotaViewModel.ilanzouQuota.collectAsState().value,
                                onClick = if (ilanzou.isLoggedIn) {
                                    { showILanzouCloud = true }
                                } else {
                                    onILanzouLogin
                                },
                                onMoreClick = if (ilanzou.isLoggedIn) {
                                    { showILanzouSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = lanzou.id) {
                            DriveAccountCard(
                                account = lanzou,
                                onClick = if (lanzou.isLoggedIn) {
                                    { showLanzouCloud = true }
                                } else {
                                    onLanzouLogin
                                },
                                onMoreClick = if (lanzou.isLoggedIn) {
                                    { showLanzouSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        item(key = github.id) {
                            // GitHub：已配置 Token 点击主体进入「我的主页」；未配置打开 Token 弹窗；更多按钮在登录后显示
                            DriveAccountCard(
                                account = github,
                                onClick = if (githubHasToken) onGitHubBrowseHome else onGitHubTokenClick,
                                onMoreClick = if (githubHasToken) {
                                    { showGitHubSheet = true }
                                } else {
                                    null
                                }
                            )
                        }
                        items(others, key = { it.id }) { account ->
                            DriveAccountCard(account = account)
                        }
                        // 视频解析：平台列表与登录态全部由 VideoAccountSection 自己呈现，这里只透传数据与回调
                        item(key = "video_section_label") {
                            AccountSectionLabel("视频解析")
                        }
                        item(key = "video_section") {
                            VideoAccountSection(
                                loginState = videoLoginState,
                                accounts = videoAccounts,
                                onLogin = onVideoLogin,
                                onManage = { showVideoSheet = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // 已登录夸克：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val quarkAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.QUARK).collectAsState()
    if (showQuarkSheet && quarkAccount != null) {
        QuarkAccountSheet(
            account = quarkAccount,
            accounts = quarkAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.QUARK, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.QUARK, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.QUARK, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.QUARK) },
            onDismiss = { showQuarkSheet = false }
        )
    }

    // 已登录 UC：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val ucAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.UC).collectAsState()
    if (showUCSheet && ucAccount != null) {
        UCAccountSheet(
            account = ucAccount,
            accounts = ucAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.UC, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.UC, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.UC, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.UC) },
            onDismiss = { showUCSheet = false }
        )
    }

    // 已登录迅雷：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val xunleiAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.XUNLEI).collectAsState()
    if (showXunleiSheet && xunleiAccount != null) {
        XunleiAccountSheet(
            account = xunleiAccount,
            accounts = xunleiAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.XUNLEI, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.XUNLEI, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.XUNLEI, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.XUNLEI) },
            onDismiss = { showXunleiSheet = false }
        )
    }

    // 已登录百度：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val baiduAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.BAIDU).collectAsState()
    if (showBaiduSheet && baiduAccount != null) {
        BaiduAccountSheet(
            account = baiduAccount,
            accounts = baiduAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.BAIDU, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.BAIDU, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.BAIDU, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.BAIDU) },
            onDismiss = { showBaiduSheet = false }
        )
    }

    // 已登录 139：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val c139Accounts by multiAccountViewModel.accountsOf(DownloadPlatform.C139).collectAsState()
    if (showC139Sheet && c139Account != null) {
        C139AccountSheet(
            account = c139Account,
            accounts = c139Accounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.C139, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.C139, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.C139, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.C139) },
            onDismiss = { showC139Sheet = false }
        )
    }

    // 已登录 123：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val pan123Accounts by multiAccountViewModel.accountsOf(DownloadPlatform.PAN123).collectAsState()
    if (showPan123Sheet && pan123Account != null) {
        Pan123AccountSheet(
            account = pan123Account,
            accounts = pan123Accounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.PAN123, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.PAN123, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.PAN123, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.PAN123) },
            onDismiss = { showPan123Sheet = false }
        )
    }

    // 已登录 115：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val pan115Accounts by multiAccountViewModel.accountsOf(DownloadPlatform.PAN115).collectAsState()
    if (showPan115Sheet && pan115Account != null) {
        Pan115AccountSheet(
            account = pan115Account,
            accounts = pan115Accounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.PAN115, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.PAN115, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.PAN115, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.PAN115) },
            onDismiss = { showPan115Sheet = false }
        )
    }

    // 已登录光鸭：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val guangyaAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.GUANGYA).collectAsState()
    if (showGuangYaSheet && guangyaAccount != null) {
        GuangYaAccountSheet(
            account = guangyaAccount,
            accounts = guangyaAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.GUANGYA, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.GUANGYA, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.GUANGYA, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.GUANGYA) },
            onDismiss = { showGuangYaSheet = false }
        )
    }

    // 已登录蓝奏优享：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val ilanzouAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.ILANZOU).collectAsState()
    if (showILanzouSheet && ilanzouAccount != null) {
        ILanzouAccountSheet(
            account = ilanzouAccount,
            accounts = ilanzouAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.ILANZOU, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.ILANZOU, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.ILANZOU, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.ILANZOU) },
            onDismiss = { showILanzouSheet = false }
        )
    }

    // 已登录蓝奏云：点击卡片弹出账号信息底部弹窗（含多账号管理）
    val lanzouAccounts by multiAccountViewModel.accountsOf(DownloadPlatform.LANZOU).collectAsState()
    if (showLanzouSheet && lanzouAccount != null) {
        LanzouAccountSheet(
            account = lanzouAccount,
            accounts = lanzouAccounts,
            onSetPrimary = { multiAccountViewModel.setPrimary(DownloadPlatform.LANZOU, it) },
            onRename = { id, remark -> multiAccountViewModel.rename(DownloadPlatform.LANZOU, id, remark) },
            onRemove = { multiAccountViewModel.remove(DownloadPlatform.LANZOU, it) },
            onAddAccount = { onAddAccount(DownloadPlatform.LANZOU) },
            onDismiss = { showLanzouSheet = false }
        )
    }

    // 视频平台账号弹窗：需要登录且已登录的平台点「管理」打开
    showVideoSheet?.let { platform ->
        VideoAccountSheet(
            platform = platform,
            accounts = videoAccounts.filter { it.platform == platform.name },
            onSetPrimary = { onVideoSetPrimary(platform, it) },
            onRename = onVideoRename,
            onRemove = onVideoRemove,
            onRemoveAll = { onVideoRemoveAll(platform) },
            onAddAccount = { onVideoAddAccount(platform) },
            onDismiss = { showVideoSheet = null }
        )
    }

    // GitHub 卡片「更多」菜单：浏览我的主页 / 配置 Token / 清除 Token（清除由上层做二次确认）
    if (showGitHubSheet) {
        OverlayBottomSheet(
            show = true,
            onDismissRequest = { showGitHubSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
            ) {
                Text(
                    text = "GitHub",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 浏览我的主页
                TextButton(
                    text = "浏览我的主页",
                    onClick = {
                        showGitHubSheet = false
                        onGitHubBrowseHome()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 配置 Token
                TextButton(
                    text = "配置 Token",
                    onClick = {
                        showGitHubSheet = false
                        onGitHubTokenClick()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // 清除 Token
                TextButton(
                    text = "清除 Token",
                    onClick = {
                        showGitHubSheet = false
                        onGitHubClearToken()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MiuixTheme.textStyles.button.copy(color = MiuixTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
private fun DriveAccountCard(
    account: DriveAccount,
    /** 网盘空间详情（已登录且有数据时在卡片内显示进度条）；null 不显示 */
    quota: QuotaInfo? = null,
    onClick: (() -> Unit)? = null,
    /** 已登录时右侧「三个点」更多按钮（打开账号弹窗）；null 则不显示 */
    onMoreClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        DriveAccountCardContent(
            account = account,
            quota = quota,
            clickable = onClick != null,
            onMoreClick = onMoreClick
        )
    }

    if (onClick != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) { content() }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) { content() }
    }
}

@Composable
private fun DriveAccountCardContent(
    account: DriveAccount,
    quota: QuotaInfo? = null,
    clickable: Boolean,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 品牌图标：按 Logo 自身形状原样画（不裁圆、不垫底色）——这些图标有的是带底色的圆角
        // 方块、有的是透明底图形，统一裁形会裁掉前者的边、给后者糊一圈假底色。
        // 未登录整体压暗，替代原来靠圆形底色区分登录态的做法。
        // 尺寸 40dp = 同一页里视频平台图标的大小（见 VideoAccountSection），两组图标在同一页里保持一致。
        val logoRes = drivePlatformLogoRes(account.id)
        if (logoRes != null) {
            Image(
                painter = painterResource(logoRes),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (account.isLoggedIn) 1f else 0.45f),
                contentScale = ContentScale.Fit
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (account.isLoggedIn) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = account.avatarText,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = if (account.isLoggedIn) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = account.name,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            AnimatedVisibility(
                visible = account.isLoggedIn && quota != null,
                enter = fadeIn(tween(300)) + expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(300)
                ),
                exit = fadeOut(tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    quota?.let { QuotaInlineBar(it) }
                }
            }
        }

        when {
            account.isLoggedIn && onMoreClick != null -> IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "更多",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
            account.isLoggedIn -> LoginBadge(isLoggedIn = true)
            clickable -> Text(
                text = "去登录",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            else -> LoginBadge(isLoggedIn = false)
        }
    }
}

/** 网盘卡片内空间进度条：已用 / 总容量 + 细进度条 */
@Composable
private fun QuotaInlineBar(quota: QuotaInfo) {
    val ratio = if (quota.total > 0) {
        (quota.used.toFloat() / quota.total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column {
        Text(
            text = "已用 ${formatBytes(quota.used)} / ${formatBytes(quota.total)}",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = ratio,
            modifier = Modifier.fillMaxWidth(),
            height = 4.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

@Composable
private fun LoginBadge(isLoggedIn: Boolean) {
    val (label, color) = if (isLoggedIn) {
        "已登录" to MiuixTheme.colorScheme.primary
    } else {
        "未登录" to MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 字节数格式化：B / KB / MB / GB / TB */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${bytes} B"
    else String.format("%.1f %s", value, units[unit])
}
