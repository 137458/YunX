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

package com.yunx.app.ui

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import com.yunx.app.data.announcement.AnnouncementReadStore
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.ChunkDownloader
import com.yunx.app.data.download.DownloadManager
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.C139DeviceInfo
import com.yunx.app.data.network.GitHubApi
import com.yunx.app.data.network.GitHubLinkParser
import com.yunx.app.data.network.GitHubTokenStore
import com.yunx.app.data.network.GuangYaApi
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.ILanzouApi
import com.yunx.app.data.network.LanzouApi
import com.yunx.app.data.network.Pan115Api
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.TokenCheck
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.repository.AccountDirectory
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.GuangYaAccountRepository
import com.yunx.app.data.repository.GuangYaResolveRepository
import com.yunx.app.data.repository.ILanzouAccountRepository
import com.yunx.app.data.repository.ILanzouResolveRepository
import com.yunx.app.data.repository.LanzouAccountRepository
import com.yunx.app.data.repository.LanzouResolveRepository
import com.yunx.app.data.repository.Pan115AccountRepository
import com.yunx.app.data.repository.Pan115ResolveRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.VideoAccountRepository
import com.yunx.app.data.repository.VideoParseRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.repository.XunleiResolveRepository
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.R
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.components.AppBottomBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.liquid.BottomBarStyle
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.components.rememberBlurBackdrop
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
import com.yunx.app.ui.navigation.AboutRoute
import com.yunx.app.ui.navigation.AccountLoginPlatform
import com.yunx.app.ui.navigation.AccountLoginRoute
import com.yunx.app.ui.navigation.AnnouncementRoute
import com.yunx.app.ui.navigation.AppGraph
import com.yunx.app.ui.navigation.BookmarkRoute
import com.yunx.app.ui.navigation.LanguageRoute
import com.yunx.app.ui.navigation.LinkOpenRoute
import com.yunx.app.ui.navigation.MainTab
import com.yunx.app.ui.navigation.MultiAccountDownloadRoute
import com.yunx.app.ui.navigation.PendingResolve
import com.yunx.app.ui.navigation.PreviewRoute
import com.yunx.app.ui.navigation.SupportRoute
import com.yunx.app.ui.navigation.ThemeRoute
import com.yunx.app.ui.navigation.VideoLoginRoute
import com.yunx.app.ui.preview.MediaPreviewScreen
import com.yunx.app.ui.preview.PreviewLauncher
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.screens.AnnouncementPopupDialog
import com.yunx.app.ui.screens.AnnouncementScreen
import com.yunx.app.ui.screens.AnnouncementUnreadBadge
import com.yunx.app.ui.screens.BookmarkScreen
import com.yunx.app.ui.screens.DownloadScreen
import com.yunx.app.ui.screens.DriveScreen
import com.yunx.app.ui.screens.LanguageScreen
import com.yunx.app.ui.screens.LinkOpenScreen
import com.yunx.app.ui.screens.MultiAccountDownloadScreen
import com.yunx.app.ui.screens.rememberUpdateDialogState
import com.yunx.app.ui.screens.ResolveScreen
import com.yunx.app.ui.screens.SettingsScreen
import com.yunx.app.ui.screens.SupportScreen
import com.yunx.app.ui.screens.ThemeScreen
import com.yunx.app.ui.screens.UpdateDialog
import com.yunx.app.ui.screens.UpdateDialogState
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.BOTTOM_BAR_CONTENT_PADDING_DP
import com.yunx.app.ui.theme.isWideWindow
import com.yunx.app.ui.theme.shouldShowBottomBar
import com.yunx.app.ui.theme.shouldShowNavigationRail
import com.yunx.app.ui.util.installApk
import com.yunx.app.ui.viewmodel.AnnouncementViewModel
import com.yunx.app.ui.viewmodel.BaiduAccountViewModel
import com.yunx.app.ui.viewmodel.BaiduCloudViewModel
import com.yunx.app.ui.viewmodel.BookmarkViewModel
import com.yunx.app.ui.viewmodel.C139AccountViewModel
import com.yunx.app.ui.viewmodel.C139CloudViewModel
import com.yunx.app.ui.viewmodel.DownloadViewModel
import com.yunx.app.ui.viewmodel.DriveQuotaViewModel
import com.yunx.app.ui.viewmodel.GuangYaAccountViewModel
import com.yunx.app.ui.viewmodel.GuangYaCloudViewModel
import com.yunx.app.ui.viewmodel.ILanzouAccountViewModel
import com.yunx.app.ui.viewmodel.ILanzouCloudViewModel
import com.yunx.app.ui.viewmodel.LanzouAccountViewModel
import com.yunx.app.ui.viewmodel.LanzouCloudViewModel
import com.yunx.app.ui.viewmodel.MultiAccountViewModel
import com.yunx.app.ui.viewmodel.Pan115AccountViewModel
import com.yunx.app.ui.viewmodel.Pan115CloudViewModel
import com.yunx.app.ui.viewmodel.Pan123AccountViewModel
import com.yunx.app.ui.viewmodel.Pan123CloudViewModel
import com.yunx.app.ui.viewmodel.QuarkAccountViewModel
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import com.yunx.app.ui.viewmodel.ResolveViewModel
import com.yunx.app.ui.viewmodel.UCAccountViewModel
import com.yunx.app.ui.viewmodel.UCCoudViewModel
import com.yunx.app.ui.viewmodel.VideoAccountViewModel
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel
import com.yunx.app.ui.viewmodel.XunleiCloudViewModel
import com.yunx.app.util.AppLinks
import com.yunx.app.util.AppStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 主页框架（Miuix 架构）：
 * - 外层骨架仍是 Miuix Scaffold，但**只用它的 topBar 槽**：顶栏大标题要折叠、
 *   且全应用 100+ 处 Overlay 系弹窗（OverlayDialog / OverlayBottomSheet / OperationSheet）
 *   需要 Scaffold 的 Popup 图层当宿主，这两件事离不开它。
 * - 顶部大标题（TopAppBar），切换 Tab 时标题文字随 Tab 变化；
 * - 导航 Tab（解析 / 网盘 / 下载 / 设置）：**底部导航栏不占 Scaffold 任何槽位**，
 *   而是作为内容 Box 的兄弟节点 `align(BottomCenter)` 叠上去，inset 避让由
 *   [com.yunx.app.ui.components.AppBottomBar] 各自的实现负责。内容层挂 `blurBackdropSource`
 *   当采样源，悬浮档的液态玻璃才有东西可折射（采样方向是「内容 → 底栏」，所以内容必须先布局）。
 * - 侧边导航栏（NavigationRail）与底部导航栏**严格互斥**，判据见
 *   [com.yunx.app.ui.theme.shouldShowNavigationRail] / [com.yunx.app.ui.theme.shouldShowBottomBar]；
 * - 通过 SaveableStateHolder 保存各页面状态，切换 Tab 再切回来不会重置；
 * - 全屏覆盖层（关于页、主题页、登录页等）平滑过渡展示。
 */
