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

package com.yunx.app.data.download

import android.media.MediaFormat
import android.os.Build

/**
 * `MediaFormat#getInteger(key, fallback)` 自 API 29 才提供，而本应用 minSdk 24。
 * 低版本直接调用会在 24–28 的设备上抛 NoSuchMethodError；改用单参 `getInteger(key)`
 * 时缺键又会抛 IllegalStateException（`KEY_ROTATION`、`KEY_MAX_INPUT_SIZE` 都属可选键）。
 */
object MediaFormatCompat {
    fun getIntegerOr(
        format: MediaFormat,
        key: String,
        fallback: Int,
    ): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.getInteger(key, fallback)
        } else {
            @Suppress("DEPRECATION")
            runCatching { format.getInteger(key) }.getOrDefault(fallback)
        }
}
