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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.backup.AuthCrypto
import com.yunx.app.data.download.DownloadDebugLog
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.download.DownloadSaver
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.R
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.LocaleController
import com.yunx.app.util.AppLinks
import com.yunx.app.util.AppStrings
import com.yunx.app.util.DiagnosticLog
import com.yunx.app.util.LogExporter
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 可选的下载线程数档位（最高 512） */
private val threadOptions = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512)

/** 卡片分组之间的统一间距（页面左右外边距统一为 16dp） */
private val CardGap = 12.dp

/** 按平台下载线程数设置项 */
private data class ThreadPlatform(val platform: String, val label: String)

private val threadPlatforms = listOf(
    ThreadPlatform(DownloadPlatform.GENERIC, AppStrings.get(R.string.settings_thread_platform_generic)),
    ThreadPlatform(DownloadPlatform.QUARK, AppStrings.get(R.string.csa_platform_quark)),
    ThreadPlatform(DownloadPlatform.UC, AppStrings.get(R.string.rs_platform_uc)),
    ThreadPlatform(DownloadPlatform.XUNLEI, AppStrings.get(R.string.csa_platform_xunlei)),
    ThreadPlatform(DownloadPlatform.BAIDU, AppStrings.get(R.string.csa_platform_baidu)),
    ThreadPlatform(DownloadPlatform.C139, AppStrings.get(R.string.rs_platform_139)),
    ThreadPlatform(DownloadPlatform.PAN123, AppStrings.get(R.string.vm_link_target_pan123)),
)

/** 跳转系统「应用通知」设置页（Android 8+ 通用入口；失败时退回应用详情页） */
private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
    }
}