@Composable
fun MainScreen(
    /** 根导航控制器：全屏页迁出为 NavHost destination 后，入口统一从这里 navigate */
    navController: NavHostController,
    /** 应用级依赖图（网络客户端 / 数据库 / 仓库 / 下载管理器），与各 destination 共享 */
    graph: AppGraph,
    /** 外部来源请求直接打开的 Tab（如点击实况通知 → 下载页）；null = 用默认「解析」页 */
    openTab: MainTab? = null,
    /** [openTab] 被消费后回调：调用方负责清空它，避免旋转/重组时重复切页 */
    onOpenTabConsumed: () -> Unit = {},
    /** 深链接 / 外部分享拿到的待解析文本（分享链接、中文口令、带链接的文案都行）；null = 没有 */
    incomingLink: String? = null,
    /** [incomingLink] 被解析页接手后回调（调用方清空，避免重组时重复解析） */
    onIncomingLinkConsumed: () -> Unit = {},
    /** 深链接拿到的直链 / 磁力：走下载页「添加下载任务」弹窗预填（磁力只有 Gopeed 引擎能下，不能走解析链路） */
    incomingAddUrl: String? = null,
    /** [incomingAddUrl] 被下载页接手后回调 */
    onIncomingAddUrlConsumed: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Resolve) }

    // 账号页顶栏「排序」：弹窗和网盘列表都在 DriveScreen，但入口按钮画在全局顶栏，
    // 所以开关状态提升到本层（弹窗内容由 DriveScreen 渲染）。
    var showDriveSort by rememberSaveable { mutableStateOf(false) }
    // 账号页是否正停在某个网盘的浏览子页：子页打开时排序按钮（它只属于账号列表）要让位
    var driveSubPageOpen by rememberSaveable { mutableStateOf(false) }

    // 实况通知等外部来源请求切 Tab：读一次即消费（清空由调用方做）
    LaunchedEffect(openTab) {
        if (openTab != null) {
            currentTab = openTab
            onOpenTabConsumed()
        }
    }
    var showGitHubTokenDialog by rememberSaveable { mutableStateOf(false) }
    var showGitHubClearConfirm by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 设置仓储：引导页/忽略版本号等 UI 状态也统一从这里读写（构造时会迁移旧的 yunx_prefs 值）
    // 由 AppGraph 统一持有：主框架在覆盖页（登录/收藏等）入栈时会离开组合，不能在本页 remember
    val settings = graph.settings
    // 底栏样式由「主题与外观」页写入，经 AppThemeManager 的内存态即时响应
    val bottomBarStyle = BottomBarStyle.fromPersisted(AppThemeManager.bottomBarStyle)
    val blurBackdrop = rememberBlurBackdrop()

    // 更新检测：请求 GitHub 最新 Release（仓库无 Release / 网络失败则不提示）
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    // 主框架在覆盖页入栈时会离开组合：启动期一次性动作要用 saveable 标记，返回主页时不重复触发
    var startupUpdateChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startupUpdateChecked) return@LaunchedEffect
        startupUpdateChecked = true
        val checkResult = UpdateChecker.fetchLatestRelease(
            includePrerelease = settings.acceptPrereleaseUpdate
        )
        if (checkResult !is UpdateChecker.CheckResult.Success) return@LaunchedEffect
        val release = checkResult.release
        val current = UpdateChecker.currentVersion(context)
        val ignored = settings.ignoredUpdateVersion
        if (UpdateChecker.compareVersions(release.tagName, current) > 0 &&
            release.tagName != ignored
        ) {
            pendingRelease = release
            showUpdateDialog = true
        }
    }
    // 网络客户端、数据库与账号仓库由 AppGraph 统一持有（登录页 destination 也要拿到同一实例）
    val api = graph.api
    val ucApi = graph.ucApi
    val xunleiApi = graph.xunleiApi
    val baiduApi = graph.baiduApi
    val c139Api = graph.c139Api
    val pan123Api = graph.pan123Api
    val pan115Api = graph.pan115Api
    val guangyaApi = graph.guangyaApi
    val ilanzouApi = graph.ilanzouApi
    val lanzouApi = graph.lanzouApi
    val db = graph.db

    // 应用内公告：列表接口有副作用（会更新服务端曝光计数），只在本页启动时检查一次，不轮询
    val announcementReadStore = remember { AnnouncementReadStore(context) }
    val announcementViewModel: AnnouncementViewModel = viewModel(
        factory = AnnouncementViewModel.Factory(announcementReadStore)
    )
    val unreadAnnouncementCount by announcementViewModel.unreadCount.collectAsState()
    val popupAnnouncement by announcementViewModel.popup.collectAsState()
    var startupAnnouncementChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startupAnnouncementChecked) return@LaunchedEffect
        startupAnnouncementChecked = true
        announcementViewModel.checkStartup()
    }

    // GitHub API：Token 存储在 GitHubTokenStore；被 GitHub 拒绝（401）时自动清除并回退匿名限额
    val githubApi = remember { GitHubApi(tokenProvider = { GitHubTokenStore.getToken(context) }) }
    var githubHasTokenState by remember { mutableStateOf(GitHubTokenStore.hasToken(context)) }
    LaunchedEffect(githubApi) {
        githubApi.onUnauthorized = {
            scope.launch {
                GitHubTokenStore.setToken(context, null)
                githubHasTokenState = false
                SnackbarController.show(AppStrings.get(R.string.vm_main_github_token_invalid_cleared))
            }
        }
    }
    val repository = graph.quarkRepository
    val ucRepository = graph.ucRepository
    val xunleiRepository = graph.xunleiRepository
    val baiduRepository = graph.baiduRepository
    val c139Repository = graph.c139Repository
    val pan123Repository = graph.pan123Repository
    val pan115Repository = graph.pan115Repository
    val guangyaRepository = graph.guangyaRepository
    val ilanzouRepository = graph.ilanzouRepository
    val lanzouRepository = graph.lanzouRepository
    // 认证备份：打包/恢复各平台凭证（网盘 + 视频解析平台）
    val backupManager = graph.backupManager
    // 下载管理器由 AppGraph 持有：覆盖页入栈时主框架离开组合，下载任务不能随之重建
    val downloadManager = graph.downloadManager
    // Android 9- 写公共 Download 的运行时授权由 AppNavHost 统一接线（主框架离开组合时仍有效）
    val viewModel: QuarkAccountViewModel = viewModel(
        factory = QuarkAccountViewModel.Factory(repository)
    )
    val ucViewModel: UCAccountViewModel = viewModel(
        factory = UCAccountViewModel.Factory(ucRepository)
    )
    val xunleiViewModel: XunleiAccountViewModel = viewModel(
        factory = XunleiAccountViewModel.Factory(xunleiRepository)
    )
    val baiduViewModel: BaiduAccountViewModel = viewModel(
        factory = BaiduAccountViewModel.Factory(baiduRepository)
    )
    val c139ViewModel: C139AccountViewModel = viewModel(
        factory = C139AccountViewModel.Factory(c139Repository)
    )
    val pan123ViewModel: Pan123AccountViewModel = viewModel(
        factory = Pan123AccountViewModel.Factory(pan123Repository)
    )
    val pan115ViewModel: Pan115AccountViewModel = viewModel(
        factory = Pan115AccountViewModel.Factory(pan115Repository)
    )
    val guangyaViewModel: GuangYaAccountViewModel = viewModel(
        factory = GuangYaAccountViewModel.Factory(guangyaRepository)
    )
    val ilanzouViewModel: ILanzouAccountViewModel = viewModel(
        factory = ILanzouAccountViewModel.Factory(ilanzouRepository)
    )
    val lanzouViewModel: LanzouAccountViewModel = viewModel(
        factory = LanzouAccountViewModel.Factory(lanzouRepository)
    )
    // 多账号：账号列表 / 设主账号 / 改备注 / 退出 的统一入口
    // （这四件事对 10 个平台同构，见 AccountDirectory；平台专属的凭证读写仍在各平台仓库里）
    val multiAccountViewModel: MultiAccountViewModel = viewModel(
        factory = MultiAccountViewModel.Factory(AccountDirectory(db))
    )
    // 视频解析账号：账号页「视频解析」区的登录态与账号管理（所有视频平台共用一张表、一个 Cookie）
    val videoAccountViewModel: VideoAccountViewModel = viewModel(
        factory = VideoAccountViewModel.Factory(context)
    )
    // 视频区需要的两份数据：各平台当前账号（决定「未登录 / 昵称 / 备注」）与全部账号（多账号计数、弹窗列表）
    val videoLoginState by videoAccountViewModel.loginState.collectAsState()
    val videoAccounts by videoAccountViewModel.accounts.collectAsState()
    // 各平台「账号是否已登录」流：云盘浏览 VM 在启动期（未登录）init 加载会残留「请先登录…」错误态，
    // 首次登录成功后由 VM 监听该流自动重载根目录（见各 XxxCloudViewModel init）
    val quarkLoginState = remember { repository.observeAccount().map { it != null } }
    val ucLoginState = remember { ucRepository.observeAccount().map { it != null } }
    val xunleiLoginState = remember { xunleiRepository.observeAccount().map { it != null } }
    val baiduLoginState = remember { baiduRepository.observeAccount().map { it != null } }
    val c139LoginState = remember { c139Repository.observeAccount().map { it != null } }
    val pan123LoginState = remember { pan123Repository.observeAccount().map { it != null } }
    val pan115LoginState = remember { pan115Repository.observeAccount().map { it != null } }
    val guangyaLoginState = remember { guangyaRepository.observeAccount().map { it != null } }
    val ilanzouLoginState = remember { ilanzouRepository.observeAccount().map { it != null } }
    val lanzouLoginState = remember { lanzouRepository.observeAccount().map { it != null } }
    // 夸克云盘浏览：作为网盘 Tab 内容展示（非全屏），cookie 从数据库读取（避免 StateFlow 初始值为空的竞态）；
    // 下载前经 getFreshCookie 惰性刷新 __puus（修复 AlistGo/alist#830 下载 412）
    val quarkCloudViewModel: QuarkCloudViewModel = viewModel(
        factory = QuarkCloudViewModel.Factory(
            api,
            { repository.getFreshCookie() },
            downloadManager,
            loginState = quarkLoginState
        )
    )
    // UC 网盘云盘浏览：点击已登录的 UC 卡片打开（cookie 从数据库读取）；
    // 取链前经 getFreshCookie 惰性刷新 __puus（与夸克同源，修复取链/直链过期失败）
    val ucCloudViewModel: UCCoudViewModel = viewModel(
        factory = UCCoudViewModel.Factory(
            ucApi,
            { ucRepository.getFreshCookie() },
            downloadManager,
            loginState = ucLoginState
        )
    )
    // 迅雷 token 自动刷新与网页 captcha 现换由 AppGraph 初始化时接线（与仓库实例同源）
    // 迅雷云盘浏览：点击已登录的迅雷卡片打开（access_token/设备指纹/captcha 从数据库读取）
    val xunleiCloudViewModel: XunleiCloudViewModel = viewModel(
        factory = XunleiCloudViewModel.Factory(
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            downloadManager,
            loginState = xunleiLoginState
        )
    )
    // 百度网盘云盘浏览：点击已登录的百度卡片打开（cookie 从数据库读取）
    val baiduCloudViewModel: BaiduCloudViewModel = viewModel(
        factory = BaiduCloudViewModel.Factory(
            baiduApi,
            { baiduRepository.getAccount()?.cookie },
            downloadManager,
            loginState = baiduLoginState
        )
    )
    // 139 网盘云盘浏览：点击已登录的 139 卡片打开（cookie 从数据库读取）
    val c139CloudViewModel: C139CloudViewModel = viewModel(
        factory = C139CloudViewModel.Factory(
            c139Api,
            { c139Repository.getAccount()?.cookie },
            downloadManager,
            loginState = c139LoginState
        )
    )
    // 123 云盘浏览：点击已登录的 123 卡片打开（token 从数据库读取）
    val pan123CloudViewModel: Pan123CloudViewModel = viewModel(
        factory = Pan123CloudViewModel.Factory(
            pan123Api,
            { pan123Repository.getAccount()?.accessToken },
            downloadManager,
            loginState = pan123LoginState
        )
    )
    // 115 云盘浏览：点击已登录的 115 卡片打开（整串登录 Cookie 从数据库读取）
    val pan115CloudViewModel: Pan115CloudViewModel = viewModel(
        factory = Pan115CloudViewModel.Factory(
            pan115Api,
            { pan115Repository.getAccount()?.cookie },
            downloadManager,
            loginState = pan115LoginState
        )
    )
    // 光鸭云盘浏览：点击已登录的光鸭卡片打开（access token / 设备标识从数据库读取）
    val guangyaCloudViewModel: GuangYaCloudViewModel = viewModel(
        factory = GuangYaCloudViewModel.Factory(
            guangyaApi,
            guangyaRepository,
            downloadManager,
            loginState = guangyaLoginState
        )
    )
    // 蓝奏云优享版浏览：点击已登录的蓝奏优享卡片打开（appToken + uuid 从数据库读取）
    val ilanzouCloudViewModel: ILanzouCloudViewModel = viewModel(
        factory = ILanzouCloudViewModel.Factory(
            ilanzouApi,
            ilanzouRepository,
            downloadManager,
            loginState = ilanzouLoginState
        )
    )
    // 蓝奏云浏览：点击已登录的蓝奏云卡片打开（Cookie 从数据库读取）
    val lanzouCloudViewModel: LanzouCloudViewModel = viewModel(
        factory = LanzouCloudViewModel.Factory(
            lanzouApi,
            lanzouRepository,
            downloadManager,
            loginState = lanzouLoginState
        )
    )
    // 网盘空间详情：网盘页顶部「空间总览」展示各平台容量使用
    val driveQuotaViewModel: DriveQuotaViewModel = viewModel(
        factory = DriveQuotaViewModel.Factory(
            api, { repository.getAccount()?.cookie },
            ucApi, { ucRepository.getAccount()?.cookie },
            xunleiApi,
            { xunleiRepository.getAccount()?.accessToken },
            { xunleiRepository.getAccount()?.deviceId },
            { xunleiRepository.getAccount()?.captchaToken },
            baiduApi, { baiduRepository.getAccount()?.cookie },
            c139Api, { c139Repository.getAccount()?.cookie },
            pan123Api, { pan123Repository.getAccount()?.accessToken },
            pan115Api, { pan115Repository.getAccount()?.cookie },
            guangyaApi, { guangyaRepository.getAccount() },
            ilanzouApi, { ilanzouRepository.getAccount() }
        )
    )
    val xunleiResolveRepository = remember {
        XunleiResolveRepository(
            api = xunleiApi,
            accountProvider = { xunleiRepository.getAccount()?.accessToken },
            deviceIdProvider = { xunleiRepository.getAccount()?.deviceId },
            captchaProvider = { xunleiRepository.getAccount()?.captchaToken },
            // token 过期（含导入恢复后旧 token 过期）自动用 refresh_token 刷新并持久化
            refreshProvider = {
                val acc = xunleiRepository.getAccount()
                if (acc == null || acc.refreshToken.isBlank()) null
                else xunleiApi.refreshToken(acc.refreshToken, acc.deviceId)?.also { (at, nrt) ->
                    xunleiRepository.updateTokens(at, nrt)
                }
            },
            // 多账号下载：按 access_token 反查该账号自己的设备指纹与 captcha —— 迅雷的请求
            // 必须「token + 设备号 + captcha」三件套同属一个账号，否则会被拒（见 identityFor）
            accountByTokenProvider = { token ->
                xunleiRepository.getAccounts().firstOrNull { it.accessToken == token }
            }
        )
    }
    val baiduResolveRepository = remember {
        BaiduResolveRepository(baiduApi)
    }
    val c139ResolveRepository = remember {
        C139ResolveRepository(c139Api)
    }
    val pan123ResolveRepository = remember {
        Pan123ResolveRepository(
            api = pan123Api,
            tokenProvider = { pan123Repository.getAccount()?.accessToken }
        )
    }
    val pan115ResolveRepository = remember {
        Pan115ResolveRepository(pan115Api)
    }
    val guangyaResolveRepository = remember {
        GuangYaResolveRepository(
            api = guangyaApi,
            tokenProvider = { guangyaRepository.ensureAccessToken() }
        )
    }
    val ilanzouResolveRepository = remember {
        ILanzouResolveRepository(ilanzouApi, ilanzouRepository)
    }
    val lanzouResolveRepository = remember {
        LanzouResolveRepository(lanzouApi)
    }
    // 视频解析仓库：账号表 + 各平台解析器（抖音/小红书/快手/微博/AcFun/B站/Pixiv）
    val videoParseRepository = remember {
        VideoParseRepository(VideoAccountRepository(db.videoAccountDao()))
    }
    val resolveViewModel: ResolveViewModel = viewModel(
        factory = ResolveViewModel.Factory(
            repository,
            QuarkResolveRepository(api),
            ucRepository,
            UCResolveRepository(ucApi),
            xunleiRepository,
            xunleiResolveRepository,
            baiduRepository,
            baiduResolveRepository,
            c139Repository,
            c139ResolveRepository,
            pan123Repository,
            pan123ResolveRepository,
            pan115Repository,
            pan115ResolveRepository,
            guangyaRepository,
            guangyaResolveRepository,
            ilanzouRepository,
            ilanzouResolveRepository,
            lanzouRepository,
            lanzouResolveRepository,
            downloadManager,
            db.bookmarkDao(),
            githubApi,
            noSaveDownloadProvider = { settings.quarkNoSaveDownload },
            multiAccountDownloadProvider = { settings.multiAccountDownload },
            multiSourceDownloadProvider = { settings.multiSourceActive() },
            videoParseRepository = videoParseRepository
        )
    )
    val downloadViewModel: DownloadViewModel = viewModel(
        factory = DownloadViewModel.Factory(downloadManager)
    )
    // 更新包下载任务：弹窗只消费 Idle / Downloading / Done / Failed 四态，任务 id 留在这一层
    var updateTaskId by remember { mutableStateOf<Long?>(null) }
    val updateState = rememberUpdateDialogState(
        tasks = downloadViewModel.tasks,
        stats = downloadViewModel.stats,
        taskId = updateTaskId,
        // 落盘后的 SHA-256 校验用当前弹窗这个版本的 asset digest；没有 digest 字段则内部跳过
        digest = pendingRelease?.apkAsset?.digest.orEmpty()
    )

    /** 打开更新弹窗：清掉上一次的下载任务绑定，避免旧进度直接回填到新弹窗 */
    fun showUpdateDialogFor(release: UpdateChecker.Release) {
        updateTaskId = null
        pendingRelease = release
        showUpdateDialog = true
    }

    /** 更新包加入内置下载引擎（mirror=true 走镜像站），任务 id 回填给弹窗订阅进度 */
    fun enqueueUpdateApk(release: UpdateChecker.Release, mirror: Boolean) {
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
        if (apk == null) {
            SnackbarController.show(AppStrings.get(R.string.vm_main_apk_not_found))
            return
        }
        val url = if (mirror) UpdateChecker.mirrorUrl(apk.downloadUrl) else apk.downloadUrl
        scope.launch { updateTaskId = downloadManager.enqueue(url = url, fileName = apk.name) }
    }

    /** 弹窗「取消下载」：删除任务与已下载分片，弹窗退回 Idle，可重新选择镜像或稍后再下 */
    fun cancelUpdateDownload() {
        updateTaskId?.let { downloadManager.remove(it, deleteLocal = true) }
        updateTaskId = null
    }

    /** 检查更新（设置页入口）：有新版才弹窗；preview=true 为隐藏调试菜单的强制预览 */
    fun checkForUpdate(preview: Boolean) {
        scope.launch {
            if (!preview) SnackbarController.show(AppStrings.get(R.string.vm_main_checking_update))
            val checkResult = runCatching {
                UpdateChecker.fetchLatestRelease(includePrerelease = settings.acceptPrereleaseUpdate)
            }.getOrNull()
            val release = (checkResult as? UpdateChecker.CheckResult.Success)?.release
            when {
                release == null && preview -> showUpdateDialogFor(
                    UpdateChecker.Release(
                        tagName = AppStrings.get(R.string.vm_update_preview_version),
                        body = AppStrings.get(R.string.vm_update_preview_body),
                        assets = emptyList(),
                        publishedAt = "",
                        htmlUrl = ""
                    )
                )
                release == null -> {
                    val reason = (checkResult as? UpdateChecker.CheckResult.Failure)?.reason
                    SnackbarController.show(
                        if (reason.isNullOrBlank()) AppStrings.get(R.string.vm_main_update_check_failed_network)
                        else AppStrings.get(R.string.vm_main_update_check_failed, reason)
                    )
                }
                preview -> showUpdateDialogFor(release)
                UpdateChecker.compareVersions(release.tagName, UpdateChecker.currentVersion(context)) > 0 ->
                    showUpdateDialogFor(release)
                else -> SnackbarController.show(AppStrings.get(R.string.vm_main_up_to_date))
            }
        }
    }
    val bookmarkViewModel: BookmarkViewModel = viewModel(
        factory = BookmarkViewModel.Factory(db.bookmarkDao())
    )
    val quarkAccount by viewModel.quarkAccount.collectAsState()
    val ucAccount by ucViewModel.ucAccount.collectAsState()
    val xunleiAccount by xunleiViewModel.xunleiAccount.collectAsState()
    val baiduAccount by baiduViewModel.baiduAccount.collectAsState()
    val c139Account by c139ViewModel.c139Account.collectAsState()
    val pan123Account by pan123ViewModel.pan123Account.collectAsState()
    val pan115Account by pan115ViewModel.pan115Account.collectAsState()
    val guangyaAccount by guangyaViewModel.guangyaAccount.collectAsState()
    val ilanzouAccount by ilanzouViewModel.ilanzouAccount.collectAsState()
    val lanzouAccount by lanzouViewModel.lanzouAccount.collectAsState()

    // 解析页发起下载后，自动切换到「下载」Tab
    LaunchedEffect(resolveViewModel.downloadStarted) {
        if (resolveViewModel.downloadStarted) {
            currentTab = MainTab.Download
            resolveViewModel.consumeDownloadStarted()
        }
    }


    // 登录页与在线预览页已迁为 NavHost destination（见 AppNavHost）；
    // 预览请求按序号去重：手势返回弹出预览页后请求仍保留，不能据此重复入栈
    val previewSeq by PreviewLauncher.seq.collectAsState()
    var handledPreviewSeq by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(previewSeq) {
        if (previewSeq != 0 && previewSeq != handledPreviewSeq) {
            handledPreviewSeq = previewSeq
            if (navController.currentDestination?.hasRoute<PreviewRoute>() != true) {
                navController.navigate(PreviewRoute)
            }
        }
    }
    // 收藏页「解析」：收藏页只发请求并回退，解析链路仍在主框架里（与迁移前一致）
    LaunchedEffect(Unit) {
        PendingResolve.request.collect { request ->
            if (request == null) return@collect
            currentTab = MainTab.Resolve
            // GitHub 收藏（仓库链接）走 GitHub 解析入口，与主页快捷方式一致
            val github = GitHubLinkParser.parse(request.link)
            if (github != null) {
                resolveViewModel.startGitHubResolve(github)
            } else {
                resolveViewModel.startResolve(request.link, request.pwd)
            }
            PendingResolve.consume()
        }
    }
    // 折叠标题状态提升到本层：跨页面共享，页面切换时折叠/展开状态保持不变
    val scrollBehavior = MiuixScrollBehavior()

    // 主框架与全屏覆盖层（关于页）放在同一 Box：覆盖层带过渡动画。
    // 用 BoxWithConstraints 而不是读 LocalConfiguration：后者给的是整块屏幕的宽高，
    // 多窗口 / 分屏 / 折叠态下与本应用实际拿到的窗口不一致，会把半屏误判成大屏。
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // 侧边栏与底栏严格互斥，判据是纯函数（真值表见 WindowLayoutTest）
    val isWideScreen = isWideWindow(maxWidth.value.toInt())
    val useFloatingBottomBar = bottomBarStyle.isFloating
    val showNavigationRail = shouldShowNavigationRail(isWideScreen, useFloatingBottomBar)
    val showBottomBar = shouldShowBottomBar(isWideScreen, useFloatingBottomBar)
    // 系统栏底部 inset：内容避让与提示条避让共用同一份，且与 AppFloatingBottomBar 外壳同源。
    // 底栏不占 Scaffold 槽位后没人替我们预留这段空间，两处都得显式加上。
    val bottomInset = WindowInsets.navigationBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    // 顶部大标题（竖屏 / 横屏共用）
    val topBarContent: @Composable () -> Unit = {
        TopAppBar(
            title = stringResource(currentTab.titleRes),
            largeTitle = stringResource(currentTab.titleRes),
            scrollBehavior = scrollBehavior,
            color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            actions = {
                // 公告入口：所有 Tab 都显示（收藏只在解析页出现，公告是全局入口）。
                // 从图标进 = 先看列表（清掉上次「查看详情」直接进详情的请求）
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        // 从图标进 = 先看列表（不带详情 id）
                        navController.navigate(AnnouncementRoute())
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Campaign,
                            contentDescription = if (unreadAnnouncementCount > 0) {
                                AppStrings.get(R.string.vm_main_announcements_unread, unreadAnnouncementCount)
                            } else {
                                AppStrings.get(R.string.vm_announcements_title)
                            }
                        )
                    }
                    if (unreadAnnouncementCount > 0) {
                        AnnouncementUnreadBadge(
                            count = unreadAnnouncementCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-6).dp, y = 2.dp)
                        )
                    }
                }
                // 解析页标题右上角：收藏网盘链接入口。
                // 用带文字的按钮而不是纯图标：收藏是低频入口，只画一个书签图标时
                // 用户既认不出图标含义，也不知道这里能进收藏夹。
                if (currentTab == MainTab.Resolve) {
                    TextButton(
                        text = AppStrings.get(R.string.rs_bookmark),
                        onClick = { navController.navigate(BookmarkRoute) }
                    )
                }
                // 账号页标题右上角：网盘列表排序 / 隐藏未登录。
                // ★ 只在账号列表上显示：进了某个网盘的浏览子页时排序没有意义，
                //   由 DriveScreen 通过 onSubPageChange 通知本层隐藏。
                // 与上面「收藏」同样用带文字的按钮：排序是低频入口，纯图标认不出含义。
                if (currentTab == MainTab.Drive && !driveSubPageOpen) {
                    TextButton(
                        text = AppStrings.get(R.string.vm_main_sort),
                        onClick = { showDriveSort = true }
                    )
                }
            }
        )
    }

    // Tab 内容区（竖屏 / 横屏共用）：每个页面独立保存状态，切换 Tab 再切回来不丢失；带 Material3 过渡动画（按 Tab 顺序决定方向）
    val tabContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                // 根据 Tab 顺序决定滑动方向：向右切（新Tab在右边）→ 新页从右滑入；向左切反向
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 4 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 4 })
                }
            },
            label = "mainTab"
        ) { tab ->
            saveableStateHolder.SaveableStateProvider(tab) {
                when (tab) {
                    MainTab.Resolve -> ResolveScreen(
                        scrollBehavior,
                        resolveViewModel,
                        quarkCloudViewModel,
                        xunleiCloudViewModel,
                        baiduCloudViewModel,
                        c139CloudViewModel,
                        ucCloudViewModel,
                        pan123CloudViewModel,
                        pan115CloudViewModel,
                        guangyaCloudViewModel,
                        bookmarkViewModel = bookmarkViewModel,
                        onOpenBookmarks = { navController.navigate(BookmarkRoute) },
                        // 深链接 / 分享进来的链接：解析页填进输入框并自动发起解析
                        incomingLink = incomingLink,
                        onIncomingLinkConsumed = onIncomingLinkConsumed
                    )
                    MainTab.Drive -> DriveScreen(
                        scrollBehavior = scrollBehavior,
                        quarkAccount = quarkAccount,
                        ucAccount = ucAccount,
                        xunleiAccount = xunleiAccount,
                        baiduAccount = baiduAccount,
                        c139Account = c139Account,
                        pan123Account = pan123Account,
                        pan115Account = pan115Account,
                        guangyaAccount = guangyaAccount,
                        ilanzouAccount = ilanzouAccount,
                        lanzouAccount = lanzouAccount,
                        quarkCloudViewModel = quarkCloudViewModel,
                        ucCloudViewModel = ucCloudViewModel,
                        xunleiCloudViewModel = xunleiCloudViewModel,
                        baiduCloudViewModel = baiduCloudViewModel,
                        c139CloudViewModel = c139CloudViewModel,
                        pan123CloudViewModel = pan123CloudViewModel,
                        pan115CloudViewModel = pan115CloudViewModel,
                        guangyaCloudViewModel = guangyaCloudViewModel,
                        ilanzouCloudViewModel = ilanzouCloudViewModel,
                        lanzouCloudViewModel = lanzouCloudViewModel,
                        driveQuotaViewModel = driveQuotaViewModel,
                        onQuarkLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.QUARK.name)) },
                        // 「添加账号」：先给对应平台的账号 VM 打上「新增」标记，再打开它的登录页；
                        // 登录成功后这次登录会落成新的一行（见 resolveAccountSlot），
                        // 登录页关闭时会清掉标记，不会影响日后的普通重新登录
                        onAddAccount = { platform ->
                            // 先清 WebView 登录态：否则网页登录页会自动检测出旧账号又存一遍
                            // （见 MultiAccountViewModel.prepareAddAccount）
                            multiAccountViewModel.prepareAddAccount()
                            val target = when (platform) {
                                DownloadPlatform.QUARK -> AccountLoginPlatform.QUARK
                                DownloadPlatform.UC -> AccountLoginPlatform.UC
                                DownloadPlatform.XUNLEI -> AccountLoginPlatform.XUNLEI
                                DownloadPlatform.BAIDU -> AccountLoginPlatform.BAIDU
                                DownloadPlatform.C139 -> AccountLoginPlatform.C139
                                DownloadPlatform.PAN123 -> AccountLoginPlatform.PAN123
                                DownloadPlatform.PAN115 -> AccountLoginPlatform.PAN115
                                DownloadPlatform.GUANGYA -> AccountLoginPlatform.GUANGYA
                                DownloadPlatform.ILANZOU -> AccountLoginPlatform.ILANZOU
                                DownloadPlatform.LANZOU -> AccountLoginPlatform.LANZOU
                                else -> null
                            }
                            if (target != null) {
                                navController.navigate(AccountLoginRoute(target.name, addAccount = true))
                            }
                        },
                        multiAccountViewModel = multiAccountViewModel,
                        onDownloadStarted = { currentTab = MainTab.Download },
                        onUCLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.UC.name)) },
                        onXunleiLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.XUNLEI.name)) },
                        onBaiduLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.BAIDU.name)) },
                        onC139Login = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.C139.name)) },
                        onPan123Login = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.PAN123.name)) },
                        onPan115Login = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.PAN115.name)) },
                        onGuangYaLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.GUANGYA.name)) },
                        onILanzouLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.ILANZOU.name)) },
                        onLanzouLogin = { navController.navigate(AccountLoginRoute(AccountLoginPlatform.LANZOU.name)) },
                        githubHasToken = githubHasTokenState,
                        onGitHubTokenClick = { showGitHubTokenDialog = true },
                        // 已配置 Token 点卡片主体：用 GET /user 取 login，经统一解析入口进入该账号仓库列表
                        onGitHubBrowseHome = {
                            scope.launch {
                                val login = githubApi.getUserLogin()
                                if (!login.isNullOrBlank()) {
                                    resolveViewModel.startResolve("https://github.com/$login", "")
                                    currentTab = MainTab.Resolve
                                } else {
                                    // 取不到 login：Token 无效/已过期，或网络异常
                                    SnackbarController.show(AppStrings.get(R.string.vm_main_github_account_failed))
                                }
                            }
                        },
                        // 更多菜单「清除 Token」：先二次确认再清除
                        onGitHubClearToken = { showGitHubClearConfirm = true },
                        // 视频解析区：登录态 + 账号管理（弹窗里的操作全部落到 VideoAccountViewModel）
                        videoLoginState = videoLoginState,
                        videoAccounts = videoAccounts,
                        onVideoLogin = { platform ->
                            // 普通登录：覆盖当前账号那一行，不新增记录
                            navController.navigate(VideoLoginRoute(platform.name, newAccount = false))
                        },
                        onVideoAddAccount = { platform ->
                            // 先清一次 WebView 登录态：否则登录页会自动检测出旧账号，
                            // 「添加账号」加进来的还是同一个账号（与网盘的 prepareAddAccount 同因）
                            multiAccountViewModel.prepareAddAccount()
                            navController.navigate(VideoLoginRoute(platform.name, newAccount = true))
                        },
                        onVideoSetPrimary = { platform, id ->
                            videoAccountViewModel.setPrimary(platform, id)
                        },
                        onVideoRename = { id, remark ->
                            videoAccountViewModel.updateRemark(id, remark)
                        },
                        onVideoRemove = { id -> videoAccountViewModel.remove(id) },
                        onVideoRemoveAll = { platform ->
                            videoAccountViewModel.removeAll(platform)
                        },
                        // 顶栏「排序」入口：弹窗内容、顺序与隐藏未登录开关都由 DriveScreen 渲染
                        showSortDialog = showDriveSort,
                        onDismissSortDialog = { showDriveSort = false },
                        onSubPageChange = { driveSubPageOpen = it }
                    )
                    MainTab.Download -> DownloadScreen(
                        scrollBehavior,
                        downloadViewModel,
                        // 下载调试开关（开发调试菜单）：开启后下载页长按任务可进「调试信息」页
                        downloadDebug = AppThemeManager.downloadDebug,
                        // 深链接进来的直链 / 磁力：预填「添加下载任务」弹窗，由用户确认后入队
                        incomingAddUrl = incomingAddUrl,
                        onIncomingAddUrlConsumed = onIncomingAddUrlConsumed
                    )
                    MainTab.Settings -> SettingsScreen(
                        scrollBehavior = scrollBehavior,
                        onThemeClick = { navController.navigate(ThemeRoute) },
                        onLanguageClick = { navController.navigate(LanguageRoute) },
                        onAboutClick = { navController.navigate(AboutRoute) },
                        onSupportClick = { navController.navigate(SupportRoute) },
                        onLinkOpenClick = { navController.navigate(LinkOpenRoute) },
                        onMultiAccountDownloadClick = { navController.navigate(MultiAccountDownloadRoute) },
                        backupManager = backupManager,
                        // 检查更新统一由本页弹窗承载（含下载进度与安装入口）
                        onCheckUpdate = { checkForUpdate(preview = false) },
                        onPreviewUpdateDialog = { checkForUpdate(preview = true) }
                    )
                }
            }
        }
    }

    // 宽屏时侧边导航栏整高铺满、顶栏只覆盖右侧内容区；
    // 若把 Rail 塞进 Scaffold 内部，顶栏会横跨整宽而 Rail 挂在顶栏下方，平板上观感是「顶栏悬空」。
    Row(modifier = Modifier.fillMaxSize()) {
        if (showNavigationRail) {
            MainNavigationRail(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }

        // Scaffold 只保留 topBar 槽：底栏两种形态都改成下面 Box 的兄弟节点，
        // bottomBar / floatingToolbar 槽一律不用（理由见文件头 KDoc）。
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            topBar = {
                BlurredBar(
                    backdrop = blurBackdrop,
                    scrollBehavior = scrollBehavior,
                ) {
                    topBarContent()
                }
            },
        ) { innerPadding ->
            val layoutDirection = LocalLayoutDirection.current
            // 内容避让 = 系统栏 inset + 固定常量。常量而非实测栏高：避让只要够大不遮住末项即可，
            // 实测回传会多一轮测量-回传-重组，首帧还会闪一下。
            val effectiveScaffoldPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = if (showBottomBar) BOTTOM_BAR_CONTENT_PADDING_DP.dp + bottomInset else bottomInset
            )
            CompositionLocalProvider(LocalScaffoldPadding provides effectiveScaffoldPadding) {
                // 内容区占满内容栏（宽屏时右栏），内部列表消费 LocalScaffoldPadding 获得穿透滚动效果
                Box(modifier = Modifier.fillMaxSize()) {
                    // 内容层必须先于底栏布局并挂采样源：液态玻璃的折射采样方向是「内容 → 底栏」，
                    // 顺序反了底栏就没有可折射的图层，会退化成半透明纯色
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .blurBackdropSource(blurBackdrop)
                    ) {
                        tabContent()
                    }
                    // 底部导航栏：与内容同层的兄弟节点，不占 Scaffold 槽位，inset 避让由组件外壳自理
                    if (showBottomBar) {
                        AppBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            style = bottomBarStyle,
                            backdrop = blurBackdrop,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }

    // 收藏页与公告页已迁为 NavHost destination（见 AppNavHost）

    // 全局提示条宿主已上移到 AppNavHost 根层，任何 destination 都能显示提示

    }




    // 启动公告弹窗：展示未读的置顶公告（没有则最新未读）。两个出口都算已读，见 AnnouncementViewModel.consumePopup
    popupAnnouncement?.let { announcement ->
        AnnouncementPopupDialog(
            announcement = announcement,
            onDetail = {
                navController.navigate(AnnouncementRoute(announcement.id))
                announcementViewModel.consumePopup()
            },
            onDismiss = { announcementViewModel.consumePopup() }
        )
    }

    // GitHub Token 配置弹窗（网盘页入口）：Keystore 加密存储，输入用密码可见性切换
    // 注意：这个弹窗与更新检查无关，必须在 latestRelease 为 null 时也能弹出
    if (showGitHubTokenDialog) {
        var tokenInput by rememberSaveable { mutableStateOf(GitHubTokenStore.getToken(context) ?: "") }
        var passwordVisible by remember { mutableStateOf(false) }
        // 校验失败提示，修改输入即清除
        var tokenError by remember { mutableStateOf<String?>(null) }
        WindowDialog(
            show = true,
            title = "GitHub Token",
            onDismissRequest = { showGitHubTokenDialog = false }
        ) {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = AppStrings.get(R.string.vm_github_token_usage),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = AppStrings.get(R.string.vm_github_token_howto_title),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.get(R.string.vm_github_token_howto_body),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                // 上面四步是电脑上的翻页路径，手机上一路点下来很容易翻丢 —— 直接给一个直达入口，
                // 链接里已预填 public_repo 权限与说明，生成后复制回来粘到下方输入框即可。
                TextButton(
                    text = AppStrings.get(R.string.vm_github_get_token),
                    onClick = {
                        // 与设置页「GitHub 仓库」同一口径：没有可用浏览器就复制链接，不静默失败
                        if (!AppLinks.openGitHubTokenPage(context)) {
                            copyToClipboard(context, AppLinks.GITHUB_TOKEN_PAGE)
                            SnackbarController.show(AppStrings.get(R.string.vm_github_no_browser_link_copied))
                        }
                    }
                )
                Text(
                    text = AppStrings.get(R.string.vm_github_token_security_tip),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                TextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        tokenError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "Personal Access Token",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.Visibility
                                else Icons.Outlined.VisibilityOff,
                                contentDescription = if (passwordVisible) AppStrings.get(R.string.vm_password_hide) else AppStrings.get(R.string.vm_password_show)
                            )
                        }
                    }
                )
                tokenError?.let { message ->
                    Text(
                        text = message,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_cancel),
                    onClick = { showGitHubTokenDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val input = tokenInput.trim()
                        if (input.isBlank()) {
                            // 清空即清除 Token（无需联网校验）
                            GitHubTokenStore.setToken(context, null)
                            githubHasTokenState = false
                            showGitHubTokenDialog = false
                            SnackbarController.show(AppStrings.get(R.string.vm_github_token_cleared))
                        } else {
                            // 保存前先校验：无效 Token 会让 GitHub 拒绝之后的所有请求（含公开仓库解析）
                            scope.launch {
                                when (val check = githubApi.validateToken(input)) {
                                    is TokenCheck.Valid -> {
                                        // 保存失败（本机加密密钥暂时不可用）时留在弹窗里如实提示：
                                        // 写不进去却报「已保存」，用户会以为配好了、实际仍在走匿名限额
                                        if (GitHubTokenStore.setToken(context, input)) {
                                            githubHasTokenState = true
                                            showGitHubTokenDialog = false
                                            SnackbarController.show(AppStrings.get(R.string.vm_github_token_saved, check.login))
                                        } else {
                                            tokenError = AppStrings.get(R.string.vm_github_token_save_failed)
                                        }
                                    }
                                    TokenCheck.Invalid -> tokenError = AppStrings.get(R.string.vm_github_token_invalid)
                                    TokenCheck.Unknown -> tokenError = AppStrings.get(R.string.vm_github_token_check_failed)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.get(R.string.lg_save))
                }
            }

            // WindowDialog 是独立窗口，全局 Snackbar 宿主盖不住它 ⇒ 本弹窗自带一个（同 UpdateDialog）
            AppSnackbarHost(rememberGlobalSnackbarHostState())
        }
    }

    // 清除 GitHub Token 二次确认（网盘页更多菜单）
    if (showGitHubClearConfirm) {
        WindowDialog(
            show = true,
            title = AppStrings.get(R.string.vm_github_clear_token_title),
            onDismissRequest = { showGitHubClearConfirm = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = AppStrings.get(R.string.vm_github_clear_token_message),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        text = AppStrings.get(R.string.csa_cancel),
                        onClick = { showGitHubClearConfirm = false },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            GitHubTokenStore.setToken(context, null)
                            githubHasTokenState = false
                            showGitHubClearConfirm = false
                            SnackbarController.show(AppStrings.get(R.string.vm_github_token_cleared))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(AppStrings.get(R.string.vm_clear))
                    }
                }
            }
        }
    }

    // 发现新版本弹窗（覆盖在主页之上）：下载进度、安装入口都在弹窗内完成
    pendingRelease?.let { release ->
        if (showUpdateDialog) {
            UpdateDialog(
                currentVersion = UpdateChecker.currentVersion(context),
                release = release,
                download = updateState,
                onDownload = { enqueueUpdateApk(release, mirror = false) },
                onDownloadMirror = { enqueueUpdateApk(release, mirror = true) },
                // 弹窗只在 SHA-256 校验通过后才报 Done，拿不到 Done 就不会走到这里；
                // savePath 为空仍兜底提示，避免拉起空安装器
                onInstall = {
                    val path = (updateState as? UpdateDialogState.Done)?.savePath
                    if (path.isNullOrBlank()) {
                        SnackbarController.show(AppStrings.get(R.string.em_update_package_missing))
                    } else {
                        installApk(context, path)
                    }
                },
                onCancelDownload = { cancelUpdateDownload() },
                onOpenDownloadPage = {
                    showUpdateDialog = false
                    currentTab = MainTab.Download
                },
                // 发布页外链：Release 正文可能被 GitHub 截断，给用户一条看完整说明的出口。
                // html_url 缺失（如调试预览）时退回仓库 releases 索引，保证下载失败时
                // 浏览器兜底通道始终存在，而不是整条链接消失
                onOpenReleasePage = {
                    val url = release.releaseUrl.ifBlank {
                        "https://github.com/CYQawa/YunX/releases"
                    }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure { SnackbarController.show(AppStrings.get(R.string.em_cannot_open_browser)) }
                },
                onLater = { showUpdateDialog = false },
                onIgnore = {
                    settings.ignoredUpdateVersion = release.tagName
                    showUpdateDialog = false
                    // 忽略是不可见状态，给一次撤销机会，避免用户误点后再也等不到提示
                    SnackbarController.show(
                        message = AppStrings.get(R.string.vm_update_ignored_toast, release.tagName),
                        actionLabel = AppStrings.get(R.string.sh_undo),
                        onAction = { settings.ignoredUpdateVersion = "" }
                    )
                }
            )
        }

    }
}

