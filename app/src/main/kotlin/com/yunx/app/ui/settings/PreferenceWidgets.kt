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

package com.yunx.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.preference.SliderPreference
import kotlin.math.roundToInt

/**
 * 整数档位滑块设置项。
 *
 * 关键点：拖动过程中只改本地状态，[SliderPreference.onValueChangeFinished]（松手）才回调 [onValueChange]。
 * 若每帧都回调，调用方会把值写进 SharedPreferences，一次拖动就能触发几十次磁盘写入与全局重组。
 *
 * 本地状态用传入的 value 做 key：外部值变化（如恢复默认、从备份导入）时重新对齐滑块。
 */
@Composable
fun IntSliderPreference(
    title: String,
    summary: String? = null,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Int) -> Unit,
    valueText: (Int) -> String = { it.toString() },
    enabled: Boolean = true
) {
    // 拖动中的瞬时值；松手前不向上游暴露
    var localValue by remember(value, valueRange) { mutableFloatStateOf(value.toFloat()) }

    SliderPreference(
        value = localValue,
        onValueChange = { localValue = it },
        onValueChangeFinished = {
            // 四舍五入到整数档位，并钳制回合法区间，避免浮点误差产生越界值
            val rounded = localValue.roundToInt()
                .coerceIn(valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt())
            localValue = rounded.toFloat()
            onValueChange(rounded)
        },
        valueRange = valueRange,
        steps = steps,
        title = title,
        summary = summary,
        valueText = valueText(localValue.roundToInt()),
        enabled = enabled
    )
}
