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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.video.VideoMedia
import com.yunx.app.data.network.video.VideoMediaKind
import com.yunx.app.data.network.video.VideoParseResult
import com.yunx.app.R
import com.yunx.app.ui.components.RemoteImage
import com.yunx.app.ui.preview.MediaPlayerPane
import com.yunx.app.ui.preview.PreviewKind
import com.yunx.app.ui.preview.PreviewLauncher
import com.yunx.app.ui.preview.PreviewMedia
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import com.yunx.app.util.ByteFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 视频解析结果展示区（已全量适配 Miuix 规范）：
 *
 * 1. 结果元信息（标题 / 作者 / 正文，超长可折叠）
 * 2. 媒体列表：
 *    - 纯视频且仅 1 项：顶部内嵌视频播放器（默认静音/停住）+ 媒体行
 *    - 图文（>3 张图）：瀑布流缩略图网格 + 长按多选 + 底部批量下载栏
 *    - 其他（多视频 / 音频）：Miuix Card 分组媒体列表
 * 3. 底部固定「全部下载」或「批量下载」按钮
 */
@Composable
fun VideoResultSection(
    result: VideoParseResult,
    onDownloadMedia: (VideoMedia) -> Unit,
    /** 「仅下载音频」：产物是 `.m4a`，对视频行才显示入口 */
    onDownloadAudio: (VideoMedia) -> Unit,
    onDownloadAll: () -> Unit,
    onDownloadSelected: (List<VideoMedia>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 媒体布局形态决策：图集且图片数量 > 3 张时走网格 + 批量模式；其余走列表模式
    val isPhotoGallery = remember(result) {
        val images = result.medias.filter { it.kind == VideoMediaKind.IMAGE }
        images.size > 3 && images.size == result.medias.size
    }

    if (isPhotoGallery) {
        VideoMediaGridLayout(
            result = result,
            onDownloadMedia = onDownloadMedia,
            onDownloadSelected = onDownloadSelected,
            onBack = onBack,
            modifier = modifier
        )
    } else {
        BackHandler { onBack() }
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.title.ifBlank { AppStrings.get(R.string.vm_video_result_title) },
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.get(R.string.csa_back),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            VideoMetaBlock(result = result)

            VideoMediaListLayout(
                result = result,
                onDownloadMedia = onDownloadMedia,
                onDownloadAudio = onDownloadAudio
            )

            Spacer(modifier = Modifier.height(16.dp))
            DownloadAllBar(
                result = result,
                onDownloadAll = onDownloadAll
            )
        }
    }
}

