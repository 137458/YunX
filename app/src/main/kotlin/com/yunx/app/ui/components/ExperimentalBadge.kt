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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 功能标识小徽标（默认「实验性」）：主色胶囊，放在功能入口标题行的右端（右上角）。
 *
 * Miuix 规范重构：Surface, Text, MiuixTheme, SquircleShape。
 */
@Composable
internal fun ExperimentalBadge(
    text: String = AppStrings.get(R.string.dl_badge_experimental),
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MiuixTheme.colorScheme.primaryContainer,
        shape = SquircleShape(6.dp)
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onPrimaryContainer,
            style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
