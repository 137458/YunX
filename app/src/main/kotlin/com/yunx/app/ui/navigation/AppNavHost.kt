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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yunx.app.R
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.MainScreen
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.login.BaiduLoginScreen
import com.yunx.app.ui.login.C139LoginScreen
import com.yunx.app.ui.login.GuangYaLoginScreen
import com.yunx.app.ui.login.ILanzouLoginScreen
import com.yunx.app.ui.login.LanzouLoginScreen
import com.yunx.app.ui.login.Pan115LoginScreen
import com.yunx.app.ui.login.Pan123LoginScreen
import com.yunx.app.ui.login.QuarkLoginScreen
import com.yunx.app.ui.login.UCLoginScreen
import com.yunx.app.ui.login.VideoLoginScreen
import com.yunx.app.ui.login.XunleiLoginScreen
import com.yunx.app.ui.login.XunleiVerifyWebViewScreen
import com.yunx.app.ui.login.XunleiWebLoginScreen
import com.yunx.app.ui.preview.MediaPreviewScreen
import com.yunx.app.ui.preview.PreviewLauncher
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.screens.AboutScreen
import com.yunx.app.ui.screens.AnnouncementScreen
import com.yunx.app.ui.screens.BookmarkScreen
import com.yunx.app.ui.screens.LanguageScreen
import com.yunx.app.ui.screens.LinkOpenScreen
import com.yunx.app.ui.screens.MultiAccountDownloadScreen
import com.yunx.app.ui.screens.OnboardingScreen
import com.yunx.app.ui.screens.SupportScreen
import com.yunx.app.ui.screens.ThemeScreen
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.BOTTOM_BAR_CONTENT_PADDING_DP
import com.yunx.app.ui.theme.isWideWindow
import com.yunx.app.ui.theme.shouldShowBottomBar
import com.yunx.app.ui.viewmodel.AnnouncementViewModel
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel
import com.yunx.app.ui.viewmodel.BookmarkViewModel
import com.yunx.app.ui.viewmodel.C139AccountViewModel
import com.yunx.app.ui.viewmodel.GuangYaAccountViewModel
import com.yunx.app.ui.viewmodel.ILanzouAccountViewModel
import com.yunx.app.ui.viewmodel.LanzouAccountViewModel
import com.yunx.app.ui.viewmodel.Pan115AccountViewModel
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import com.yunx.app.ui.viewmodel.QuarkAccountViewModel
import com.yunx.app.ui.viewmodel.UCAccountViewModel
import com.yunx.app.ui.viewmodel.VideoAccountViewModel
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel
import com.yunx.app.data.announcement.AnnouncementReadStore
import com.yunx.app.util.AppStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context) }
    // 应用级依赖图：主框架与登录等 destination 共享同一批仓库与下载管理器
    val graph = remember(context) { AppGraph(context) }

    // Android 9- 写公共 Download 需要 WRITE_EXTERNAL_STORAGE 运行时授权：
    // 下载完成保存前由 DownloadManager.storagePermissionProvider 触发动态申请，授权后自动继续保存。
    // 接线放在根导航宿主（常驻组合），主框架被覆盖页盖住时下载完成也能正常申请。
    var pendingStoragePermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingStoragePermission?.complete(granted)
        pendingStoragePermission = null
    }
    graph.downloadManager.storagePermissionProvider = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true // Android 10+ MediaStore 无需存储权限
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            val deferred = CompletableDeferred<Boolean>()
            pendingStoragePermission = deferred
            withContext(Dispatchers.Main) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            deferred.await()
        }
    }
    // 首次启动直接以引导页作为图起点，避免先组合主框架再跳转造成一帧闪烁；
    // 引导完成后的「清空整栈再进主页」见 OnboardingRoute 的 onFinish。
    val startDestination: AppRoute = remember(settings) {
        if (settings.onboardingShown) MainRoute else OnboardingRoute
    }
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
            NavHost(navController = navController, startDestination = startDestination) {
                composable<OnboardingRoute>(
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    OnboardingScreen(
                        onFinish = {
                            settings.onboardingShown = true
                            // 引导可能作为图起点（首次启动），也可能叠在「关于页预览」之上；
                            // 两种栈形态都清空，再落到主页，返回不会退回引导
                            navController.navigate(MainRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        }
                    )
                }

                // 主框架是常驻底页：自身不参与覆盖层过渡，否则每次进出覆盖页底页都会闪一下
                composable<MainRoute>(
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    MainScreen(
                        navController = navController,
                        graph = graph,
                        openTab = openTab,
                        onOpenTabConsumed = onOpenTabConsumed,
                        incomingLink = incomingLink,
                        onIncomingLinkConsumed = onIncomingLinkConsumed,
                        incomingAddUrl = incomingAddUrl,
                        onIncomingAddUrlConsumed = onIncomingAddUrlConsumed
                    )
                }

                // 网盘账号登录页：ViewModel 取自主框架的 NavBackStackEntry（与账号页共用同一实例），
                // 返回一律走 NavHost 的 pop，页面自身不再注册 BackHandler，预测性返回得以保留。
                composable<AccountLoginRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) { entry ->
                    AccountLoginDestination(
                        navController = navController,
                        graph = graph,
                        route = entry.toRoute()
                    )
                }

                composable<XunleiVerifyRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) { entry ->
                    val route = entry.toRoute<XunleiVerifyRoute>()
                    val mainEntry = remember { navController.getBackStackEntry<MainRoute>() }
                    val vm: XunleiAccountViewModel = viewModel(
                        viewModelStoreOwner = mainEntry,
                        factory = XunleiAccountViewModel.Factory(graph.xunleiRepository)
                    )
                    XunleiVerifyWebViewScreen(
                        verifyUrl = route.verifyUrl,
                        deviceId = route.deviceId,
                        onResult = { success, _ ->
                            navController.popBackStack()
                            if (success) {
                                // 设备已验证受信任：自动重试密码登录（应直接成功并关闭登录页）
                                SnackbarController.show(AppStrings.get(R.string.vm_main_verify_done_logging_in))
                                vm.retryLoginAfterVerify()
                            } else {
                                SnackbarController.show(AppStrings.get(R.string.vm_main_verify_incomplete))
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<VideoLoginRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) { entry ->
                    val route = entry.toRoute<VideoLoginRoute>()
                    val platform = VideoPlatform.entries.firstOrNull { it.name == route.platformName }
                    if (platform == null) {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        val mainEntry = remember { navController.getBackStackEntry<MainRoute>() }
                        val vm: VideoAccountViewModel = viewModel(
                            viewModelStoreOwner = mainEntry,
                            factory = VideoAccountViewModel.Factory(context)
                        )
                        VideoLoginScreen(
                            platform = platform,
                            viewModel = vm,
                            newAccount = route.newAccount,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() }
                        )
                    }
                }

                composable<BookmarkRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    val mainEntry = remember { navController.getBackStackEntry<MainRoute>() }
                    val vm: BookmarkViewModel = viewModel(
                        viewModelStoreOwner = mainEntry,
                        factory = BookmarkViewModel.Factory(graph.db.bookmarkDao())
                    )
                    BookmarkScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onResolve = { link, pwd ->
                            PendingResolve.request(link, pwd)
                            navController.popBackStack()
                        }
                    )
                }

                composable<AnnouncementRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) { entry ->
                    val route = entry.toRoute<AnnouncementRoute>()
                    val mainEntry = remember { navController.getBackStackEntry<MainRoute>() }
                    val vm: AnnouncementViewModel = viewModel(
                        viewModelStoreOwner = mainEntry,
                        factory = AnnouncementViewModel.Factory(remember { AnnouncementReadStore(context) })
                    )
                    AnnouncementScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        initialDetailId = route.initialDetailId
                    )
                }

                composable<PreviewRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    val request by PreviewLauncher.request.collectAsState()
                    if (request == null) {
                        // 预览请求是进程内事件，进程重建后不会恢复：栈顶只剩路由时直接回退
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    } else {
                        MediaPreviewScreen(
                            request = request!!,
                            onClose = {
                                PreviewLauncher.dismiss()
                                navController.popBackStack()
                            }
                        )
                    }
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

                composable<AboutRoute>(
                    enterTransition = OverlayEnter,
                    popExitTransition = OverlayExit
                ) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onPreviewOnboarding = {
                            settings.onboardingShown = false
                            navController.navigate(OnboardingRoute) {
                                // 与迁移前一致：预览引导时先关掉关于页
                                popUpTo(AboutRoute) { inclusive = true }
                            }
                        }
                    )
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

