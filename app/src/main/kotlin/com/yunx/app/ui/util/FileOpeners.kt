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

package com.yunx.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.yunx.app.ui.SnackbarController
import java.io.File

/**
 * 打开已下载文件 / 安装 APK 的公共出口。
 *
 * 抽到 ui/util 是因为「下载页任务卡片」与「更新弹窗」两个入口都要用同一套
 * FileProvider 转换与未知来源权限检查，避免两处实现分叉。
 */

/** 用系统应用打开已保存的文件（下载目录为空则忽略） */
internal fun openSavedFile(context: Context, savePath: String) {
    if (savePath.isBlank()) return
    val uri = if (savePath.startsWith("content://")) {
        Uri.parse(savePath)
    } else {
        // Android 7.0+ 禁止暴露 file:// URI，必须经 FileProvider 转 content://
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(savePath))
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        SnackbarController.show("无法打开该文件")
    }
}

/** 安装 APK：检查「安装未知来源应用」权限（Android 8+），ACTION_VIEW 调起系统安装器 */
internal fun installApk(context: Context, savePath: String) {
    if (savePath.isBlank()) {
        SnackbarController.show("文件不存在")
        return
    }
    // Android 8+：需先授予「安装未知来源应用」
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        SnackbarController.show("请先允许安装未知来源应用")
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }.onFailure {
            SnackbarController.show("无法打开设置")
        }
        return
    }
    val uri = if (savePath.startsWith("content://")) {
        Uri.parse(savePath)
    } else {
        // Android 7.0+ 禁止暴露 file:// URI，必须经 FileProvider 转 content://
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(savePath))
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "安装应用"))
    }.onFailure {
        SnackbarController.show("无法打开安装器")
    }
}