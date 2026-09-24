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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 超过该字符数即视为长文本（与行数判定取或）。 */
private const val LONG_TEXT_CHAR_THRESHOLD = 200

/**
 * 可折叠长文本。
 *
 * 短文本不出现展开按钮：判定同时看「实际行数 > collapsedMaxLines」与「字符数 > 200」，
 * 行数判定覆盖了短字符数但被自动换行撑开的情况，字符数判定覆盖了单行超长（行数=1）的情况。
 *
 * @param selectable 为真时包一层 SelectionContainer，允许用户长按复制
 */
@Composable
fun CollapsibleText(
    text: String,
    collapsedMaxLines: Int = 4,
    modifier: Modifier = Modifier,
    style: TextStyle = MiuixTheme.textStyles.body2,
    color: Color = MiuixTheme.colorScheme.onSurface,
    selectable: Boolean = true
) {
    // 行数由 Text 的布局回调上报：只有真正布局过才知道自动换行后的行数
    var laidOutLines by remember(text) { mutableStateOf(0) }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }

    val overflowedByLines = laidOutLines > collapsedMaxLines
    val overflowedByLength = text.length > LONG_TEXT_CHAR_THRESHOLD
    val isLongText = overflowedByLines || overflowedByLength

    Column(modifier = modifier) {
        val content: @Composable () -> Unit = {
            Column(modifier = Modifier.animateContentSize()) {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    // 只在行数真正变化时写状态：布局回调可能连续触发，
                    // 无脑赋值会在动画期间造成"布局->重组->布局"的抖动
                    onTextLayout = { result ->
                        if (laidOutLines != result.lineCount) laidOutLines = result.lineCount
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLongText) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (expanded) "收起" else "展开",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
            }
        }
        if (selectable) {
            SelectionContainer { content() }
        } else {
            content()
        }
    }
}