/**
 * 复制文本到剪贴板（写剪贴板不受「自动识别剪贴板」开关约束，见 AGENTS.md §3.22）。
 *
 * 从上游 v1.3.1 接过：GitHub Token 弹窗在「没有可用浏览器」时要把令牌生成页链接给到用户，
 * 复制是打开浏览器失败后的唯一出口，所以不能静默失败。
 */
private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_link", text))
}

/**
 * 侧边导航栏（宽屏）：4 个主 Tab，使用 Miuix NavigationRail 与 NavigationRailItem。
 *
 * 按 miuix 官方要求传入 [rememberNavigationRailState] 启用可展开形态：
 * 顶部自带展开/收起开关，展开后条目切换为「图标 + 文字」横排并带选中高亮胶囊，
 * 收起态为经典紧凑布局；展开状态由官方 state 持有并跨配置变更保存。
 *
 * 整高铺满并自行消费系统栏 inset（defaultWindowInsetsPadding 默认开启），
 * 因此可以放在内容 Scaffold 之外，不再被顶栏压在下面。
 */
@Composable
private fun MainNavigationRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = rememberNavigationRailState()
    ) {
        MainTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = if (currentTab == tab) tab.selectedIcon else tab.unselectedIcon,
                label = stringResource(tab.titleRes)
            )
        }
    }
}
