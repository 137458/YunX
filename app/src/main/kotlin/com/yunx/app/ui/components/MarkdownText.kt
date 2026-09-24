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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Markdown 富文本渲染器（标题 / 列表 / 引用分隔线 + 行内加粗与代码）。
 *
 * 用途：GitHub Release 的更新说明本身是 Markdown，直接当纯文本渲染会把
 * `##`、`- `、`**` 这些标记原样显示出来，用户看到的是「带符号的乱码」。
 * 这里只实现更新说明实际用到的子集，不引入任何 Markdown 依赖。
 *
 * @param baseFontSize 正文字号（sp），标题在此基础上按层级放大
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSize: Int = 13,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedContent(block.text),
                        style = when (block.level) {
                            1 -> MiuixTheme.textStyles.title3.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (baseFontSize + 4).sp,
                            )
                            2 -> MiuixTheme.textStyles.title4.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (baseFontSize + 2).sp,
                            )
                            else -> MiuixTheme.textStyles.body1.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (baseFontSize + 1).sp,
                            )
                        },
                        color = MiuixTheme.colorScheme.primary,
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp, end = 8.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary),
                        )
                        Text(
                            text = buildAnnotatedContent(block.text),
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = baseFontSize.sp,
                                lineHeight = (baseFontSize + 6).sp,
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${block.number}. ",
                            style = MiuixTheme.textStyles.body2.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = baseFontSize.sp,
                                lineHeight = (baseFontSize + 6).sp,
                            ),
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Text(
                            text = buildAnnotatedContent(block.text),
                            style = MiuixTheme.textStyles.body2.copy(
                                fontSize = baseFontSize.sp,
                                lineHeight = (baseFontSize + 6).sp,
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildAnnotatedContent(block.text),
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = baseFontSize.sp,
                            lineHeight = (baseFontSize + 6).sp,
                        ),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.Divider -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.2f)),
                    )
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    object Divider : MarkdownBlock
}

/** 按行切块：标题（# 到 ####）、无序列表（- 、*、+）、有序列表、分隔线、普通段落 */
private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isEmpty()) continue

        when {
            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                blocks.add(MarkdownBlock.Divider)
            }
            line.startsWith("#### ") -> {
                blocks.add(MarkdownBlock.Heading(level = 4, text = line.removePrefix("#### ").trim()))
            }
            line.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(level = 3, text = line.removePrefix("### ").trim()))
            }
            line.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(level = 2, text = line.removePrefix("## ").trim()))
            }
            line.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(level = 1, text = line.removePrefix("# ").trim()))
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                blocks.add(MarkdownBlock.BulletItem(text = line.substring(2).trim()))
            }
            line.matches(Regex("""^\d+\.\s+.*""")) -> {
                val num = line.substringBefore('.')
                val content = line.substringAfter('.').trim()
                blocks.add(MarkdownBlock.NumberedItem(number = num, text = content))
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(text = line))
            }
        }
    }

    return blocks
}

/** 行内样式：**加粗** 与 `行内代码`，其余按普通文本追加 */
private fun buildAnnotatedContent(rawText: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = rawText.length

        while (i < len) {
            // **加粗**
            if (rawText.startsWith("**", i)) {
                val end = rawText.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(rawText.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // `代码`
            if (rawText.startsWith("`", i)) {
                val end = rawText.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(rawText.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            append(rawText[i])
            i++
        }
    }
}