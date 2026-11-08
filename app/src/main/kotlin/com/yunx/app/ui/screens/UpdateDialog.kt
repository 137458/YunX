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

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.download.DownloadSaver
import com.yunx.app.data.download.DownloadStats
import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.data.update.UpdatePackageDigest
import com.yunx.app.R
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.components.MarkdownText
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 弹窗层的下载态：只认 Idle / Downloading / Done / Failed 四态。
 *
 * 任务 id、暂停恢复、镜像切换、分片线程数这些引擎细节一律不进这一层——
 * 弹窗只负责「能不能点、显示什么」，任何一项引擎状态变化都不该让界面多出一档。
 */
sealed interface UpdateDialogState {
    /** 尚未开始下载（含任务被用户在下载页删除） */
    data object Idle : UpdateDialogState

    /** 任务在跑（含排队与用户暂停：暂停后进度只是不再推进，弹窗不额外多出「已暂停」态） */
    data class Downloading(val downloaded: Long, val total: Long, val speed: Long) : UpdateDialogState

    /** 已落盘且通过完整性校验，可以安装 */
    data class Done(val savePath: String) : UpdateDialogState

    data class Failed(val message: String) : UpdateDialogState
}

/** 下载失败但引擎没给出原因时的兜底文案 */
internal val UPDATE_DOWNLOAD_FAILED_MESSAGE: String
    get() = AppStrings.get(R.string.em_update_download_failed)

/** 进度比例：总长未知时恒为 0，交给进度条退回不确定态 */
internal fun updateDownloadFraction(downloaded: Long, total: Long): Float =
    if (total > 0L) (downloaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f

/** 引擎任务状态 → 弹窗四态（纯函数，规则由 UpdateDialogStateTest 钉住） */
internal fun updateDialogStateOf(
    status: Int?,
    downloaded: Long,
    total: Long,
    speed: Long,
    savePath: String,
    errorMsg: String,
): UpdateDialogState = when (status) {
    null -> UpdateDialogState.Idle
    DownloadTaskEntity.STATUS_PENDING,
    DownloadTaskEntity.STATUS_DOWNLOADING,
    DownloadTaskEntity.STATUS_PAUSED -> UpdateDialogState.Downloading(downloaded, total, speed)
    DownloadTaskEntity.STATUS_COMPLETED -> UpdateDialogState.Done(savePath)
    DownloadTaskEntity.STATUS_FAILED -> UpdateDialogState.Failed(errorMsg.ifBlank { UPDATE_DOWNLOAD_FAILED_MESSAGE })
    else -> UpdateDialogState.Idle
}

/**
 * 订阅某个更新下载任务并折算成弹窗四态。
 *
 * 任务完成不等于可以安装：这里会先把已经落盘的文件读一遍 SHA-256（GitHub 给了 digest 才校验），
 * 通过才对外报 [UpdateDialogState.Done]。摘要不匹配时删掉坏包并报失败，避免把篡改/损坏的包交给安装器。
 */
@Composable
fun rememberUpdateDialogState(
    tasks: StateFlow<List<DownloadTaskEntity>>,
    stats: StateFlow<Map<Long, DownloadStats>>,
    taskId: Long?,
    digest: String,
): UpdateDialogState {
    if (taskId == null) return UpdateDialogState.Idle
    val context = LocalContext.current
    val taskList by tasks.collectAsState()
    val statMap by stats.collectAsState()
    val task = taskList.firstOrNull { it.id == taskId } ?: return UpdateDialogState.Idle
    val raw = updateDialogStateOf(
        status = task.status,
        downloaded = task.downloadedSize,
        total = task.totalSize,
        speed = statMap[taskId]?.speed ?: 0L,
        savePath = task.savePath,
        errorMsg = task.errorMsg,
    )
    if (raw !is UpdateDialogState.Done) return raw

    // 校验结果按「任务 + 落盘位置 + 摘要」缓存：换任务重新校验，重组不重复读文件
    var verified by remember(taskId, raw.savePath, digest) { mutableStateOf<UpdateDialogState?>(null) }
    LaunchedEffect(taskId, raw.savePath, digest) {
        verified = withContext(Dispatchers.IO) {
            verifySavedUpdatePackage(context.applicationContext, raw.savePath, digest)
        }
    }
    // 校验期间保持 100% 的下载态：既不提前给安装按钮，也不会闪回「立即下载」
    return verified ?: UpdateDialogState.Downloading(
        downloaded = task.totalSize,
        total = task.totalSize,
        speed = 0L,
    )
}

/** 打开已落盘的安装包：可能是 MediaStore / SAF 的 content:// 地址，也可能是普通文件路径 */
private fun openSavedPackage(context: Context, savePath: String): InputStream? = runCatching {
    if (savePath.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(savePath))
    } else {
        FileInputStream(java.io.File(savePath))
    }
}.getOrNull()

