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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.components.MarkdownText
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 更新包下载进度快照：数据直接取自内置下载引擎的任务与实时统计，
 * 因此弹窗内的进度与下载页完全一致（同一任务、同一份状态）。
 */
data class UpdateDownloadState(
    val status: Int,
    val downloaded: Long,
    val total: Long,
    val speed: Long,
    val savePath: String
) {
    val isActive: Boolean
        get() = status == DownloadTaskEntity.STATUS_DOWNLOADING ||
            status == DownloadTaskEntity.STATUS_PENDING

    val isFinished: Boolean
        get() = status == DownloadTaskEntity.STATUS_COMPLETED

    val isFailed: Boolean
        get() = status == DownloadTaskEntity.STATUS_FAILED

    /** 服务器未给 Content-Length 时无法算比例，由调用方展示"大小未知" */
    val fraction: Float
        get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * 订阅某个更新下载任务的状态；任务已被用户在下载页删除时返回 null，
 * 弹窗随即回到「下载更新」初始按钮组。
 */
@Composable
fun rememberUpdateDownloadState(
    tasks: StateFlow<List<DownloadTaskEntity>>,
    stats: StateFlow<Map<Long, DownloadStats>>,
    taskId: Long?
): UpdateDownloadState? {
    if (taskId == null) return null
    val taskList by tasks.collectAsState()
    val statMap by stats.collectAsState()
    val task = taskList.firstOrNull { it.id == taskId } ?: return null
    return UpdateDownloadState(
        status = task.status,
        downloaded = task.downloadedSize,
        total = task.totalSize,
        speed = statMap[taskId]?.speed ?: 0L,
        savePath = task.savePath
    )
}

/**
 * 发现新版本弹窗（Miuix WindowDialog）：
 * 标题 + 当前/最新版本 + 更新说明（可滚动）+ 下载更新 / 稍后 / 忽略本次。
 *
 * 下载走应用内置下载引擎，并在同一弹窗内展示进度：用户不再被切到下载页后
 * 自己去找那个 APK 任务；下载完成后可直接「安装更新」，也可随时转到下载任务页查看。
 */
@Composable
fun UpdateDialog(
    currentVersion: String,
    release: UpdateChecker.Release,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    /** 更新包下载进度；null = 尚未开始下载 */
    download: UpdateDownloadState? = null,
    /** 下载完成后安装更新包 */
    onInstall: () -> Unit = {},
    /** 关闭弹窗并跳到下载任务页 */
    onOpenDownloadPage: () -> Unit = {},
    /** 使用镜像站下载（可选）；为 null 时不显示镜像站按钮 */
    onDownloadMirror: (() -> Unit)? = null
) {
    WindowDialog(
        show = true,
        onDismissRequest = onLater,
        title = "发现新版本",
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = release.tagName,
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "当前 $currentVersion",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Text(
                text = "更新内容",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                insideMargin = PaddingValues(12.dp)
            ) {
                // Release 说明是 Markdown：按富文本渲染，避免把 ## / - / ** 原样甩给用户
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownText(
                        markdown = release.body.ifBlank { "暂无更新说明" },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            when {
                // ① 未开始下载：主按钮 + 镜像站 + 忽略/稍后
                download == null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MiuixTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下载更新", style = MiuixTheme.textStyles.button)
                        }
                        if (onDownloadMirror != null) {
                            TextButton(
                                text = "使用镜像站下载",
                                onClick = onDownloadMirror,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IgnoreAndLaterRow(onIgnore = onIgnore, onLater = onLater)
                    }
                }

                // ② 下载中 / 失败：进度区 + 转到下载任务
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DownloadProgressSection(download)
                        if (download.isFinished) {
                            Button(
                                onClick = onInstall,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("安装更新", style = MiuixTheme.textStyles.button)
                            }
                        }
                        TextButton(
                            text = "转到下载任务",
                            onClick = onOpenDownloadPage,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            text = "稍后",
                            onClick = onLater,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** 下载进度区：进度条 + 「已下载 / 总大小 · 速度」；失败时给出可操作的说明 */
@Composable
private fun DownloadProgressSection(download: UpdateDownloadState) {
    if (download.isFailed) {
        Text(
            text = "更新包下载失败，可到下载任务中重试。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.error
        )
        return
    }
    if (download.isFinished) {
        Text(
            text = "更新包已下载完成，点击下方按钮进行安装。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
        return
    }
    // 进度条只在总数已知时出现：否则画出来永远是 0%，反而让人以为卡住了
    if (download.total > 0) {
        LinearProgressIndicator(
            progress = download.fraction,
            modifier = Modifier.fillMaxWidth(),
            height = 6.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfiniteProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = MiuixTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (download.total > 0) {
                "已下载 ${formatBytes(download.downloaded)} / ${formatBytes(download.total)}" +
                    if (download.speed > 0) " · ${formatBytes(download.speed)}/s" else ""
            } else {
                "已下载 ${formatBytes(download.downloaded)} · 大小未知"
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 「忽略此版本 / 稍后」两个次要动作并排 */
@Composable
private fun IgnoreAndLaterRow(onIgnore: () -> Unit, onLater: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(
            text = "忽略此版本",
            onClick = onIgnore,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            text = "稍后",
            onClick = onLater,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 字节数格式化（B/KB/MB/GB），与下载页展示口径保持一致 */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}