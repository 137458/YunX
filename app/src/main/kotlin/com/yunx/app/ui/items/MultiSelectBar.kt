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

package com.yunx.app.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 多选底部批量操作项。
 *
 * @param destructive 危险操作（删除等）用 error 角色色呈现；
 *   不能用 label 字符串判断——改文案或做多语言时会立刻失效。
 */
internal data class MultiSelectAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color = Color.Unspecified,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 多选模式悬浮胶囊工具栏（Miuix FloatingToolbar 架构）。
 * 放置全选、下载、移动、删除（错误色）、转存等操作按钮。
 *
 * @param busy 批量操作执行中：禁用全部按钮，防止连点重复入队
 */
@Composable
internal fun MultiSelectBar(
    actions: List<MultiSelectAction>,
    busy: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingToolbar(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MiuixTheme.colorScheme.surfaceContainer,
            cornerRadius = FloatingToolbarDefaults.CornerRadius,
            outSidePadding = PaddingValues(0.dp),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions.forEach { action ->
                    val itemTint = when {
                        action.destructive -> MiuixTheme.colorScheme.error
                        action.tint != Color.Unspecified -> action.tint
                        else -> MiuixTheme.colorScheme.primary
                    }
                    val textTint = if (action.destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantActions

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            // 保证 48dp 命中区：2 字标签时实际宽度约 44dp，低于无障碍下限
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !busy, onClick = action.onClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .alpha(if (busy) 0.38f else 1f)
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = itemTint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = action.label,
                            style = MiuixTheme.textStyles.footnote2,
                            color = textTint
                        )
                    }
                }
            }
        }
    }
}

/**
 * 多选模式标准悬浮胶囊工具栏快捷重载：
 * 预置全选、下载、移动、转存、删除（错误色）。
 */
@Composable
internal fun MultiSelectBar(
    onSelectAll: (() -> Unit)? = null,
    isAllSelected: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    busy: Boolean = false,
    modifier: Modifier = Modifier
) {
    val actions = buildList {
        if (onSelectAll != null) {
            add(
                MultiSelectAction(
                    label = if (isAllSelected) "取消全选" else "全选",
                    icon = if (isAllSelected) Icons.Outlined.CheckCircle else Icons.Outlined.SelectAll,
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onSelectAll
                )
            )
        }
        if (onDownload != null) {
            add(
                MultiSelectAction(
                    label = "下载",
                    icon = Icons.Outlined.Download,
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onDownload
                )
            )
        }
        if (onMove != null) {
            add(
                MultiSelectAction(
                    label = "移动",
                    icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onMove
                )
            )
        }
        if (onSave != null) {
            add(
                MultiSelectAction(
                    label = "转存",
                    icon = Icons.Outlined.SaveAlt,
                    tint = MiuixTheme.colorScheme.primary,
                    onClick = onSave
                )
            )
        }
        if (onDelete != null) {
            add(
                MultiSelectAction(
                    label = "删除",
                    icon = Icons.Outlined.Delete,
                    tint = MiuixTheme.colorScheme.error,
                    destructive = true,
                    onClick = onDelete
                )
            )
        }
    }
    MultiSelectBar(actions = actions, busy = busy, modifier = modifier)
}