/**
 * 落盘后的完整性校验：放在任务完成、文件落盘之后、对外报 Done 之前。
 *
 * 没有 digest 字段（老仓库 / 预览构造的 Release）直接跳过；但无论是否校验，
 * 都要确认文件仍可打开——任务标记完成而文件已被用户删掉时不能给出安装按钮。
 */
private fun verifySavedUpdatePackage(
    context: Context,
    savePath: String,
    digest: String,
): UpdateDialogState {
    if (savePath.isBlank()) return UpdateDialogState.Failed(AppStrings.get(R.string.em_update_package_missing))
    val expected = UpdatePackageDigest.expectedSha256(digest)
    val input = openSavedPackage(context, savePath)
        ?: return UpdateDialogState.Failed(AppStrings.get(R.string.em_update_package_missing))
    if (expected == null) {
        runCatching { input.close() }
        return UpdateDialogState.Done(savePath)
    }
    return when (UpdatePackageDigest.verify(input, digest)) {
        is UpdatePackageDigest.Verdict.Mismatched -> {
            // 坏包不能留在下载目录里被手动安装，直接删掉让用户重下
            DownloadSaver.delete(context, savePath)
            UpdateDialogState.Failed(AppStrings.get(R.string.em_update_digest_mismatch))
        }
        UpdatePackageDigest.Verdict.Matched,
        UpdatePackageDigest.Verdict.Skipped -> UpdateDialogState.Done(savePath)
    }
}

/** 内容区高度上限：超长说明在弹窗内部滚动，底部主按钮始终留在可视区 */
private val UpdateNotesMaxHeight = 240.dp

/** 内容区上下羽化距离 */
private val UpdateNotesFade = 40.dp

/**
 * 竖向边缘羽化：滚动区上下两侧的内容淡出，替代硬裁切。
 *
 * 只在还有内容的方向羽化：滚到顶或底时对应一侧完全实心，避免「永远有雾」的假暗示。
 * 必须套在 `verticalScroll` 外侧，否则量到的是内容整高，渐变带会滚出视口。
 */
private fun Modifier.verticalEdgeFade(top: Boolean, bottom: Boolean, fade: Dp): Modifier {
    if (!top && !bottom) return this
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        if (size.width <= 0f || size.height <= 0f) return@drawWithContent
        val fadePx = fade.toPx().coerceAtMost(size.height / 2f)
        val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
        val brush = when {
            top && bottom -> Brush.verticalGradient(
                0f to Color.Transparent,
                stop to Color.Black,
                1f - stop to Color.Black,
                1f to Color.Transparent,
            )
            top -> Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black)
            else -> Brush.verticalGradient(1f - stop to Color.Black, 1f to Color.Transparent)
        }
        drawRect(brush = brush, blendMode = BlendMode.DstIn)
    }
}

/**
 * 发现新版本弹窗（Miuix WindowDialog / HyperOS 规范）。
 *
 * 结构固定为：标题 + 一行版本摘要 + 更新说明 + 按下载态推导的动作区。
 * 说明区是弹窗里唯一的滚动容器（12 行折叠、240dp 限高、上下 40dp 羽化）；
 * 外层不再套 verticalScroll，避免两个同向滚动容器抢手势。
 */
