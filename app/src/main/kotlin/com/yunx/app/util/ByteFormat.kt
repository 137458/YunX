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

package com.yunx.app.util

import java.util.Locale

/**
 * 字节数与速率的统一格式化。
 *
 * 同一种换算原本在 5 处各写一遍（下载页、网盘页、解析详情页、下载引擎、更新弹窗），
 * 口径漂移后会出现「同一个文件在两个页面显示不同大小」。这里收拢成唯一实现，
 * 新增展示点一律复用，不要再私藏一份。
 */
object ByteFormat {

    /**
     * 字节数 → 可读字符串（B/KB/MB/GB/TB，保留一位小数）。
     * 非正值统一显示 0 B：调用方拿到的 0 既可能是「未知」也可能是真空文件，
     * 两种情况给用户的观感一致，不必区分。
     */
    fun size(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return scale(bytes)
    }

    /** 速率 → 可读字符串（在 [size] 基础上加 /s 后缀） */
    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0L) "0 B/s" else scale(bytesPerSecond) + "/s"

    private fun scale(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}
