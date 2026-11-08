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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.MainScreen
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.screens.LanguageScreen
import com.yunx.app.ui.screens.LinkOpenScreen
import com.yunx.app.ui.screens.MultiAccountDownloadScreen
import com.yunx.app.ui.screens.SupportScreen
import com.yunx.app.ui.screens.ThemeScreen
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.BOTTOM_BAR_CONTENT_PADDING_DP
import com.yunx.app.ui.theme.isWideWindow
import com.yunx.app.ui.theme.shouldShowBottomBar

/** 全屏页过渡：与迁移前覆盖层的「淡入 + 轻微缩放」保持一致。 */
private val OverlayEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)
}

/** 全屏页退出：返回（含预测性返回松手完成）时播放，与迁移前一致。 */
private val OverlayExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.96f)
}

/**
 * 应用根导航宿主。
 *
 * 返回语义：覆盖页入栈后，pop 动作由 NavHost 的 PredictiveBackHandler 驱动，
 * 系统能拿到手势进度并播放「目标页预览」动画；页面自身不再用 `BackHandler` 吞返回。
 *
 * 全局提示条宿主上移到本层：它不再随 [MainScreen] 的组合进出，覆盖页也走同一条提示队列
 * （队列的「最上层宿主显示」语义不受影响，页面自建宿主仍优先）。
 */
@Composable
fun AppNavHost(
    /** 外部来源请求直接打开的 Tab（如点击实况通知 → 下载页）；null = 默认「解析」页 */
    openTab: MainTab? = null,
    /** [openTab] 被消费后回调 */
    onOpenTabConsumed: () -> Unit = {},
    /** 深链接 / 外部分享拿到的待解析文本 */
    incomingLink: String? = null,
    /** [incomingLink] 被消费后回调 */
    onIncomingLinkConsumed: () -> Unit = {},
    /** 深链接拿到的直链 / 磁力 */
    incomingAddUrl: String? = null,
    /** [incomingAddUrl] 被消费后回调 */
    onIncomingAddUrlConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val snackbarHostState = rememberGlobalSnackbarHostState()
    val currentEntry by navController.currentBackStackEntryAsState()
    // 只有停在主框架时才有底栏，提示条要抬到它上方；覆盖页没有底栏，按普通页面避让
    val onMain = currentEntry?.destination?.hasRoute<MainRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showBottomBar = onMain &&
            shouldShowBottomBar(
                isWideWindow(maxWidth.value.toInt()),
                BottomBarStyle.fromPersisted(AppThemeManager.bottomBarStyle).isFloating
            )
        val bottomInset = WindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val snackbarBottomPadding = if (showBottomBar) {
            bottomInset + BOTTOM_BAR_CONTENT_PADDING_DP.dp + 8.dp
        } else {
            bottomInset + 16.dp
        }

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = MainRoute) {
                // 主框架是常驻底页：自身不参与覆盖层过渡，否则每次进出覆盖页底页都会闪一下
                composable<MainRoute>(
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    MainScreen(
                        navController = navController,
                        openTab = openTab,
                        onOpenTabConsumed = onOpenTabConsumed,
                        incomingLink = incomingLink,
                        onIncomingLinkConsumed = onIncomingLinkConsumed,
                        incomingAddUrl = incomingAddUrl,
                        onIncomingAddUrlConsumed = onIncomingAddUrlConsumed
                    )
                }

                composable<SupportRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    SupportScreen(onBack = { navController.popBackStack() })
                }

                composable<ThemeRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    ThemeScreen(onBack = { navController.popBackStack() })
                }

                composable<LanguageRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    LanguageScreen(onBack = { navController.popBackStack() })
                }

                composable<LinkOpenRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    LinkOpenScreen(onBack = { navController.popBackStack() })
                }

                composable<MultiAccountDownloadRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    MultiAccountDownloadScreen(onBack = { navController.popBackStack() })
                }
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = snackbarBottomPadding)
            )
        }
    }
}
