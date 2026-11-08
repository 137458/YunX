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

import com.yunx.app.data.update.UpdateChecker
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.util.ByteFormat

/** 折叠态只展示前 12 行更新说明，展开后整段放进弹窗唯一的滚动区 */
internal const val UPDATE_NOTES_PREVIEW_LINES = 12

/** 弹窗主按钮对应的动作；文案与动作都由下载态推导，界面不自己判断状态 */
internal enum class UpdatePrimaryAction { DOWNLOAD, INSTALL, OPEN_RELEASE }

/**
 * 弹窗的一次快照：Miuix 组件只按这份数据铺像素。
 *
 * 只留决策所需信息：一行版本摘要、更新说明、下一步动作与进度。
 * 安装包文件名、附件数量、说明行数这类对「是否更新」没有影响的信息不进入计划，
 * 也就不会再被堆回界面。
 */
internal data class UpdateDialogPlan(
    val summary: String,
    val primaryAction: UpdatePrimaryAction,
    val primaryText: String,
    val primaryEnabled: Boolean,
    val dismissText: String,
    val showProgress: Boolean,
    val progressFraction: Float?,
    val progressDetail: String?,
    val showCancelDownload: Boolean,
    val showOpenDownloadPage: Boolean,
    val showIgnore: Boolean,
    val showMirror: Boolean,
    val hasNotes: Boolean,
)

internal data class UpdateDialogNotesState(val visibleMarkdown: String, val hiddenLineCount: Int)

/** 顶部唯一的一行版本信息：从哪个版本到哪个版本，附发布日期 */
internal fun updateDialogSummary(
    currentVersion: String,
    release: UpdateChecker.Release,
): String {
    val jump = updateVersionJump(currentVersion, release)
    val date = updateDisplayDate(release.publishedAt)
    return if (date.isBlank()) jump else "$jump · $date"
}

/** 版本跃迁文案（当前 → 最新），摘要与下载页头部共用这一句 */
internal fun updateVersionJump(
    currentVersion: String,
    release: UpdateChecker.Release,
): String = AppStrings.get(R.string.vm_update_version_jump, updateDisplayVersion(currentVersion), updateDisplayVersion(release.tagName))

/** 版本号展示统一去掉前导 v：GitHub tag 习惯写 v1.3.0，界面统一显示 1.3.0 */
internal fun updateDisplayVersion(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

/** 发布日期只取 ISO 时间里的日期段；不是 ISO 格式就原样展示 */
private fun updateDisplayDate(raw: String): String {
    val trimmed = raw.trim()
    val looksIso = trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-'
    return if (looksIso) trimmed.take(10) else trimmed
}

/** 折叠态取前 N 行；空白说明与短说明都不显示「展开全部」 */
internal fun updateDialogNotesState(
    markdown: String,
    expanded: Boolean,
    previewLines: Int,
): UpdateDialogNotesState {
    if (markdown.isBlank()) {
        return UpdateDialogNotesState("", 0)
    }
    if (expanded) {
        return UpdateDialogNotesState(markdown, 0)
    }
    val lines = markdown.lines()
    val limit = previewLines.coerceAtLeast(1)
    return UpdateDialogNotesState(
        visibleMarkdown = lines.take(limit).joinToString("\n"),
        hiddenLineCount = (lines.size - limit).coerceAtLeast(0),
    )
}

/** 下载明细行：已下载 / 总大小 · 速度；总长未知时不伪造百分比 */
internal fun updateDialogProgressDetail(state: UpdateDialogState.Downloading): String {
    val downloaded = ByteFormat.size(state.downloaded)
    val speed = if (state.speed > 0L) " · ${ByteFormat.speed(state.speed)}" else ""
    return if (state.total > 0L) {
        AppStrings.get(
            R.string.vm_update_progress_detail,
            downloaded,
            ByteFormat.size(state.total),
            speed
        )
    } else {
        AppStrings.get(R.string.vm_update_progress_detail_unknown, downloaded, speed)
    }
}

/** 下载百分比（含 % 后缀）；总长未知时返回空串 */
internal fun updateDialogProgressPercent(state: UpdateDialogState.Downloading): String =
    if (state.total > 0L) {
        "${(updateDownloadFraction(state.downloaded, state.total) * 100).toInt()}%"
    } else {
        ""
    }

/** 主按钮文案：下载中把百分比放进按钮本身，用户不必回下载页就能看到进度 */
internal fun updateDialogPrimaryText(
    hasApk: Boolean,
    state: UpdateDialogState,
): String = when {
    !hasApk -> AppStrings.get(R.string.vm_update_open_releases)
    state is UpdateDialogState.Downloading -> {
        val percent = updateDialogProgressPercent(state)
        if (percent.isEmpty()) AppStrings.get(R.string.ed_notif_status_downloading) else AppStrings.get(R.string.vm_update_downloading_percent, percent)
    }
    state is UpdateDialogState.Done -> AppStrings.get(R.string.vm_update_install)
    state is UpdateDialogState.Failed -> AppStrings.get(R.string.csa_retry)
    else -> AppStrings.get(R.string.vm_update_download_now)
}

/** 次按钮文案：下载中写「后台下载」，明确关掉弹窗不会中断任务 */
internal fun updateDialogDismissText(state: UpdateDialogState): String =
    if (state is UpdateDialogState.Downloading) AppStrings.get(R.string.vm_update_background_download) else AppStrings.get(R.string.vm_update_later)

/**
 * 按下载态推导整份弹窗计划。
 *
 * 动作只有主次两级：主按钮负责「立即下载 / 安装更新 / 前往 Releases」，
 * 次按钮是「稍后（下载中为后台下载）」「取消下载（仅下载中）」；
 * 「忽略此版本」降级为说明下方的文字链，不与主操作并列。
 */
internal fun buildUpdateDialogPlan(
    currentVersion: String,
    release: UpdateChecker.Release,
    downloadState: UpdateDialogState,
    canIgnore: Boolean,
    canCancelDownload: Boolean,
    canMirror: Boolean,
): UpdateDialogPlan {
    val hasApk = release.apkAsset != null
    val downloading = downloadState as? UpdateDialogState.Downloading
    val done = downloadState is UpdateDialogState.Done
    val failed = downloadState is UpdateDialogState.Failed
    return UpdateDialogPlan(
        summary = updateDialogSummary(currentVersion, release),
        primaryAction = when {
            !hasApk -> UpdatePrimaryAction.OPEN_RELEASE
            done -> UpdatePrimaryAction.INSTALL
            else -> UpdatePrimaryAction.DOWNLOAD
        },
        primaryText = updateDialogPrimaryText(hasApk, downloadState),
        // 下载中主按钮禁用并显示百分比；其余状态（含安装、重试）都能点
        primaryEnabled = downloading == null,
        dismissText = updateDialogDismissText(downloadState),
        showProgress = downloading != null,
        progressFraction = downloading?.let {
            if (it.total > 0L) updateDownloadFraction(it.downloaded, it.total) else null
        },
        progressDetail = downloading?.let(::updateDialogProgressDetail),
        showCancelDownload = downloading != null && canCancelDownload,
        // 「转到下载任务」是本项目核心能力：只要有下载任务就给入口
        showOpenDownloadPage = downloading != null || done || failed,
        // 忽略只属于「还没开始下」这一档，进入下载流程后不再提供
        showIgnore = canIgnore && downloadState is UpdateDialogState.Idle,
        showMirror = canMirror && downloading == null && !done,
        hasNotes = release.body.isNotBlank(),
    )
}