@Composable
fun UpdateDialog(
    currentVersion: String,
    release: UpdateChecker.Release,
    download: UpdateDialogState,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onOpenDownloadPage: () -> Unit,
    onIgnore: () -> Unit,
    onCancelDownload: () -> Unit,
    /** 使用镜像站下载；为 null 时不显示镜像站入口 */
    onDownloadMirror: (() -> Unit)? = null,
    /** 打开发布页（没有 APK 资产时主按钮改走这里） */
    onOpenReleasePage: (() -> Unit)? = null,
) {
    val plan = remember(currentVersion, release, download, onDownloadMirror != null) {
        buildUpdateDialogPlan(
            currentVersion = currentVersion,
            release = release,
            downloadState = download,
            canIgnore = true,
            canCancelDownload = true,
            canMirror = onDownloadMirror != null,
        )
    }
    var notesExpanded by remember(release.tagName, release.body) { mutableStateOf(false) }
    val notesState = remember(release.body, notesExpanded) {
        updateDialogNotesState(release.body, notesExpanded, UPDATE_NOTES_PREVIEW_LINES)
    }

    WindowDialog(
        show = true,
        title = AppStrings.get(R.string.vm_update_new_version),
        summary = plan.summary,
        onDismissRequest = onLater,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (plan.showProgress) {
                DownloadProgress(plan = plan, state = download)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (plan.hasNotes) {
                NotesSection(
                    notesState = notesState,
                    expanded = notesExpanded,
                    onToggle = { notesExpanded = !notesExpanded },
                )
            }

            // 说明下方的文字链：镜像入口与「忽略此版本」不再占主操作位
            if (plan.showMirror && onDownloadMirror != null) {
                DialogTextLink(
                    text = AppStrings.get(R.string.vm_update_mirror_download_cn),
                    color = MiuixTheme.colorScheme.primary,
                    onClick = onDownloadMirror,
                )
            }
            if (plan.showIgnore) {
                DialogTextLink(
                    text = AppStrings.get(R.string.vm_update_ignore_version),
                    color = MiuixTheme.colorScheme.error,
                    onClick = onIgnore,
                )
            }
            if (plan.showOpenDownloadPage) {
                DialogTextLink(
                    text = AppStrings.get(R.string.vm_update_open_download_task),
                    color = MiuixTheme.colorScheme.primary,
                    onClick = onOpenDownloadPage,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = when (plan.primaryAction) {
                    UpdatePrimaryAction.DOWNLOAD -> onDownload
                    UpdatePrimaryAction.INSTALL -> onInstall
                    UpdatePrimaryAction.OPEN_RELEASE -> onOpenReleasePage ?: onDownload
                },
                enabled = plan.primaryEnabled,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = plan.primaryText, style = MiuixTheme.textStyles.button)
            }

            if (plan.showCancelDownload) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = AppStrings.get(R.string.vm_update_cancel_download),
                        onClick = onCancelDownload,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = plan.dismissText,
                        onClick = onLater,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                TextButton(
                    text = plan.dismissText,
                    onClick = onLater,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

/** 下载进度：明细行 + 进度条；总长未知时进度条退回不确定态，不伪造 0% */
@Composable
private fun DownloadProgress(plan: UpdateDialogPlan, state: UpdateDialogState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(12.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Text(
            text = if (state is UpdateDialogState.Downloading && state.total > 0L) {
                AppStrings.get(R.string.vm_update_downloading_package)
            } else {
                AppStrings.get(R.string.vm_update_downloading_package_unknown)
            },
            style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = MiuixTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = plan.progressDetail.orEmpty(),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = plan.progressFraction,
            modifier = Modifier.fillMaxWidth(),
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
}

/**
 * 更新说明视口：弹窗内唯一的滚动容器。
 *
 * 12 行折叠只是初始可见内容，限高 240dp 与上下 40dp 羽化负责滚动时的边缘过渡；
 * 羽化层必须贴在滚动容器外侧（见 [verticalEdgeFade] 的说明）。
 */
@Composable
private fun NotesSection(
    notesState: UpdateDialogNotesState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val hasContentAbove by remember { derivedStateOf { scrollState.value > 0 } }
    val hasContentBelow by remember { derivedStateOf { scrollState.maxValue > scrollState.value } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
            .border(
                0.5.dp,
                MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.25f),
                SquircleShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = UpdateNotesMaxHeight)
                .verticalEdgeFade(
                    top = hasContentAbove,
                    bottom = hasContentBelow,
                    fade = UpdateNotesFade,
                )
                .verticalScroll(scrollState),
        ) {
            MarkdownText(
                markdown = notesState.visibleMarkdown.ifBlank { AppStrings.get(R.string.vm_update_no_changelog) },
                modifier = Modifier.fillMaxWidth(),
                baseFontSize = 12,
            )
            if (notesState.hiddenLineCount > 0 || expanded) {
                TextButton(
                    text = if (expanded) AppStrings.get(R.string.vm_update_notes_collapse) else AppStrings.get(R.string.vm_update_notes_expand_more, notesState.hiddenLineCount),
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** 说明下方的文字链：样式统一，避免每处各写一遍 clickable + 内边距 */
@Composable
private fun DialogTextLink(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