/** 列表布局：1 条视频内联播放器，或多条媒体统一由 Miuix Card 呈现 */
@Composable
private fun VideoMediaListLayout(
    result: VideoParseResult,
    onDownloadMedia: (VideoMedia) -> Unit,
    onDownloadAudio: (VideoMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstVideo = firstVideoOf(result)
    if (result.medias.size == 1 && firstVideo != null) {
        Column(modifier = modifier.fillMaxWidth()) {
            InlineVideoPlayer(
                media = firstVideo.toPreviewMedia(result.title),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                VideoMediaRow(
                    index = 0,
                    media = firstVideo,
                    onDownload = { onDownloadMedia(firstVideo) },
                    onDownloadAudio = { onDownloadAudio(firstVideo) },
                    onPreview = {
                        openMediaPreview(
                            result = result,
                            group = listOf(firstVideo),
                            clicked = firstVideo,
                            onDownloadMedia = onDownloadMedia
                        )
                    }
                )
            }
        }
    } else {
        Card(modifier = modifier.fillMaxWidth()) {
            result.medias.forEachIndexed { index, media ->
                VideoMediaRow(
                    index = index,
                    media = media,
                    onDownload = { onDownloadMedia(media) },
                    onDownloadAudio = { onDownloadAudio(media) },
                    onPreview = {
                        openMediaPreview(
                            result = result,
                            group = listOf(media),
                            clicked = media,
                            onDownloadMedia = onDownloadMedia
                        )
                    }
                )
            }
        }
    }
}

/** 图集网格布局：3 列缩略图 + 长按多选 + 底部批量下载栏 */
@Composable
private fun VideoMediaGridLayout(
    result: VideoParseResult,
    onDownloadMedia: (VideoMedia) -> Unit,
    onDownloadSelected: (List<VideoMedia>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedMedias = remember { mutableStateListOf<VideoMedia>() }

    fun toggleSelect(media: VideoMedia) {
        if (selectedMedias.any { it === media }) {
            selectedMedias.removeAll { it === media }
            if (selectedMedias.isEmpty()) multiSelectMode = false
        } else {
            selectedMedias.add(media)
        }
    }

    fun toggleSelectAll() {
        if (selectedMedias.size == result.medias.size) {
            selectedMedias.clear()
            multiSelectMode = false
        } else {
            selectedMedias.clear()
            selectedMedias.addAll(result.medias)
            multiSelectMode = true
        }
    }

    BackHandler {
        if (multiSelectMode) {
            selectedMedias.clear()
            multiSelectMode = false
        } else {
            onBack()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.title.ifBlank { AppStrings.get(R.string.vm_video_gallery_title) },
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get(R.string.csa_back),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        VideoMetaBlock(result = result)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(result.medias, key = { it.url.ifBlank { it.hashCode().toString() } }) { media ->
                val isSelected = selectedMedias.any { it === media }
                MediaGridCell(
                    media = media,
                    multiSelectMode = multiSelectMode,
                    selected = isSelected,
                    onClick = {
                        if (multiSelectMode) {
                            toggleSelect(media)
                        } else {
                            openMediaPreview(
                                result = result,
                                group = result.medias,
                                clicked = media,
                                onDownloadMedia = onDownloadMedia
                            )
                        }
                    },
                    onLongClick = {
                        if (!multiSelectMode) {
                            multiSelectMode = true
                            toggleSelect(media)
                        }
                    },
                    onToggleSelect = { toggleSelect(media) }
                )
            }
            if (!multiSelectMode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DownloadAllBar(
                        result = result,
                        onDownloadAll = { result.medias.forEach(onDownloadMedia) }
                    )
                }
            }
        }

        if (multiSelectMode) {
            BatchDownloadBar(
                totalCount = result.medias.size,
                selectedCount = selectedMedias.size,
                onToggleSelectAll = { toggleSelectAll() },
                onDownloadSelected = {
                    val batch = selectedMedias.toList()
                    selectedMedias.clear()
                    multiSelectMode = false
                    onDownloadSelected(batch)
                }
            )
        }
    }
}

/** 批量下载底栏 */
@Composable
private fun BatchDownloadBar(
    totalCount: Int,
    selectedCount: Int,
    onToggleSelectAll: () -> Unit,
    onDownloadSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = if (selectedCount == totalCount) AppStrings.get(R.string.csa_deselect_all) else AppStrings.get(R.string.csa_select_all),
                onClick = onToggleSelectAll
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onDownloadSelected,
                enabled = selectedCount > 0
            ) {
                Text(
                    if (selectedCount > 0) AppStrings.get(R.string.vm_video_download_selected_count, selectedCount)
                    else AppStrings.get(R.string.vm_video_download_selected)
                )
            }
        }
    }
}

