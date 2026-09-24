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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.CrumbBar
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.theme.SquircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 转存抽屉通用件：浏览目标网盘目录，确认后把解析出的分享内容转存到当前目录。
 *
 * 六个转存抽屉原本逐字重复（含 Loading/Error/Empty 三态），这里统一；
 * 各平台差异只在标题文案与「当前目录标识」的取值方式，由包装层以 lambda 传入。
 */
@Composable
fun CloudPickerSheet(
    title: String,
    targetName: String,
    state: CloudDriveUiState,
    onDismiss: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenFolder: (ShareFile) -> Unit,
    onRetry: () -> Unit,
    saving: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    contentLabel: String = "saveState"
) {
    val loaded = state as? CloudDriveUiState.Loaded
    val pathNames = loaded?.pathNames ?: emptyList()

    OverlayBottomSheet(
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = SquircleShape(12.dp),
                    color = MiuixTheme.colorScheme.primaryVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SaveAlt,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimaryVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = targetName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            CrumbBar(
                rootTitle = "根目录",
                pathNames = pathNames,
                onNavigate = onNavigateToLevel
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (pathNames.isNotEmpty()) {
                BackToParentItem(onClick = onBack)
                Spacer(modifier = Modifier.height(4.dp))
            }

            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = contentLabel
            ) { s ->
                when (s) {
                    is CloudDriveUiState.Loading -> DriveSaveLoadingPane()

                    is CloudDriveUiState.Error -> DriveSaveErrorPane(
                        message = s.message,
                        onRetry = onRetry
                    )

                    is CloudDriveUiState.Loaded -> {
                        val dirs = s.files.filter { it.isdir }
                        if (dirs.isEmpty()) {
                            DriveSaveEmptyPane()
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
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

            Spacer(modifier = Modifier.height(20.dp))

            val currentDirName = pathNames.lastOrNull() ?: "根目录"
            Button(
                onClick = onConfirm,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (saving) {
                    InfiniteProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("转存到此目录（$currentDirName）", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}
