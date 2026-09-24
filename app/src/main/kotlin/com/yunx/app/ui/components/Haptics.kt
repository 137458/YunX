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

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** 触感语义：调用方只表达"发生了什么"，具体震动强度由这里统一映射。 */
enum class HapticType {
    /** 轻微刻度：选择切换、滑过档位。 */
    Tick,

    /** 确认：提交成功、操作生效。 */
    Confirm,

    /** 拒绝：校验失败、操作被阻止。 */
    Reject,

    /** 手势开始：拖动/长按进入手势态。 */
    GestureStart
}

/**
 * 触感反馈入口。
 *
 * 内部做 20ms 节流：滚动或快速连点时事件会成簇到达，
 * 逐个下发震动会让马达持续响应并明显拖慢 UI 线程，节流后只保留首个事件。
 */
class Haptics internal constructor(
    private val feedback: HapticFeedback
) {
    private var lastPerformUptimeMs = 0L

    fun tick() = perform(HapticType.Tick)

    fun confirm() = perform(HapticType.Confirm)

    fun reject() = perform(HapticType.Reject)

    fun gestureStart() = perform(HapticType.GestureStart)

    private fun perform(type: HapticType) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPerformUptimeMs < THROTTLE_WINDOW_MS) return
        lastPerformUptimeMs = now
        feedback.performHapticFeedback(type.toPlatformType())
    }

    private companion object {
        const val THROTTLE_WINDOW_MS = 20L
    }
}

/**
 * 映射到 Compose 的 HapticFeedbackType。
 *
 * Compose 1.11 已提供 Confirm/Reject/SegmentTick/GestureThresholdActivate 这些细粒度类型，
 * 直接使用它们即可拿到平台对应的原生波形，不需要自己拼装震动 Pattern。
 */
private fun HapticType.toPlatformType(): HapticFeedbackType = when (this) {
    HapticType.Tick -> HapticFeedbackType.SegmentTick
    HapticType.Confirm -> HapticFeedbackType.Confirm
    HapticType.Reject -> HapticFeedbackType.Reject
    HapticType.GestureStart -> HapticFeedbackType.GestureThresholdActivate
}

/** 取得当前组合的触感入口；HapticFeedback 实例变化时重建，节流时间戳随之重置。 */
@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    // 以 HapticFeedback 实例身份为 key：实例变化（跨窗口/平台实现切换）时重建，
    // 节流时间戳随之重置，避免把新实例的首次触感误判为节流窗口内。
    return remember(feedback) { Haptics(feedback) }
}
