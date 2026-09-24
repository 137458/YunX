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

package com.yunx.app.ui.drive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.components.BreadcrumbBar
import com.yunx.app.ui.components.BreadcrumbItem
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.ShareFileRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 目录选择器统一度量。
 *
 * 移动抽屉原来百度/UC/迅雷/C139/123 用 180/140/90/240，夸克用 200/160/100/260，
 * 差异没有功能含义，统一取前者（各态都更紧凑，小屏上更少出现整屏滚动）。
 */
object DrivePickerDimens {
    val loading: Dp = 180.dp
    val error: Dp = 140.dp
    val empty: Dp = 90.dp
    val listMax: Dp = 240.dp
}

/**
 * 移动/转存共用的目录选择器。
 *
 * 结构：步骤头部 → 面包屑 → 返回上一级 → 内容（加载/错误/空/列表）→ 确认按钮。
 * 加载失败必须给「重试」出口（[onRetry]），否则用户既看不到目录也无法继续，形成死胡同。
 */
@Composable
fun DriveDirPicker(
    title: String,
    subtitle: String,
    state: CloudDriveUiState,
    onBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
    onBackToParent: () -> Unit,
    onOpenFolder: (com.yunx.app.data.network.model.ShareFile) -> Unit,
    onRetry: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    emptyText: String = "当前目录没有子文件夹，可直接移动到此处",
    contentLabel: String = "moveState"
) {
    val loaded = state as? CloudDriveUiState.Loaded
    val pathNames = loaded?.pathNames ?: emptyList()

    // 内边距由调用方提供（抽屉内容统一 24/32dp）
    Column(modifier = modifier.fillMaxWidth()) {
        CloudStepHeader(title = title, subtitle = subtitle, onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        BreadcrumbBar(
            items = remember(pathNames) { pathNames.toBreadcrumbs() },
            onItemClick = onNavigateToLevel,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (pathNames.isNotEmpty()) {
            BackToParentItem(onClick = onBackToParent)
            Spacer(modifier = Modifier.height(4.dp))
        }
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = contentLabel
        ) { s ->
            when (s) {
                is CloudDriveUiState.Loading -> DrivePickerLoadingPane()

                is CloudDriveUiState.Error -> DrivePickerErrorPane(
                    message = s.message,
                    onRetry = onRetry
                )

                is CloudDriveUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                    if (dirs.isEmpty()) {
                        DrivePickerEmptyPane(text = emptyText)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = DrivePickerDimens.listMax),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dirs, key = { it.fid }) { dir ->
                                ShareFileRow(file = dir, onClick = { onOpenFolder(dir) })
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        val dirName = pathNames.lastOrNull() ?: "根目录"
        Button(
            onClick = onConfirm,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (loading) {
                InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = DriveIcons.move,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(confirmText.format(dirName), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 目录选择器加载态 */
@Composable
fun DrivePickerLoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DrivePickerDimens.loading),
        contentAlignment = Alignment.Center
    ) {
        InfiniteProgressIndicator()
    }
}

/** 目录选择器空态 */
@Composable
fun DrivePickerEmptyPane(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DrivePickerDimens.empty),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 目录选择器加载失败态。
 *
 * 必须给「重试」出口：选择器失败后用户既看不到目录也无法继续，只有一行错误文本会变成死胡同。
 */
@Composable
fun DrivePickerErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DrivePickerDimens.error
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(text = "重试", onClick = onRetry)
        }
    }
}

/**
 * 转存抽屉的加载态：转存浏览复用的是云盘主页状态，量级与移动选择器一致，
 * 但只展示进度不展示按钮，所以单独给一个固定高度的进度行。
 */
@Composable
fun DriveSaveLoadingPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        InfiniteProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = MiuixTheme.colorScheme.primary
        )
    }
}

/** 转存抽屉的空目录态 */
@Composable
fun DriveSaveEmptyPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "当前目录没有子文件夹，可直接转存到此目录",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )
    }
}

/** 转存抽屉的错误态（同样必须带重试） */
@Composable
fun DriveSaveErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(text = "重试", onClick = onRetry)
        }
    }
}

/** 目录层级 → 面包屑项（根节点固定为「根目录」） */
internal fun List<String>.toBreadcrumbs(): List<BreadcrumbItem> = buildList {
    add(BreadcrumbItem(path = "root", text = "根目录"))
    this@toBreadcrumbs.forEachIndexed { index, name ->
        add(BreadcrumbItem(path = (index + 1).toString(), text = name))
    }
}

/** 步骤头部：返回按钮 + 标题 + 副标题 */
@Composable
fun CloudStepHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MiuixTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
