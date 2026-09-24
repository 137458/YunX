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

package com.yunx.app.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.MainActivity
import com.yunx.app.R
import com.yunx.app.ui.GlobalSnackbarHost
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.items.CustomFabMenu
import com.yunx.app.ui.items.FabMenuItem
import com.yunx.app.ui.theme.ComposeEmptyActivityTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 崩溃界面（运行在独立进程 :crash）：
 * 展示崩溃报告，并通过 CustomFabMenu 提供「复制崩溃信息 / 重启应用 / 退出应用」。
 */
class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 应用内主题设置：与 MainActivity 一致，必须在 super.onCreate 之前切换窗口主题。
        // values-night 只跟随系统，若应用内「始终深色」而系统是浅色，崩溃页会先闪一帧白色背景。
        val darkModePref = getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getInt("dark_mode", 0)
        when (darkModePref) {
            1 -> setTheme(R.style.Theme_ComposeEmptyActivity_Light)
            2 -> setTheme(R.style.Theme_ComposeEmptyActivity_Dark)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crashLog = intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG) ?: "未知错误"
        setContent {
            ComposeEmptyActivityTheme {
                CrashScreen(crashLog = crashLog)
            }
        }
    }
}

@Composable
private fun CrashScreen(crashLog: String) {
    val context = LocalContext.current
    var fabExpanded by remember { mutableStateOf(false) }

    val menuItems = remember(crashLog) {
        listOf(
            FabMenuItem(
                label = "复制崩溃信息",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    copyToClipboard(context, crashLog)
                    SnackbarController.show("崩溃信息已复制")
                }
            ),
            FabMenuItem(
                label = "重启应用",
                icon = Icons.Outlined.Refresh,
                onClick = { restartApp(context) }
            ),
            FabMenuItem(
                label = "退出应用",
                icon = Icons.Outlined.ExitToApp,
                onClick = { exitApp(context) }
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 消费系统栏 insets：enableEdgeToEdge 后正文与 FAB 都不能被状态栏/导航栏遮挡
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // 错误图标
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MiuixTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MiuixTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "应用发生崩溃",
                style = MiuixTheme.textStyles.title1,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "很抱歉，应用遇到了未预期的错误。你可以复制崩溃信息反馈给开发者，或重启应用继续使用。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 崩溃报告（整页可滚动）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer
                ),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = crashLog,
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(88.dp)) // 给 FAB 留出空间（系统栏 insets 已由外层 Box 消费）
        }

        CustomFabMenu(
            expanded = fabExpanded,
            onCheckedChange = { fabExpanded = it },
            items = menuItems
        )

        // 全局 Snackbar（崩溃页为独立 Activity，需自带宿主）
        GlobalSnackbarHost()
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", text))
}

private fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    context.startActivity(intent)
    if (context is Activity) context.finish()
    Process.killProcess(Process.myPid())
}

private fun exitApp(context: Context) {
    if (context is Activity) context.finishAffinity()
    Process.killProcess(Process.myPid())
}