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

package com.yunx.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面骨架的三态之一。只描述"该渲染什么"，不持有业务数据，
 * 因此可以被页面 ViewModel 之外的任意层构造与比较（data class/object 均有结构相等性）。
 */
sealed interface UiPaneState {
    /** 加载中：显示进度指示器与一行提示。 */
    data object Loading : UiPaneState

    /** 空数据：标题 + 可选说明/图标/行动按钮。 */
    data class Empty(
        val title: String,
        val description: String? = null,
        val icon: ImageVector? = null,
        val actionText: String? = null
    ) : UiPaneState

    /** 失败：错误文案 + 可选重试/退出。 */
    data class Error(
        val message: String,
        val canRetry: Boolean = false,
        val canExit: Boolean = false
    ) : UiPaneState

    /** 有内容：骨架不再渲染占位，由调用方继续追加真实 item。 */
    data object Content : UiPaneState
}

/** 加载中占位：居中无限进度指示器 + 提示文案。 */
@Composable
fun LoadingPane(
    message: String = "加载中…",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 空态占位：图标（可选）+ 标题 + 说明（可选）+ 行动按钮（可选）。
 *
 * 只有在同时给出 actionText 与 onAction 时才渲染按钮：只给文案不给回调会变成点不动的假按钮。
 */
@Composable
fun EmptyPane(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center
                )
            }
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(onClick = onAction, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(text = actionText, style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/**
 * 错误态占位：错误文案 + 可选「重试」「返回」。
 *
 * 错误文案直接来自异常 message，可能包含 URL/JSON 而极长：
 * 这里限 8 行 + 省略号，并允许纵向滚动，避免撑破屏幕或把按钮顶出可视区。
 */
@Composable
fun ErrorPane(
    message: String,
    onRetry: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            if (onRetry != null || onExit != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onExit != null) {
                        Button(onClick = onExit) {
                            Text(text = "返回", style = MiuixTheme.textStyles.button)
                        }
                    }
                    if (onRetry != null) {
                        Button(onClick = onRetry, colors = ButtonDefaults.buttonColorsPrimary()) {
                            Text(text = "重试", style = MiuixTheme.textStyles.button)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把三态作为 LazyList 的 item 参与懒加载。
 *
 * 为什么不是一个包住整列的 Column：占位态一旦塞进单个 item，列表的复用、滚动位置与
 * item 级动画全部失效；拆成 lazy item 后，占位态与真实内容共用同一条懒加载管线。
 *
 * Content 态不产生任何 item，调用方在它之后继续 items(...) 追加真实内容。
 */
fun LazyListScope.stateItem(
    state: UiPaneState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null
) {
    when (state) {
        is UiPaneState.Loading -> item(key = "pane-loading") {
            LoadingPane(modifier = modifier.fillMaxWidth())
        }

        is UiPaneState.Empty -> item(key = "pane-empty") {
            EmptyPane(
                title = state.title,
                description = state.description,
                icon = state.icon,
                actionText = state.actionText,
                onAction = onAction,
                modifier = modifier.fillMaxWidth()
            )
        }

        is UiPaneState.Error -> item(key = "pane-error") {
            ErrorPane(
                message = state.message,
                onRetry = if (state.canRetry) onRetry else null,
                onExit = if (state.canExit) onExit else null,
                modifier = modifier.fillMaxWidth()
            )
        }

        is UiPaneState.Content -> Unit
    }
}