/** 单张网格图片瓦片 */
@Composable
private fun MediaGridCell(
    media: VideoMedia,
    multiSelectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: (VideoMedia) -> Unit
) {
    val thumbUrl = media.url
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.defaultColors()
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(media) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
        ) {
            RemoteImage(
                url = thumbUrl,
                contentDescription = media.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (media.motionUrl.isNotBlank()) {
                CellBadge(
                    text = AppStrings.get(R.string.vm_video_motion_badge),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            if (multiSelectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSelect(media) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        state = ToggleableState(selected),
                        onClick = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (selected && !multiSelectMode) {
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = AppStrings.get(R.string.content_desc_selected),
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** 缩略图角标（半透明胶囊） */
@Composable
private fun CellBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MiuixTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

/** 底部固定的「全部下载」 */
@Composable
private fun DownloadAllBar(result: VideoParseResult, onDownloadAll: () -> Unit) {
    if (result.medias.isEmpty()) return
    Surface(color = MiuixTheme.colorScheme.surfaceContainer) {
        Button(
            onClick = onDownloadAll,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(48.dp)
        ) {
            Text(AppStrings.get(R.string.sh_download_all_count, result.medias.size))
        }
    }
}

/** 作品信息块：作者、发布时间、正文/文案（超长折叠，点击展开，可一键复制原文） */
@Composable
private fun VideoMetaBlock(result: VideoParseResult, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 元信息行：作者 / 发布时间（时间戳为 0 表示未知，不显示）
    val metaLine = buildList {
        if (result.author.isNotBlank()) add(result.author)
        val date = formatVideoDate(result.timestampSeconds)
        if (date.isNotBlank()) add(date)
        if (result.medias.isNotEmpty()) add(AppStrings.get(R.string.csa_total_count, result.medias.size))
    }.joinToString("  ·  ")

    Column(modifier = modifier.padding(bottom = 12.dp)) {
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        // 文案：抖音/快手的分享文案、小红书笔记正文、B站简介都落在这里。
        // 个别平台的「文案」就是标题本身（解析器把它同时写进 text），此时仍照常展示，
        // 因为顶栏标题最多两行、必然会被截断，这里才是完整的原文。
        val caption = result.text.ifBlank { result.title }
        if (caption.isNotBlank()) {
            var expanded by remember(caption) { mutableStateOf(false) }
            val canExpand = caption.length > 60
            Text(
                text = caption,
                style = MiuixTheme.textStyles.body2,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = if (metaLine.isNotBlank()) 6.dp else 0.dp)
                    .clickable(enabled = canExpand) { expanded = !expanded }
            )
            Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canExpand) {
                    Text(
                        text = if (expanded) AppStrings.get(R.string.dl_collapse) else AppStrings.get(R.string.dl_expand),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clickable { expanded = !expanded }
                    )
                }
                // 复制原文而不是折叠后的显示文本：长文案正是最需要复制出去的（上游 v1.3.1 新增）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { copyVideoCaption(context, caption) }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.get(R.string.sh_copy_caption),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

/** 结果里的第一条视频 */
private fun firstVideoOf(result: VideoParseResult): VideoMedia? =
    result.medias.firstOrNull { it.kind == VideoMediaKind.VIDEO && it.url.isNotBlank() }

/** 内联视频播放器 */
@Composable
private fun InlineVideoPlayer(media: PreviewMedia, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(SquircleShape(16.dp)),
        color = MiuixTheme.colorScheme.surfaceContainer
    ) {
        MediaPlayerPane(
            media = media,
            active = false,
            onFullscreenChanged = {},
            allowFullscreen = false,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 单条媒体行：Miuix 原生 Row 布局（视频行额外给「仅下载音频」入口） */
@Composable
private fun VideoMediaRow(
    index: Int,
    media: VideoMedia,
    onDownload: () -> Unit,
    onDownloadAudio: () -> Unit,
    onPreview: () -> Unit
) {
    val subtitle = buildList {
        if (media.quality.isNotBlank()) add(media.quality)
        if (media.size > 0) add(ByteFormat.size(media.size))
    }.joinToString("  ·  ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${videoMediaKindLabel(media.kind)}",
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreview, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = AppStrings.get(R.string.rs_preview),
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
            // 仅下载音频（上游 v1.3.1 新增）：只有视频才有音轨（图片没有，音频本身就是音频）
            if (media.kind == VideoMediaKind.VIDEO) {
                IconButton(onClick = onDownloadAudio, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = AppStrings.get(R.string.sh_download_audio_only),
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
            }
            TextButton(
                text = AppStrings.get(R.string.csa_download),
                onClick = onDownload
            )
        }
    }
}

/** 媒体类型中文标签 */
private fun videoMediaKindLabel(kind: VideoMediaKind): String = when (kind) {
    VideoMediaKind.VIDEO -> AppStrings.get(R.string.rs_category_video)
    VideoMediaKind.IMAGE -> AppStrings.get(R.string.rs_category_image)
    VideoMediaKind.AUDIO -> AppStrings.get(R.string.sh_media_audio)
}

/** 发布时间戳（秒）→ yyyy-MM-dd */
private fun formatVideoDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
    }.getOrDefault("")
}

/**
 * 复制作品文案（复制的是**完整原文**，不是折叠后的显示文本 —— 长文案正是最需要复制出去的）。
 *
 * 走系统剪贴板 + 全局 Snackbar，与本应用其它「已复制」入口保持一致；Android 13 起系统自己会弹
 * 复制预览，这里仍给一句提示（系统提示可能被用户关掉）。
 */
private fun copyVideoCaption(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching {
        clipboard.setPrimaryClip(ClipData.newPlainText("yunx_video_caption", text))
    }.onSuccess {
        SnackbarController.show(AppStrings.get(R.string.sh_caption_copied))
    }
}

/** 点「预览」/点缩略图 */
private fun openMediaPreview(
    result: VideoParseResult,
    group: List<VideoMedia>,
    clicked: VideoMedia,
    onDownloadMedia: (VideoMedia) -> Unit
) {
    val source = group.filter { it.url.isNotBlank() }
    if (source.isEmpty()) return
    val startIndex = source.indexOfFirst { it === clicked }
    PreviewLauncher.openMedia(
        items = source.map { it.toPreviewMedia(result.title) },
        startIndex = if (startIndex >= 0) startIndex else 0,
        title = result.title.ifBlank { clicked.fileName },
        onDownload = { index -> source.getOrNull(index)?.let(onDownloadMedia) }
    )
}

/** VideoMedia → PreviewMedia */
private fun VideoMedia.toPreviewMedia(workTitle: String): PreviewMedia {
    val playMotion = motionUrl.isNotBlank()
    return PreviewMedia(
        url = if (playMotion) motionUrl else url,
        kind = when {
            playMotion -> PreviewKind.VIDEO
            kind == VideoMediaKind.VIDEO -> PreviewKind.VIDEO
            kind == VideoMediaKind.IMAGE -> PreviewKind.IMAGE
            else -> PreviewKind.AUDIO
        },
        title = fileName.ifBlank { workTitle },
        headers = headers,
        isHls = isHls,
        audioUrl = audioUrl,
        audioHeaders = audioHeaders
    )
}
