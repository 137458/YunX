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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 原始种子色不是主题容器色，图标前景需按实际亮度选择。 */
internal fun readableSeedForeground(background: Color): Color {
    // 亮度只算一次：luminance() 内部要逐通道做线性化，重复调用纯属浪费
    val luminance = background.luminance()
    val contrastOnBlack = (luminance + 0.05f) / 0.05f
    val contrastOnWhite = 1.05f / (luminance + 0.05f)
    return if (contrastOnBlack >= contrastOnWhite) Color.Black else Color.White
}
