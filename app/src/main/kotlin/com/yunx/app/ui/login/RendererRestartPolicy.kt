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

package com.yunx.app.ui.login

import com.yunx.app.R
import com.yunx.app.util.AppStrings

/**
 * 渲染进程连续崩溃后允许重建 WebView 的最大次数。
 *
 * 每次崩溃重建都是一个全新的 WebView 实例（旧实例已被系统判定不可用），连点会一直累积
 * WebView / 渲染进程资源，因此必须封顶。3 次足够覆盖偶发崩溃，又不会让用户陷入无限重建。
 */
internal const val RENDERER_RESTART_LIMIT = 3

/**
 * 渲染进程崩溃后的重建预算（纯数据，不可变）。
 *
 * 页面进入时新建（`restarts = 0`），离开页面随组合一起销毁，再次进入重新计数；
 * 每次真正重建 WebView 前调用 [consume]，[canRetry] 为 false 后 [consume] 不再变化。
 */
internal data class RendererRestartBudget(val restarts: Int = 0) {
    /** 是否还允许再次重建 */
    val canRetry: Boolean get() = restarts < RENDERER_RESTART_LIMIT

    /** 剩余可重建次数 */
    val remaining: Int get() = (RENDERER_RESTART_LIMIT - restarts).coerceAtLeast(0)

    /** 扣掉一次重建机会；已达上限时原样返回，防止连点越界 */
    fun consume(): RendererRestartBudget = if (canRetry) copy(restarts = restarts + 1) else this
}

/**
 * 渲染进程崩溃覆盖层的文案：未到上限时说明还能点几次，到达上限时明确告知重试无效、
 * 需要退出重进或改用其他登录方式（否则用户只会继续点一个不会生效的按钮）。
 */
internal fun rendererCrashMessage(budget: RendererRestartBudget): String =
    if (budget.canRetry) {
        AppStrings.get(R.string.lg_crash_retry_count, budget.remaining)
    } else {
        // 崩溃次数 = 首崩 1 次 + 已重建次数；预算耗尽时正好是 LIMIT + 1
        AppStrings.get(R.string.lg_crash_exhausted, RENDERER_RESTART_LIMIT + 1)
    }
