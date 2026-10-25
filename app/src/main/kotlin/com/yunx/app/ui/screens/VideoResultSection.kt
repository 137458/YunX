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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.video.VideoMedia
import com.yunx.app.data.network.video.VideoMediaKind
import com.yunx.app.data.network.video.VideoParseResult
import com.yunx.app.ui.components.RemoteImage
import com.yunx.app.ui.preview.MediaPlayerPane
import com.yunx.app.ui.preview.PreviewKind
import com.yunx.app.ui.preview.PreviewLauncher
import com.yunx.app.ui.preview.PreviewMedia
import com.yunx.app.ui.resolve.formatSize
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
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.title.ifBlank { "解析结果" },
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "返回",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            VideoMetaBlock(result = result)

            VideoMediaListLayout(
                result = result,
                onDownloadMedia = onDownloadMedia
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.title.ifBlank { "图集解析" },
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "返回",
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
                text = if (selectedCount == totalCount) "取消全选" else "全选",
                onClick = onToggleSelectAll
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onDownloadSelected,
                enabled = selectedCount > 0
            ) {
                Text(if (selectedCount > 0) "下载选中（$selectedCount）" else "下载选中")
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
                    text = "实况",
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
                    shape = RoundedCornerShape(50),
                    color = MiuixTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已选中",
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
        shape = RoundedCornerShape(50),
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
            Text("全部下载（${result.medias.size} 项）")
        }
    }
}

/** 作品信息块：作者、发布时间、正文（超长折叠，点击展开） */
@Composable
private fun VideoMetaBlock(result: VideoParseResult, modifier: Modifier = Modifier) {
    val metaLine = buildList {
        if (result.author.isNotBlank()) add(result.author)
        val date = formatVideoDate(result.timestampSeconds)
        if (date.isNotBlank()) add(date)
        if (result.medias.isNotEmpty()) add("共 ${result.medias.size} 项")
    }.joinToString("  ·  ")

    Column(modifier = modifier.padding(bottom = 12.dp)) {
        if (metaLine.isNotBlank()) {
            Text(
                text = metaLine,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        if (result.text.isNotBlank()) {
            var expanded by remember(result.text) { mutableStateOf(false) }
            val canExpand = result.text.length > 60
            Text(
                text = result.text,
                style = MiuixTheme.textStyles.body2,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = if (metaLine.isNotBlank()) 6.dp else 0.dp)
                    .clickable(enabled = canExpand) { expanded = !expanded }
            )
            if (canExpand) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { expanded = !expanded }
                )
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
            .clip(RoundedCornerShape(16.dp)),
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

/** 单条媒体行：Miuix 原生 Row 布局 */
@Composable
private fun VideoMediaRow(
    index: Int,
    media: VideoMedia,
    onDownload: () -> Unit,
    onPreview: () -> Unit
) {
    val subtitle = buildList {
        if (media.quality.isNotBlank()) add(media.quality)
        if (media.size > 0) add(formatSize(media.size))
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
                    contentDescription = "预览",
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
            TextButton(
                text = "下载",
                onClick = onDownload
            )
        }
    }
}

/** 媒体类型中文标签 */
private fun videoMediaKindLabel(kind: VideoMediaKind): String = when (kind) {
    VideoMediaKind.VIDEO -> "视频"
    VideoMediaKind.IMAGE -> "图片"
    VideoMediaKind.AUDIO -> "音频"
}

/** 发布时间戳（秒）→ yyyy-MM-dd */
private fun formatVideoDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) return ""
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestampSeconds * 1000L))
    }.getOrDefault("")
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
