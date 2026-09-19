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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.theme.SquircleShape
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 浮动操作菜单项：标签 + 图标 + 可选选中态。
 */
data class FabMenuItem(
    val label: String,
    val icon: ImageVector,
    val isSelected: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 浮动操作菜单（云析原创实现，Miuix 风格）：
 * - 右下角 FAB，点击展开/收起菜单，图标伴随旋转动画（＋ → ✕）；
 * - 菜单项从 FAB 上方滑入淡出，选中项高亮并带勾选标记；
 * - 展开时点击菜单外区域自动收起；
 * - 可选 visible 控制整体显隐。
 */
@Composable
fun BoxScope.CustomFabMenu(
    expanded: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    items: List<FabMenuItem>,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    // FAB 图标旋转：展开时 ＋ 旋转 90° 变为 ✕
    val fabRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "fabRotation"
    )

    // 展开时覆盖全屏的透明点击层：点击菜单外区域收起
    if (expanded) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onCheckedChange(false) }
        )
    }

    Column(
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Bottom
    ) {
        // ---------- 菜单项 ----------
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                items.forEach { item ->
                    FloatingActionButton(
                        onClick = {
                            item.onClick()
                            onCheckedChange(false)
                        },
                        shape = SquircleShape(16.dp),
                        containerColor = if (item.isSelected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.surfaceContainer
                        },
                        shadowElevation = if (item.isSelected) 4.dp else 2.dp,
                        minWidth = 48.dp,
                        minHeight = 44.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (item.isSelected) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = item.label,
                                style = MiuixTheme.textStyles.button,
                                fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (item.isSelected) {
                                    MiuixTheme.colorScheme.onPrimary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                }
                            )
                            if (item.isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "已选择",
                                    modifier = Modifier.size(16.dp),
                                    tint = MiuixTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 主 FAB ----------
        AnimatedVisibility(visible = visible) {
            FloatingActionButton(
                onClick = { onCheckedChange(!expanded) },
                modifier = Modifier.size(52.dp),
                shape = SquircleShape(18.dp),
                containerColor = MiuixTheme.colorScheme.primary,
                minWidth = 52.dp,
                minHeight = 52.dp
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = if (expanded) "关闭菜单" else "打开菜单",
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = fabRotation }
                )
            }
        }
    }
}