/**
 * 设置页：下载策略 + 主题外观 + 检查更新 + 日志与认证备份。
 * 基于 Miuix 规范重构：Card 分组包裹，miuix-preference 驱动，SliderPreference 内嵌策略控制。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSupportClick: () -> Unit,
    // 本地已移除容器变换（AGENTS §3.15），这两个修饰符保留参数位以便调用方按上游签名传值；默认值等价于不传
    linkOpenRowModifier: Modifier = Modifier,
    multiAccountRowModifier: Modifier = Modifier,
    /** 打开「链接唤起」页（深链接注册项的逐个开关） */
    onLinkOpenClick: () -> Unit,
    /** 打开「多账号下载」页（两个实验性开关：多账号轮流取链 / 单文件多源分片） */
    onMultiAccountDownloadClick: () -> Unit,
    /** 打开「语言」页（界面语言单选列表） */
    onLanguageClick: () -> Unit,
    backupManager: AuthBackupManager,
    /** 检查更新：结果弹窗（含下载进度与安装入口）由 MainScreen 统一承载，这里只发请求 */
    onCheckUpdate: () -> Unit,
    /** 隐藏调试菜单：强制弹出更新弹窗（不看版本号，仅用于预览 UI） */
    onPreviewUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    // 认证信息导出弹窗（AES 加密 + 导出范围）
    var showExportAuthDialog by remember { mutableStateOf(false) }
    // 认证信息导入：加密文件内容（非空时弹解密密码框）
    var pendingImportContent by remember { mutableStateOf<String?>(null) }
    var showImportAuthDialog by remember { mutableStateOf(false) }
    // 导出/导入处理中（PBKDF2 21万次迭代派生密钥，偶发 1~3s，期间显示加载弹窗）
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    // 按平台线程数：二级弹窗当前选择的平台
    var selectedThreadPlatform by remember { mutableStateOf(threadPlatforms.first()) }
    var showPlatformThreadDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 下载保存目录（SAF）：本地状态驱动 UI 刷新，同时同步 SharedPreferences
    val settingsRepo = remember { SettingsRepository(context) }
    var clipboardSuggest by remember { mutableStateOf(settingsRepo.clipboardSuggestEnabled) }
    var acceptPrerelease by remember { mutableStateOf(settingsRepo.acceptPrereleaseUpdate) }
    var downloadDirUri by remember { mutableStateOf(settingsRepo.downloadDirUri) }
    var showDevMenu by remember { mutableStateOf(false) }
    // 诊断模式开关（开发调试菜单里）：本地状态驱动 UI，运行态在 DiagnosticLog 里（改完立刻生效）
    var diagnosticOn by remember { mutableStateOf(settingsRepo.diagnosticMode) }
    // 下载调试开关（开发调试菜单里）：开启后下载页长按任务可查看「调试信息」；关闭会清空日志，故需二次确认
    var downloadDebugOn by remember { mutableStateOf(settingsRepo.downloadDebug) }
    var showDisableDownloadDebugConfirm by remember { mutableStateOf(false) }
    // 全量 SHA-256 校验开关（开发调试菜单里）：默认关，开启后保存文件时顺带算整文件摘要写进调试日志
    var fullShaOn by remember { mutableStateOf(settingsRepo.fullFileSha256) }
    // 网络与下载策略（本地状态驱动 UI，同时同步 SharedPreferences）
    var maxConcurrent by remember { mutableStateOf(settingsRepo.maxConcurrentDownloads) }
    var speedLimitBps by remember { mutableStateOf(settingsRepo.downloadSpeedLimit) }
    var retryCount by remember { mutableStateOf(settingsRepo.downloadRetryCount) }
    // 用户体验与系统适配：锁屏保持下载 / 通知栏速度
    var keepLocked by remember { mutableStateOf(settingsRepo.keepDownloadWhenLocked) }
    var showSpeed by remember { mutableStateOf(settingsRepo.notificationShowSpeed) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    // 通知是否可用
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 通知权限（Android 13+）：未授权时点击「通知栏下载进度」先申请，授权后生效
    val notifyPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (granted) {
            showSpeed = true
            settingsRepo.notificationShowSpeed = true
        }
    }
    val dirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            settingsRepo.downloadDirUri = uri.toString()
            downloadDirUri = uri.toString()
            SnackbarController.show(AppStrings.get(R.string.snack_download_dir_updated))
        }
    }
    // 导入认证信息文件选择器：选择后先判断是否加密备份，加密则弹密码框
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                try {
                    val text = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                    if (text == null) {
                        SnackbarController.show(AppStrings.get(R.string.snack_read_file_failed))
                        return@launch
                    }
                    if (AuthCrypto.isEncrypted(text)) {
                        pendingImportContent = text
                        showImportAuthDialog = true
                    } else {
                        val count = runCatching {
                            withContext(Dispatchers.IO) { backupManager.importJson(text, context) }
                        }.getOrElse { e ->
                            SnackbarController.show(AppStrings.get(R.string.snack_import_failed, e.message ?: ""))
                            return@launch
                        }
                        SnackbarController.show(AppStrings.get(R.string.snack_auth_imported, count))
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val scaffoldPadding = LocalScaffoldPadding.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(
                top = scaffoldPadding.calculateTopPadding() + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp
            )
    ) {
        // ---------- 分类 1: 下载 ----------
        SmallTitle(text = AppStrings.get(R.string.csa_download))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = AppStrings.get(R.string.settings_download_threads),
                summary = AppStrings.get(R.string.settings_download_threads_desc_note),
                onClick = { showThreadsDialog = true }
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_download_dir),
                summary = downloadDirUri?.let { AppStrings.get(R.string.settings_download_dir_desc_custom, DownloadSaver.safDirDisplay(it)) }
                    ?: AppStrings.get(R.string.settings_download_dir_system_default),
                onClick = { dirLauncher.launch(null) }
            )
            if (downloadDirUri != null) {
                ArrowPreference(
                    title = AppStrings.get(R.string.settings_download_dir_reset),
                    summary = AppStrings.get(R.string.settings_download_dir_reset_desc),
                    onClick = {
                        downloadDirUri = null
                        settingsRepo.downloadDirUri = null
                        SnackbarController.show(AppStrings.get(R.string.snack_download_dir_reset))
                    }
                )
            }
            SliderPreference(
                value = maxConcurrent.toFloat(),
                // 拖动过程只更新本地状态，松手（onValueChangeFinished）才落盘，避免每帧写 SharedPreferences
                onValueChange = { maxConcurrent = it.roundToInt().coerceIn(1, 8) },
                onValueChangeFinished = { settingsRepo.maxConcurrentDownloads = maxConcurrent },
                valueRange = 1f..8f,
                steps = 6,
                title = AppStrings.get(R.string.settings_max_tasks),
                summary = AppStrings.get(R.string.settings_max_tasks_desc_bg, maxConcurrent),
                valueText = AppStrings.get(R.string.settings_value_task_count, maxConcurrent)
            )
            SliderPreference(
                value = (speedLimitBps / (1024 * 1024)).toFloat(),
                onValueChange = { mb -> speedLimitBps = mb.roundToLong() * 1024 * 1024 },
                onValueChangeFinished = { settingsRepo.downloadSpeedLimit = speedLimitBps },
                valueRange = 0f..30f,
                steps = 29,
                title = AppStrings.get(R.string.settings_speed_limit),
                summary = speedLimitText(speedLimitBps),
                valueText = if (speedLimitBps <= 0) AppStrings.get(R.string.option_speed_unlimited) else "${speedLimitBps / (1024 * 1024)} MB/s"
            )
            SliderPreference(
                value = retryCount.toFloat(),
                onValueChange = { retryCount = it.roundToInt().coerceIn(0, 10) },
                onValueChangeFinished = { settingsRepo.downloadRetryCount = retryCount },
                valueRange = 0f..10f,
                steps = 9,
                title = AppStrings.get(R.string.settings_auto_retry),
                summary = if (retryCount == 0) AppStrings.get(R.string.settings_auto_retry_desc_never) else AppStrings.get(R.string.settings_auto_retry_desc, retryCount),
                valueText = if (retryCount == 0) AppStrings.get(R.string.settings_auto_retry_value_never) else AppStrings.get(R.string.settings_auto_retry_value_times, retryCount)
            )
            SwitchPreference(
                checked = keepLocked,
                onCheckedChange = { checked ->
                    keepLocked = checked
                    settingsRepo.keepDownloadWhenLocked = keepLocked
                    if (keepLocked) {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        if (pm?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                            showBatteryDialog = true
                        }
                    }
                },
                title = AppStrings.get(R.string.settings_keep_downloading),
                summary = AppStrings.get(R.string.settings_keep_downloading_desc_short)
            )
            SwitchPreference(
                checked = showSpeed,
                onCheckedChange = { checked ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!notificationsEnabled) {
                        openNotificationSettings(context)
                    } else {
                        showSpeed = checked
                        settingsRepo.notificationShowSpeed = checked
                    }
                },
                title = AppStrings.get(R.string.settings_notification_progress),
                summary = when {
                    !notificationsEnabled -> AppStrings.get(R.string.settings_notification_progress_desc_no_perm)
                    showSpeed -> AppStrings.get(R.string.settings_notification_progress_desc_on)
                    else -> AppStrings.get(R.string.settings_notification_progress_desc_simple)
                }
            )
            // 多账号下载（实验性）：这里只做入口，两个开关在独立页面里改（开启前有二次风险确认）；
            // 「实验性」徽标只标在里面那两个开关上，入口这一行不重复标
            ArrowPreference(
                title = AppStrings.get(R.string.dl_ma_title),
                summary = AppStrings.get(R.string.settings_multi_account_download_desc),
                onClick = onMultiAccountDownloadClick,
                modifier = multiAccountRowModifier
            )
        }

        // ---------- 分类 2: 外观 ----------
        SmallTitle(text = AppStrings.get(R.string.section_appearance))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = AppStrings.get(R.string.theme_title),
                summary = AppStrings.get(R.string.settings_theme_entry_desc),
                onClick = onThemeClick
            )
        }

        // ---------- 分类 2.5: 语言 ----------
        SmallTitle(text = stringResource(R.string.section_language))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = stringResource(R.string.settings_language),
                summary = stringResource(
                    R.string.settings_language_desc,
                    stringResource(LocaleController.language.labelRes)
                ),
                onClick = onLanguageClick
            )
        }

        // ---------- 分类 3: 通用 ----------
        SmallTitle(text = AppStrings.get(R.string.section_general))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            // 上游 v1.3.1 新增「列表缩略图」：关掉后列表行退回文件图标，且不再向网盘发取图请求。
            // 读 AppThemeManager 的 Compose 内存态（改动即时反映到已打开的文件列表），写回同一管理器做持久化。
            SwitchPreference(
                checked = AppThemeManager.cloudThumbnails,
                onCheckedChange = { checked ->
                    AppThemeManager.setCloudThumbnails(context, checked)
                },
                title = AppStrings.get(R.string.settings_thumbnails),
                summary = if (AppThemeManager.cloudThumbnails) {
                    AppStrings.get(R.string.settings_thumbnails_desc_on)
                } else {
                    AppStrings.get(R.string.settings_thumbnails_desc_off)
                }
            )
            SwitchPreference(
                checked = clipboardSuggest,
                onCheckedChange = { checked ->
                    clipboardSuggest = checked
                    settingsRepo.clipboardSuggestEnabled = checked
                },
                title = AppStrings.get(R.string.settings_clipboard_detect),
                summary = if (clipboardSuggest) AppStrings.get(R.string.settings_clipboard_detect_desc_on) else AppStrings.get(R.string.settings_clipboard_detect_desc_off)
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_check_update),
                summary = AppStrings.get(R.string.settings_check_update_desc),
                onClick = onCheckUpdate
            )
            SwitchPreference(
                checked = acceptPrerelease,
                onCheckedChange = { checked ->
                    acceptPrerelease = checked
                    settingsRepo.acceptPrereleaseUpdate = checked
                },
                title = AppStrings.get(R.string.settings_accept_prerelease),
                summary = if (acceptPrerelease) AppStrings.get(R.string.settings_accept_prerelease_desc_on) else AppStrings.get(R.string.settings_accept_prerelease_desc_off)
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_export_log),
                summary = AppStrings.get(R.string.settings_export_log_desc),
                onClick = { showLogDialog = true }
            )
        }

        // ---------- 分类 4: 认证备份 ----------
        SmallTitle(text = AppStrings.get(R.string.section_auth_backup))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = AppStrings.get(R.string.settings_export_auth),
                summary = AppStrings.get(R.string.settings_export_auth_desc),
                onClick = { showExportAuthDialog = true }
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_import_auth),
                summary = AppStrings.get(R.string.settings_import_auth_desc),
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            )
        }

        // ---------- 分类 5: 链接唤起 ----------
        SmallTitle(text = AppStrings.get(R.string.vm_link_open_title))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = AppStrings.get(R.string.settings_link_open),
                summary = AppStrings.get(R.string.settings_link_open_desc),
                onClick = onLinkOpenClick,
                modifier = linkOpenRowModifier
            )
        }

        // ---------- 分类 6: 关于 ----------
        SmallTitle(text = AppStrings.get(R.string.section_about))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = CardGap)
        ) {
            ArrowPreference(
                title = AppStrings.get(R.string.vm_about_title),
                summary = AppStrings.get(R.string.settings_about_app_desc),
                onClick = onAboutClick,
                modifier = Modifier.combinedClickable(
                    onClick = onAboutClick,
                    onLongClick = { showDevMenu = true }
                )
            )
            ArrowPreference(
                title = AppStrings.get(R.string.vm_support_title),
                summary = AppStrings.get(R.string.settings_support_dev_desc),
                onClick = onSupportClick
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_qq_group),
                summary = AppStrings.get(R.string.settings_qq_group_desc, AppLinks.QQ_GROUP),
                onClick = {
                    if (!AppLinks.openQQGroup(context)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", AppLinks.QQ_GROUP))
                        SnackbarController.show(AppStrings.get(R.string.rs_qq_not_installed, AppLinks.QQ_GROUP))
                    }
                },
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (!AppLinks.openQQGroup(context)) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", AppLinks.QQ_GROUP))
                            SnackbarController.show(AppStrings.get(R.string.rs_qq_not_installed, AppLinks.QQ_GROUP))
                        }
                    },
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", AppLinks.QQ_GROUP))
                        SnackbarController.show(AppStrings.get(R.string.snack_qq_group_copied, AppLinks.QQ_GROUP))
                    }
                )
            )
            ArrowPreference(
                title = AppStrings.get(R.string.settings_github_repo),
                summary = AppStrings.get(R.string.settings_github_repo_desc, AppLinks.GITHUB_REPO_DISPLAY),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_REPO)))
                    }.onFailure {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("github_repo", AppLinks.GITHUB_REPO))
                        SnackbarController.show(AppStrings.get(R.string.settings_github_repo_no_browser, AppLinks.GITHUB_REPO))
                    }
                }
            )
        }
    }

    // 导出日志方式选择弹窗（Miuix OverlayDialog）
    OverlayDialog(
        show = showLogDialog,
        title = AppStrings.get(R.string.settings_export_log),
        onDismissRequest = { showLogDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_log_choose),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = AppStrings.get(R.string.dialog_log_share),
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { LogExporter.export(context) }
                        if (file != null && LogExporter.share(context, file)) {
                            SnackbarController.show(AppStrings.get(R.string.snack_log_shared))
                        } else {
                            SnackbarController.show(AppStrings.get(R.string.snack_log_export_failed))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.dialog_log_save),
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { LogExporter.saveToDownloads(context) }
                        SnackbarController.show(if (ok) AppStrings.get(R.string.snack_log_saved) else AppStrings.get(R.string.vm_support_save_failed))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.dialog_log_clear),
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { LogExporter.clearLogcat() }
                        SnackbarController.show(if (ok) AppStrings.get(R.string.snack_log_cleared) else AppStrings.get(R.string.snack_log_clear_failed))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showLogDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppStrings.get(R.string.csa_cancel))
            }
        }
    }

    // 隐藏开发调试菜单（长按「关于云析」打开）
    OverlayDialog(
        show = showDevMenu,
        title = AppStrings.get(R.string.dialog_dev_title),
        onDismissRequest = { showDevMenu = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = {
                    showDevMenu = false
                    // 预览走主页面同一弹窗：不再在本页维护第二份更新状态
                    onPreviewUpdateDialog()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppStrings.get(R.string.dialog_dev_show_update))
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 诊断模式：开启后 db / crypto / download / webview / network / operation 六个模块
            // 的详细日志写进私有目录 diagnostic_logs（按模块分文件），关闭时一行都不写。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppStrings.get(R.string.dialog_dev_diagnostic))
                    Text(
                        text = if (diagnosticOn) {
                            AppStrings.get(R.string.settings_diagnostic_path, context.packageName)
                        } else {
                            AppStrings.get(R.string.dialog_dev_diagnostic_off)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = diagnosticOn,
                    onCheckedChange = { on ->
                        diagnosticOn = on
                        settingsRepo.diagnosticMode = on
                        // 动态生效：DiagnosticLog.setEnabled 会立刻起/停写线程，关闭时先 flush
                        DiagnosticLog.setEnabled(context, on)
                        SnackbarController.show(if (on) AppStrings.get(R.string.snack_diagnostic_on) else AppStrings.get(R.string.snack_diagnostic_off))
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 下载调试：开启后按下载任务记录事件流（主要变更 / 错误节点），下载页长按任务可进调试信息页。
            // ★ 关闭会清空全部日志，所以不直接关，先弹二次确认（见下方 OverlayDialog）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppStrings.get(R.string.dl_debug_title))
                    Text(
                        text = if (downloadDebugOn) {
                            AppStrings.get(R.string.dialog_dev_download_debug_on)
                        } else {
                            AppStrings.get(R.string.dialog_dev_download_debug_off)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = downloadDebugOn,
                    onCheckedChange = { on ->
                        if (on) {
                            downloadDebugOn = true
                            AppThemeManager.setDownloadDebug(context, true)
                            DownloadDebugLog.setEnabled(true)
                            SnackbarController.show(AppStrings.get(R.string.snack_download_debug_on))
                        } else {
                            // 关闭会清空全部调试日志：先二次确认再真正关闭
                            showDisableDownloadDebugConfirm = true
                        }
                    }
                )
            }

            // 全量 SHA-256 只在「下载调试」开启时才有意义（摘要只写进调试日志），
            // 所以调试关着时整行隐藏；关掉下载调试会把它一并强制关闭，下次要手动重新开。
            if (downloadDebugOn) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(AppStrings.get(R.string.dialog_dev_full_sha))
                        Text(
                            text = if (fullShaOn) {
                                AppStrings.get(R.string.dialog_dev_full_sha_on)
                            } else {
                                AppStrings.get(R.string.dialog_dev_full_sha_off)
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = fullShaOn,
                        onCheckedChange = { on ->
                            fullShaOn = on
                            settingsRepo.fullFileSha256 = on
                            SnackbarController.show(if (on) AppStrings.get(R.string.snack_full_sha_on) else AppStrings.get(R.string.snack_full_sha_off))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 导出入口（zip 名 yunx_diagnostic_logs_yyyyMMdd_HHmmss.zip，导出前会先 flush）
            TextButton(
                text = AppStrings.get(R.string.dialog_log_export_zip),
                onClick = {
                    showDevMenu = false
                    scope.launch {
                        val zip = withContext(Dispatchers.IO) { LogExporter.exportDiagnosticZip(context) }
                        if (zip != null && LogExporter.share(context, zip)) {
                            SnackbarController.show(AppStrings.get(R.string.settings_diag_zip_shared, zip.name))
                        } else {
                            SnackbarController.show(AppStrings.get(R.string.snack_diag_log_empty))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = AppStrings.get(R.string.em_log_disabled),
                onClick = { showDevMenu = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 关闭「下载调试」的二次确认：关闭会清空已记录的全部调试日志，且不可恢复
    OverlayDialog(
        show = showDisableDownloadDebugConfirm,
        title = AppStrings.get(R.string.dialog_disable_debug_title),
        onDismissRequest = { showDisableDownloadDebugConfirm = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_disable_debug_message),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = AppStrings.get(R.string.action_close_and_clear),
                onClick = {
                    showDisableDownloadDebugConfirm = false
                    downloadDebugOn = false
                    AppThemeManager.setDownloadDebug(context, false)
                    // 关闭即清空：DownloadDebugLog.setEnabled(false) 内部会 clear()
                    DownloadDebugLog.setEnabled(false)
                    // 全量 SHA-256 依赖下载调试（摘要只写调试日志）：一并强制关闭，且不记忆，
                    // 下次重新开启下载调试后需要用户手动再开一次。
                    fullShaOn = false
                    settingsRepo.fullFileSha256 = false
                    SnackbarController.show(AppStrings.get(R.string.snack_download_debug_off))
                },
                colors = ButtonDefaults.textButtonColors(
                    textColor = MiuixTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.csa_cancel),
                onClick = { showDisableDownloadDebugConfirm = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 线程数选择弹窗（按平台）
    OverlayDialog(
        show = showThreadsDialog,
        title = AppStrings.get(R.string.settings_download_threads),
        onDismissRequest = { showThreadsDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_threads_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 平台行改用 miuix-preference 的 ArrowPreference：行结构、点击态与禁用态交给组件统一处理
            threadPlatforms.forEach { item ->
                val current = settingsRepo.downloadThreadsFor(item.platform)
                val isXunlei = item.platform == DownloadPlatform.XUNLEI
                ArrowPreference(
                    title = item.label,
                    summary = if (isXunlei) AppStrings.get(R.string.dialog_threads_fixed) else AppStrings.get(R.string.dl_threads, current),
                    enabled = !isXunlei,
                    onClick = {
                        selectedThreadPlatform = item
                        showPlatformThreadDialog = true
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showThreadsDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppStrings.get(R.string.rs_confirm))
            }
        }
    }

    // 单个平台线程数选择（二级弹窗）
    OverlayDialog(
        show = showPlatformThreadDialog,
        title = AppStrings.get(R.string.dialog_platform_threads_title, selectedThreadPlatform.label),
        onDismissRequest = { showPlatformThreadDialog = false }
    ) {
        val current = settingsRepo.downloadThreadsFor(selectedThreadPlatform.platform)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 线程档位改用 miuix-preference 单选行：整行可点，命中区与主题样式统一
            threadOptions.forEach { value ->
                RadioButtonPreference(
                    title = AppStrings.get(R.string.dl_threads, value),
                    selected = current == value,
                    onClick = {
                        settingsRepo.setDownloadThreads(selectedThreadPlatform.platform, value)
                        showPlatformThreadDialog = false
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showPlatformThreadDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                // 单选行点选即生效，按钮只负责关窗：叫「取消」会让人误以为能撤销刚才的选择
                Text(AppStrings.get(R.string.em_log_disabled))
            }
        }
    }

    // 导出认证信息弹窗（AES 加密密码 + 导出范围）
    ExportAuthDialog(
        show = showExportAuthDialog,
        onDismiss = { showExportAuthDialog = false },
        onConfirm = { password, onlyLoggedIn ->
            showExportAuthDialog = false
            isExporting = true
            scope.launch {
                try {
                    val content = runCatching {
                        withContext(Dispatchers.IO) { backupManager.export(password, onlyLoggedIn, context) }
                    }.getOrNull()
                    if (content == null) {
                        SnackbarController.show(AppStrings.get(R.string.snack_export_failed))
                        return@launch
                    }
                    val encrypted = true
                    val saved = withContext(Dispatchers.IO) {
                        backupManager.saveToDownloads(context, content, encrypted)
                    }
                    SnackbarController.show(
                        if (saved) {
                            if (encrypted) AppStrings.get(R.string.snack_auth_exported_encrypted) else AppStrings.get(R.string.snack_auth_exported)
                        } else {
                            AppStrings.get(R.string.snack_export_failed)
                        }
                    )
                } finally {
                    isExporting = false
                }
            }
        }
    )

    // 导入加密备份弹窗（解密密码）
    ImportAuthDialog(
        show = showImportAuthDialog,
        onDismiss = {
            showImportAuthDialog = false
            pendingImportContent = null
        },
        onConfirm = { password ->
            showImportAuthDialog = false
            val content = pendingImportContent
            pendingImportContent = null
            if (content != null) {
                isImporting = true
                scope.launch {
                    try {
                        val count = try {
                            withContext(Dispatchers.IO) { backupManager.import(content, password, context) }
                        } catch (e: javax.crypto.AEADBadTagException) {
                            SnackbarController.show(AppStrings.get(R.string.snack_decrypt_failed))
                            return@launch
                        } catch (e: Exception) {
                            SnackbarController.show(AppStrings.get(R.string.snack_import_failed, e.message ?: ""))
                            return@launch
                        }
                        SnackbarController.show(AppStrings.get(R.string.snack_auth_imported, count))
                    } finally {
                        isImporting = false
                    }
                }
            }
        }
    )

    // 导出/导入处理中：转圈加载弹窗
    if (isExporting) OperationLoadingDialog(AppStrings.get(R.string.loading_exporting_auth))
    if (isImporting) OperationLoadingDialog(AppStrings.get(R.string.loading_importing_auth))

    // 锁屏保持下载：引导加入「忽略电池优化」白名单
    OverlayDialog(
        show = showBatteryDialog,
        title = AppStrings.get(R.string.dialog_battery_title),
        onDismissRequest = { showBatteryDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_battery_message),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = AppStrings.get(R.string.action_not_now),
                    onClick = { showBatteryDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        showBatteryDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.get(R.string.action_go_settings))
                }
            }
        }
    }
}

/** 导出认证弹窗：AES 加密密码 + 导出范围（仅已登录 / 全部绑定） */
@Composable
private fun ExportAuthDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onlyLoggedIn: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var onlyLoggedIn by remember { mutableStateOf(true) }
    OverlayDialog(
        show = show,
        title = AppStrings.get(R.string.settings_export_auth),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_export_auth_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = AppStrings.get(R.string.dialog_export_auth_password),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            Text(
                text = AppStrings.get(R.string.dialog_export_scope),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            // 导出范围改用 miuix-preference 单选行：整行即命中区，交互与主题样式由组件统一负责
            RadioButtonPreference(
                title = AppStrings.get(R.string.dialog_export_scope_logged_in),
                selected = onlyLoggedIn,
                onClick = { onlyLoggedIn = true }
            )
            RadioButtonPreference(
                title = AppStrings.get(R.string.dialog_export_scope_all),
                selected = !onlyLoggedIn,
                onClick = { onlyLoggedIn = false }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(password, onlyLoggedIn) },
                    enabled = password.length >= 8,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.get(R.string.action_export))
                }
            }
        }
    }
}

/** 导入加密备份弹窗：输入解密密码 */
@Composable
private fun ImportAuthDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    OverlayDialog(
        show = show,
        title = AppStrings.get(R.string.settings_import_auth),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.dialog_import_auth_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = AppStrings.get(R.string.dialog_import_auth_password),
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(password) },
                    enabled = password.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.get(R.string.action_decrypt_import))
                }
            }
        }
    }
}

/** 操作处理中弹窗：转圈加载 + 提示文案，禁止关闭 */
@Composable
private fun OperationLoadingDialog(message: String) {
    OverlayDialog(
        show = true,
        title = message,
        onDismissRequest = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            InfiniteProgressIndicator()
        }
    }
}

/** 速度限制展示文案：0=不限速；>=1MB/s 显示 MB/s，否则 KB/s */
private fun speedLimitText(bps: Long): String {
    if (bps <= 0) return AppStrings.get(R.string.option_speed_unlimited)
    return if (bps >= 1024 * 1024) {
        val mb = bps / (1024.0 * 1024.0)
        if (mb >= 10) String.format("%.0f MB/s", mb) else String.format("%.1f MB/s", mb)
    } else {
        "${bps / 1024} KB/s"
    }
}