/**
 * 网盘账号登录页 destination：按平台分派到对应登录界面。
 *
 * ViewModel 统一取自主框架的 NavBackStackEntry，与账号页/网盘页共享同一实例，
 * 登录成功后主框架里的账号状态会立即刷新。
 */
@Composable
private fun AccountLoginDestination(
    navController: NavHostController,
    graph: AppGraph,
    route: AccountLoginRoute
) {
    val mainEntry = remember { navController.getBackStackEntry<MainRoute>() }
    val pop: () -> Unit = { navController.popBackStack() }
    val platform = remember(route.platform) {
        AccountLoginPlatform.entries.firstOrNull { it.name == route.platform }
    }
    if (platform == null) {
        LaunchedEffect(Unit) { pop() }
        return
    }
    when (platform) {
        AccountLoginPlatform.QUARK -> {
            val vm: QuarkAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = QuarkAccountViewModel.Factory(graph.quarkRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            QuarkLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.UC -> {
            val vm: UCAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = UCAccountViewModel.Factory(graph.ucRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            UCLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.XUNLEI -> {
            val vm: XunleiAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = XunleiAccountViewModel.Factory(graph.xunleiRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            XunleiLoginScreen(
                viewModel = vm,
                onBack = pop,
                onSaved = pop,
                onVerify = { url, deviceId ->
                    // 应用内验证：压到登录页之上，返回即回到登录页
                    navController.navigate(XunleiVerifyRoute(url, deviceId))
                }
            )
        }

        AccountLoginPlatform.XUNLEI_WEB -> {
            val vm: XunleiAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = XunleiAccountViewModel.Factory(graph.xunleiRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            XunleiWebLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.BAIDU -> {
            val vm: BaiduAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = BaiduAccountViewModel.Factory(graph.baiduRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            BaiduLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.C139 -> {
            val vm: C139AccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = C139AccountViewModel.Factory(graph.c139Repository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            C139LoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.PAN123 -> {
            val vm: Pan123AccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = Pan123AccountViewModel.Factory(graph.pan123Repository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            Pan123LoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.PAN115 -> {
            val vm: Pan115AccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = Pan115AccountViewModel.Factory(graph.pan115Repository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            Pan115LoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.GUANGYA -> {
            val vm: GuangYaAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = GuangYaAccountViewModel.Factory(graph.guangyaRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            GuangYaLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.ILANZOU -> {
            val vm: ILanzouAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = ILanzouAccountViewModel.Factory(graph.ilanzouRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            ILanzouLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }

        AccountLoginPlatform.LANZOU -> {
            val vm: LanzouAccountViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = LanzouAccountViewModel.Factory(graph.lanzouRepository)
            )
            AddAccountCleanup(route.addAccount, vm::beginAddAccount, vm::cancelAddAccount)
            LanzouLoginScreen(viewModel = vm, onBack = pop, onSaved = pop)
        }
    }
}

/**
 * 「添加账号」标记的生命周期：进入登录页时打上，离开（含预测性返回手势 pop）时清掉。
 * 旋转重建会先 dispose 再重新 begin，标记不会丢。
 */
@Composable
private fun AddAccountCleanup(enabled: Boolean, begin: () -> Unit, cancel: () -> Unit) {
    LaunchedEffect(enabled) { if (enabled) begin() }
    DisposableEffect(Unit) { onDispose { cancel() } }
}
