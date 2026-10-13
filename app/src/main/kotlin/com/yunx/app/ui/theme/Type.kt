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

package com.yunx.app.ui.theme

import androidx.compose.material3.Typography

/**
 * Material 3 排版兜底：
 * 全项目排版已整体迁移至 Miuix 体系（统一读取 [top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles]），
 * 此处仅作为 [ComposeEmptyActivityTheme] 内部嵌套 [androidx.compose.material3.MaterialTheme] 兼容层的兜底占位。
 */
val Typography = Typography()