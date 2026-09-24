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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.BaiduAccountEntity
import com.yunx.app.data.db.C139AccountEntity
import com.yunx.app.data.db.Pan123AccountEntity
import com.yunx.app.data.db.QuarkAccountEntity
import com.yunx.app.data.db.UCAccountEntity
import com.yunx.app.data.db.XunleiAccountEntity
import com.yunx.app.data.network.model.QuotaInfo
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
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
 * 网盘页：
 * - 账号列表：展示 6 大网盘账号及空间概览；
 * - 已登录点击进入对应云盘浏览；未登录点击进入对应登录。
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
    /** 网盘空间详情 ViewModel（顶部空间总览） */
    driveQuotaViewModel: DriveQuotaViewModel,
    onQuarkLogin: () -> Unit,
    onQuarkLogout: () -> Unit,
    /** 夸克云盘下载入队后切换到「下载」Tab */
    onDownloadStarted: () -> Unit = {},
    onUCLogin: () -> Unit,
    onUCLogout: () -> Unit,
    onXunleiLogin: () -> Unit,
    onXunleiLogout: () -> Unit,
    onBaiduLogin: () -> Unit,
    onBaiduLogout: () -> Unit,
    onC139Login: () -> Unit,
    onC139Logout: () -> Unit,
    onPan123Login: () -> Unit,
    onPan123Logout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQuarkSheet by remember { mutableStateOf(false) }
    var showUCSheet by remember { mutableStateOf(false) }
    var showXunleiSheet by remember { mutableStateOf(false) }
    var showBaiduSheet by remember { mutableStateOf(false) }
    var showC139Sheet by remember { mutableStateOf(false) }
    var showPan123Sheet by remember { mutableStateOf(false) }
    // 6 大网盘云盘浏览：网盘 Tab 内切换（非全屏），切 Tab 再回来仍保留
    var showCloud by rememberSaveable { mutableStateOf(false) }
    var showUCCloud by rememberSaveable { mutableStateOf(false) }
    var showXunleiCloud by rememberSaveable { mutableStateOf(false) }
    var showBaiduCloud by rememberSaveable { mutableStateOf(false) }
    var showC139Cloud by rememberSaveable { mutableStateOf(false) }
    var showPan123Cloud by rememberSaveable { mutableStateOf(false) }

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
    val others = remember { emptyList<DriveAccount>() }

    // 进入网盘页加载空间详情（仅已登录平台）
    LaunchedEffect(Unit) {
        driveQuotaViewModel.loadAll()
    }
    val isRefreshing by driveQuotaViewModel.loading.collectAsState()
    // 必须在 Composable 上下文里收集：LazyColumn 的 LazyListScope lambda 不是 @Composable
    val quotaError by driveQuotaViewModel.quotaError.collectAsState()

    // 账号列表 ↔ 夸克云盘 ↔ UC 云盘 ↔ 迅雷云盘 ↔ 百度云盘 ↔ 139 云盘 ↔ 123 云盘：平滑过渡
    AnimatedContent(
        targetState = when {
            showCloud -> 1
            showUCCloud -> 2
            showXunleiCloud -> 3
            showBaiduCloud -> 4
            showC139Cloud -> 5
            showPan123Cloud -> 6
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
                        modifier = modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
                            top = 16.dp + scaffoldPadding.calculateTopPadding(),
                            end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                            bottom = 16.dp + scaffoldPadding.calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                        items(others, key = { it.id }) { account ->
                            DriveAccountCard(account = account)
                        }
                    }
                }
            }
        }
    }

    // 已登录夸克：点击卡片弹出账号信息底部弹窗
    if (showQuarkSheet && quarkAccount != null) {
        QuarkAccountSheet(
            account = quarkAccount,
            onLogout = {
                onQuarkLogout()
                showQuarkSheet = false
            },
            onDismiss = { showQuarkSheet = false }
        )
    }

    // 已登录 UC：点击卡片弹出账号信息底部弹窗
    if (showUCSheet && ucAccount != null) {
        UCAccountSheet(
            account = ucAccount,
            onLogout = {
                onUCLogout()
                showUCSheet = false
            },
            onDismiss = { showUCSheet = false }
        )
    }

    // 已登录迅雷：点击卡片弹出账号信息底部弹窗
    if (showXunleiSheet && xunleiAccount != null) {
        XunleiAccountSheet(
            account = xunleiAccount,
            onLogout = {
                onXunleiLogout()
                showXunleiSheet = false
            },
            onDismiss = { showXunleiSheet = false }
        )
    }

    // 已登录百度：点击卡片弹出账号信息底部弹窗
    if (showBaiduSheet && baiduAccount != null) {
        BaiduAccountSheet(
            account = baiduAccount,
            onLogout = {
                onBaiduLogout()
                showBaiduSheet = false
            },
            onDismiss = { showBaiduSheet = false }
        )
    }

    // 已登录 139：点击卡片弹出账号信息底部弹窗
    if (showC139Sheet && c139Account != null) {
        C139AccountSheet(
            account = c139Account,
            onLogout = {
                onC139Logout()
                showC139Sheet = false
            },
            onDismiss = { showC139Sheet = false }
        )
    }

    // 已登录 123：点击卡片弹出账号信息底部弹窗
    if (showPan123Sheet && pan123Account != null) {
        Pan123AccountSheet(
            account = pan123Account,
            onLogout = {
                onPan123Logout()
                showPan123Sheet = false
            },
            onDismiss = { showPan123Sheet = false }
        )
    }
}

@Composable
private fun DriveAccountCard(
    account: DriveAccount,
    quota: QuotaInfo? = null,
    onClick: (() -> Unit)? = null,
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
        // 品牌头像
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = if (account.isLoggedIn) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MiuixTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = account.avatarText,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = if (account.isLoggedIn) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions
                )
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
                backgroundColor = MiuixTheme.colorScheme.surfaceVariant
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