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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.backup.AuthCrypto
import com.yunx.app.data.download.DownloadPlatform
import com.yunx.app.data.download.DownloadSaver
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.SnackbarController
import com.yunx.app.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 可选的下载线程数档位（最高 512） */
private val threadOptions = listOf(1, 2, 4, 8, 16, 32, 64, 128, 256, 512)

/** 按平台下载线程数设置项 */
private data class ThreadPlatform(val platform: String, val label: String)

private val threadPlatforms = listOf(
    ThreadPlatform(DownloadPlatform.QUARK, "夸克网盘"),
    ThreadPlatform(DownloadPlatform.UC, "UC 网盘"),
    ThreadPlatform(DownloadPlatform.XUNLEI, "迅雷网盘"),
    ThreadPlatform(DownloadPlatform.BAIDU, "百度网盘"),
    ThreadPlatform(DownloadPlatform.C139, "139 网盘"),
    ThreadPlatform(DownloadPlatform.PAN123, "123 云盘"),
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
 * 设置页：下载策略 + 主题外观 + 检查更新 + 日志与网盘认证。
 * 基于 Miuix 规范重构：Card 分组包裹，miuix-preference 驱动，SliderPreference 内嵌策略控制。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSupportClick: () -> Unit,
    backupManager: AuthBackupManager,
    /** 用应用内置下载器下载更新 APK（URL + 文件名），由 MainScreen 注入 DownloadManager */
    onDownloadUpdateApk: (url: String, fileName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThreadsDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    // 检查更新结果（非空时弹更新对话框）
    var updateRelease by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    // 网盘认证导出弹窗（AES 加密 + 导出范围）
    var showExportAuthDialog by remember { mutableStateOf(false) }
    // 网盘认证导入：加密文件内容（非空时弹解密密码框）
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
    var downloadDirUri by remember { mutableStateOf(settingsRepo.downloadDirUri) }
    var showDevMenu by remember { mutableStateOf(false) }
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
            SnackbarController.show("下载保存目录已更新")
        }
    }
    // 导入网盘认证文件选择器
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
                        SnackbarController.show("读取文件失败")
                        return@launch
                    }
                    if (AuthCrypto.isEncrypted(text)) {
                        pendingImportContent = text
                        showImportAuthDialog = true
                    } else {
                        val count = runCatching {
                            withContext(Dispatchers.IO) { backupManager.importJson(text) }
                        }.getOrElse { e ->
                            SnackbarController.show("导入失败：${e.message}")
                            return@launch
                        }
                        SnackbarController.show("已恢复 $count 个平台的认证信息")
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // ---------- 分类 1: 下载 ----------
        SmallTitle(text = "下载")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "下载线程数",
                summary = "按网盘分别设置分片并发数（默认 32，最高 512）",
                onClick = { showThreadsDialog = true }
            )
            ArrowPreference(
                title = "下载保存目录",
                summary = downloadDirUri?.let { "已自定义：${DownloadSaver.safDirDisplay(it)}" }
                    ?: "系统默认 Download（点击自定义）",
                onClick = { dirLauncher.launch(null) }
            )
            if (downloadDirUri != null) {
                ArrowPreference(
                    title = "恢复默认下载目录",
                    summary = "点击恢复为系统默认目录",
                    onClick = {
                        downloadDirUri = null
                        settingsRepo.downloadDirUri = null
                        SnackbarController.show("已恢复默认下载目录")
                    }
                )
            }
            SliderPreference(
                value = maxConcurrent.toFloat(),
                onValueChange = {
                    val v = it.roundToInt().coerceIn(1, 8)
                    maxConcurrent = v
                    settingsRepo.maxConcurrentDownloads = v
                },
                valueRange = 1f..8f,
                steps = 6,
                title = "最大同时下载任务数",
                summary = "同时下载 $maxConcurrent 个任务（限制后台并发）",
                valueText = "$maxConcurrent 个"
            )
            SliderPreference(
                value = (speedLimitBps / (1024 * 1024)).toFloat(),
                onValueChange = { mb ->
                    val limit = mb.roundToLong() * 1024 * 1024
                    speedLimitBps = limit
                    settingsRepo.downloadSpeedLimit = limit
                },
                valueRange = 0f..30f,
                steps = 29,
                title = "下载速度限制",
                summary = speedLimitText(speedLimitBps),
                valueText = if (speedLimitBps <= 0) "不限速" else "${speedLimitBps / (1024 * 1024)} MB/s"
            )
            SliderPreference(
                value = retryCount.toFloat(),
                onValueChange = {
                    val v = it.roundToInt().coerceIn(0, 10)
                    retryCount = v
                    settingsRepo.downloadRetryCount = v
                },
                valueRange = 0f..10f,
                steps = 9,
                title = "失败自动重试",
                summary = if (retryCount == 0) "失败后不自动重试" else "失败后自动重试 $retryCount 次（断点续传）",
                valueText = if (retryCount == 0) "不重试" else "$retryCount 次"
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
                title = "锁屏后保持下载",
                summary = "开启后下载时获取 WakeLock 维持网络，并可加入白名单"
            )
            SwitchPreference(
                checked = showSpeed,
                onCheckedChange = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!notificationsEnabled) {
                        openNotificationSettings(context)
                    } else {
                        showSpeed = !showSpeed
                        settingsRepo.notificationShowSpeed = showSpeed
                    }
                },
                title = "通知栏下载进度",
                summary = when {
                    !notificationsEnabled -> "通知未开启，下载通知将不可见（点击去开启）"
                    showSpeed -> "完整通知：进度条 + 下载速度"
                    else -> "仅显示通知（隐藏下载速度）"
                }
            )
        }

        // ---------- 分类 2: 外观 ----------
        SmallTitle(text = "外观")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "主题与外观",
                summary = "主题色、动态色彩与深色模式",
                onClick = onThemeClick
            )
        }

        // ---------- 分类 3: 通用 ----------
        SmallTitle(text = "通用")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "检查更新",
                summary = "检查 GitHub 是否有新版本可用",
                onClick = {
                    scope.launch {
                        SnackbarController.show("正在检查更新…")
                        val release = runCatching { UpdateChecker.fetchLatestRelease() }.getOrNull()
                        val current = UpdateChecker.currentVersion(context)
                        if (release == null) {
                            SnackbarController.show("检查更新失败，请检查网络")
                        } else if (UpdateChecker.compareVersions(release.tagName, current) > 0) {
                            updateRelease = release
                        } else {
                            SnackbarController.show("已是最新版本")
                        }
                    }
                }
            )
            ArrowPreference(
                title = "导出日志",
                summary = "导出崩溃日志与应用信息，便于排查问题",
                onClick = { showLogDialog = true }
            )
        }

        // ---------- 分类 4: 网盘认证 ----------
        SmallTitle(text = "网盘认证")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "导出网盘认证",
                summary = "使用至少 8 位口令加密 Cookie/JWT 后导出",
                onClick = { showExportAuthDialog = true }
            )
            ArrowPreference(
                title = "导入网盘认证",
                summary = "选择加密或明文的认证备份文件，恢复网盘登录",
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
            )
        }

        // ---------- 分类 5: 关于 ----------
        SmallTitle(text = "关于")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            ArrowPreference(
                title = "关于云析",
                summary = "版本信息、支持平台与技术说明",
                onClick = onAboutClick,
                modifier = Modifier.combinedClickable(
                    onClick = onAboutClick,
                    onLongClick = { showDevMenu = true }
                )
            )
            ArrowPreference(
                title = "支持开发",
                summary = "微信扫码捐赠，支持项目持续维护",
                onClick = onSupportClick
            )
        }
    }

    // 导出日志方式选择弹窗（Miuix OverlayDialog）
    OverlayDialog(
        show = showLogDialog,
        title = "导出日志",
        onDismissRequest = { showLogDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "选择日志导出方式：",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = "分享日志（发送到其他应用）",
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { LogExporter.export(context) }
                        if (file != null && LogExporter.share(context, file)) {
                            SnackbarController.show("日志已分享")
                        } else {
                            SnackbarController.show("导出日志失败")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "保存到下载目录",
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { LogExporter.saveToDownloads(context) }
                        SnackbarController.show(if (ok) "已保存到下载目录" else "保存失败")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "清空日志缓存（logcat -c）",
                onClick = {
                    showLogDialog = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { LogExporter.clearLogcat() }
                        SnackbarController.show(if (ok) "日志缓存已清空" else "清空失败")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showLogDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
        }
    }

    // 隐藏开发调试菜单（长按「关于云析」打开）
    OverlayDialog(
        show = showDevMenu,
        title = "开发调试",
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
                    scope.launch {
                        val release = runCatching { UpdateChecker.fetchLatestRelease() }.getOrNull()
                        updateRelease = release ?: UpdateChecker.Release(
                            tagName = "v1.2.4（预览）",
                            body = "这是调试预览弹窗，用于查看更新弹窗 UI（含镜像站下载按钮）。",
                            assets = emptyList(),
                            publishedAt = ""
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("显示检查更新弹窗")
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                text = "关闭",
                onClick = { showDevMenu = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // 检查更新结果弹窗（发现新版本时展示，下载走系统浏览器）
    updateRelease?.let { release ->
        UpdateDialog(
            currentVersion = UpdateChecker.currentVersion(context),
            release = release,
            onDownload = {
                updateRelease = null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                if (apk != null) {
                    onDownloadUpdateApk(apk.downloadUrl, apk.name)
                    SnackbarController.show("已加入下载 ${apk.name}")
                } else {
                    SnackbarController.show("未找到 APK 下载链接")
                }
            },
            onDownloadMirror = {
                updateRelease = null
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", true) }
                if (apk != null) {
                    onDownloadUpdateApk(UpdateChecker.mirrorUrl(apk.downloadUrl), apk.name)
                    SnackbarController.show("已通过镜像站加入下载 ${apk.name}")
                } else {
                    SnackbarController.show("未找到 APK 下载链接")
                }
            },
            onLater = { updateRelease = null },
            onIgnore = {
                context.getSharedPreferences("yunx_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("ignored_version", release.tagName)
                    .apply()
                updateRelease = null
            }
        )
    }

    // 线程数选择弹窗（按平台）
    OverlayDialog(
        show = showThreadsDialog,
        title = "下载线程数",
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
                text = "按网盘分别设置分片并发数；线程数不是越多越好，适当调整",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            threadPlatforms.forEach { item ->
                val current = settingsRepo.downloadThreadsFor(item.platform)
                val isXunlei = item.platform == DownloadPlatform.XUNLEI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isXunlei) {
                            selectedThreadPlatform = item
                            showPlatformThreadDialog = true
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isXunlei) "固定 8 线程" else "$current 线程",
                        style = MiuixTheme.textStyles.body2,
                        color = if (isXunlei) MiuixTheme.colorScheme.onSurfaceSecondary else MiuixTheme.colorScheme.primary
                    )
                    if (!isXunlei) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showThreadsDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确定")
            }
        }
    }

    // 单个平台线程数选择（二级弹窗）
    OverlayDialog(
        show = showPlatformThreadDialog,
        title = "${selectedThreadPlatform.label}线程数",
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
            threadOptions.chunked(2).forEach { rowValues ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowValues.forEach { value ->
                        RadioThreadRow(
                            value = value,
                            threads = current,
                            onSelect = { v ->
                                settingsRepo.setDownloadThreads(selectedThreadPlatform.platform, v)
                                showPlatformThreadDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowValues.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showPlatformThreadDialog = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
        }
    }

    // 导出网盘认证弹窗（AES 加密密码 + 导出范围）
    ExportAuthDialog(
        show = showExportAuthDialog,
        onDismiss = { showExportAuthDialog = false },
        onConfirm = { password, onlyLoggedIn ->
            showExportAuthDialog = false
            isExporting = true
            scope.launch {
                try {
                    val content = runCatching {
                        withContext(Dispatchers.IO) { backupManager.export(password, onlyLoggedIn) }
                    }.getOrNull()
                    if (content == null) {
                        SnackbarController.show("导出失败")
                        return@launch
                    }
                    val encrypted = true
                    val saved = withContext(Dispatchers.IO) {
                        backupManager.saveToDownloads(context, content, encrypted)
                    }
                    SnackbarController.show(
                        if (saved) {
                            if (encrypted) "已加密导出到下载目录" else "已导出到下载目录"
                        } else {
                            "导出失败"
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
                            withContext(Dispatchers.IO) { backupManager.import(content, password) }
                        } catch (e: javax.crypto.AEADBadTagException) {
                            SnackbarController.show("密码错误，解密失败")
                            return@launch
                        } catch (e: Exception) {
                            SnackbarController.show("导入失败：${e.message}")
                            return@launch
                        }
                        SnackbarController.show("已恢复 $count 个平台的认证信息")
                    } finally {
                        isImporting = false
                    }
                }
            }
        }
    )

    // 导出/导入处理中：转圈加载弹窗
    if (isExporting) OperationLoadingDialog("正在导出认证…")
    if (isImporting) OperationLoadingDialog("正在导入认证…")

    // 锁屏保持下载：引导加入「忽略电池优化」白名单
    OverlayDialog(
        show = showBatteryDialog,
        title = "保持后台下载",
        onDismissRequest = { showBatteryDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "为确保障屏后下载不中断，建议将云析加入「忽略电池优化」白名单。是否前往系统设置？",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "暂不",
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
                    Text("前往设置")
                }
            }
        }
    }
}

/** 导出网盘认证弹窗：AES 加密密码 + 导出范围（仅已登录 / 全部绑定） */
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
        title = "导出网盘认证",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "设置至少 8 位密码对认证文件进行 AES 加密。密码请务必牢记，丢失无法找回。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = "加密密码（至少 8 位）",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            Text(
                text = "导出范围",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onlyLoggedIn = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = onlyLoggedIn,
                    onClick = { onlyLoggedIn = true }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("仅导出当前已登录的网盘", style = MiuixTheme.textStyles.body1)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onlyLoggedIn = false }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !onlyLoggedIn,
                    onClick = { onlyLoggedIn = false }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("导出全部绑定的网盘", style = MiuixTheme.textStyles.body1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(password, onlyLoggedIn) },
                    enabled = password.length >= 8,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导出")
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
        title = "导入网盘认证",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "该备份文件已加密，请输入导出时设置的密码进行解密。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            TextField(
                value = password,
                onValueChange = { password = it },
                label = "解密密码",
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
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onConfirm(password) },
                    enabled = password.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("解密并导入")
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

/** 线程数单选行（用于弹窗两列布局，每行占半宽） */
@Composable
private fun RadioThreadRow(
    value: Int,
    threads: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect(value) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = threads == value,
            onClick = { onSelect(value) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$value 线程",
            style = MiuixTheme.textStyles.body1
        )
    }
}

/** 速度限制展示文案：0=不限速；>=1MB/s 显示 MB/s，否则 KB/s */
private fun speedLimitText(bps: Long): String {
    if (bps <= 0) return "不限速"
    return if (bps >= 1024 * 1024) {
        val mb = bps / (1024.0 * 1024.0)
        if (mb >= 10) String.format("%.0f MB/s", mb) else String.format("%.1f MB/s", mb)
    } else {
        "${bps / 1024} KB/s"
    }
}
