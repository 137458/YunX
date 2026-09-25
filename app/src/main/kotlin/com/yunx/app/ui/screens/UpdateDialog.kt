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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.ui.components.MarkdownText
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
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
 * 发现新版本弹窗（Miuix WindowDialog / HyperOS 规范）：
 * 版本跃迁横幅 + 三联元信息胶囊 + Markdown 更新日志视口 + 随状态切换的底部按钮栏。
 *
 * 下载走应用内置下载引擎（分片 / 断点续传 / 通知栏进度），弹窗只订阅该任务的实时状态：
 * 用户既能看到与下载页一致的进度，也可以在下载中退到后台继续，或直接转到下载任务页接管。
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
    onDownloadMirror: (() -> Unit)? = null,
    /** 打开发布页外链（查看完整发布说明）；为 null 时不显示该入口 */
    onOpenReleasePage: (() -> Unit)? = null
) {
    val isDownloading = download != null && download.isActive

    WindowDialog(
        show = true,
        onDismissRequest = { if (!isDownloading) onLater() },
        title = "发现新版本",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 横屏矮视口下弹层内容总高会超过窗口，整体可滚动避免被裁切
                .verticalScroll(rememberScrollState())
        ) {
            VersionBanner(currentVersion = currentVersion, newVersion = release.tagName)
            Spacer(modifier = Modifier.height(10.dp))
            MetaCapsuleRow(release = release)
            Spacer(modifier = Modifier.height(12.dp))
            ChangelogViewport(body = release.body)

            if (download != null) {
                Spacer(modifier = Modifier.height(12.dp))
                DownloadStatusSection(download)
            }

            Spacer(modifier = Modifier.height(16.dp))
            UpdateActionBar(
                download = download,
                mirrorDownload = onDownloadMirror,
                onDownload = onDownload,
                onInstall = onInstall,
                onOpenDownloadPage = onOpenDownloadPage,
                onIgnore = onIgnore,
                onLater = onLater
            )

            if (onOpenReleasePage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "在浏览器中查看完整发布说明",
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenReleasePage)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** 版本跃迁横幅：当前版本 ➔ 新版本高亮胶囊（含 NEW 角标） */
@Composable
private fun VersionBanner(currentVersion: String, newVersion: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "当前 $currentVersion",
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = "➔",
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
            color = MiuixTheme.colorScheme.primary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = newVersion,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = MiuixTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "NEW",
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** 三联元信息胶囊栏：发布通道 / 安装包体积 / 发布日期 */
@Composable
private fun MetaCapsuleRow(release: UpdateChecker.Release) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetaCapsule(text = "稳定版")
        release.apkAsset?.takeIf { it.size > 0L }?.let { apk ->
            MetaCapsule(text = UpdateChecker.formatBytes(apk.size))
        }
        Spacer(modifier = Modifier.weight(1f))
        val date = release.publishedAt.takeIf { it.isNotBlank() }?.take(10)
        if (date != null) {
            Text(
                text = date,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/** 单个元信息胶囊 */
@Composable
private fun MetaCapsule(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 更新日志展示视口：Markdown 富文本，限高内部滚动（矮视口进一步压缩） */
@Composable
private fun ChangelogViewport(body: String) {
    val configuration = LocalConfiguration.current
    // 横屏 / 矮视口下日志区让位给按钮栏，避免弹窗内容被系统裁掉
    val maxHeight = if (configuration.screenHeightDp < 480) 130.dp else 230.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp, max = maxHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
            .border(
                0.5.dp,
                MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        MarkdownText(
            markdown = body.ifBlank { "暂无更新说明" },
            modifier = Modifier.fillMaxWidth(),
            baseFontSize = 13
        )
    }
}

/** 下载状态反馈区：进度条 + 「已下载 / 总大小 · 速度」；完成/失败给出可操作说明 */
@Composable
private fun DownloadStatusSection(download: UpdateDownloadState) {
    when {
        download.isFailed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.error.copy(alpha = 0.10f))
                .padding(12.dp)
        ) {
            Text(
                text = "更新包下载失败",
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "可在下载任务中重试，或改用镜像站重新下载。",
                style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        download.isFinished -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "更新包已下载完成，点击下方按钮进行安装。",
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.primary
            )
        }

        else -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val speedText = if (download.speed > 0) " · ${UpdateChecker.formatBytes(download.speed)}/s" else ""
                Text(
                    text = "正在下载更新包$speedText",
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = if (download.total > 0) {
                        "${UpdateChecker.formatBytes(download.downloaded)} / ${UpdateChecker.formatBytes(download.total)}" +
                            " (${(download.fraction * 100).toInt()}%)"
                    } else {
                        // 服务器未给 Content-Length：只报已下载量，不伪造百分比
                        "${UpdateChecker.formatBytes(download.downloaded)} · 大小未知"
                    },
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 进度条只在总数已知时出现：否则画出来永远是 0%，反而让人以为卡住了
            if (download.total > 0) {
                LinearProgressIndicator(
                    progress = download.fraction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = MiuixTheme.colorScheme.primary,
                        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }
    }
}

/** 底部操作按钮栏：按下载状态切换按钮语义（未开始 / 下载中 / 已完成 / 失败） */
@Composable
private fun UpdateActionBar(
    download: UpdateDownloadState?,
    mirrorDownload: (() -> Unit)?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                // ① 下载中：转到下载任务接管（应用内下载引擎支持后台续跑）
                download != null && download.isActive -> {
                    Button(
                        onClick = onLater,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("后台下载")
                    }
                    Button(
                        onClick = onOpenDownloadPage,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("转到下载任务")
                    }
                }

                // ② 下载完成：重新下载 / 安装更新
                download != null && download.isFinished -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重新下载")
                    }
                    Button(
                        onClick = onInstall,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("安装更新")
                    }
                }

                // ③ 下载失败：镜像站重试 / 直接重试
                download != null && download.isFailed -> {
                    if (mirrorDownload != null) {
                        Button(
                            onClick = mirrorDownload,
                            colors = ButtonDefaults.buttonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("镜像站下载")
                        }
                    }
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(if (mirrorDownload != null) 1.2f else 1f)
                    ) {
                        Text("重试")
                    }
                }

                // ④ 尚未开始：忽略此版本 / 稍后 / 立即更新
                else -> {
                    Button(
                        onClick = onIgnore,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("忽略此版本")
                    }
                    Button(
                        onClick = onLater,
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("稍后")
                    }
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("立即更新")
                    }
                }
            }
        }

        // 镜像站入口只在「未开始下载」时露出：失败态已把镜像站升级为主按钮之一
        if (mirrorDownload != null && download == null) {
            Text(
                text = "使用镜像站下载（国内加速）",
                style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                color = MiuixTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = mirrorDownload)
                    .padding(vertical = 6.dp)
            )
        }
    }
}
