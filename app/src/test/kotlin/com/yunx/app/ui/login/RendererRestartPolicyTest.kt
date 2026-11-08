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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 渲染进程崩溃后的重建预算测试：
 * 四张直连 WebView 的登录页共用同一套计数与文案规则，页面本身无法在 JVM 上组合，
 * 因此把「还能不能重试 / 还剩几次 / 该说什么」压在纯函数里用单测钉住。
 */
class RendererRestartPolicyTest {

    @Test
    fun freshBudgetAllowsFullNumberOfRestarts() {
        val budget = RendererRestartBudget()
        assertTrue(budget.canRetry)
        assertEquals(RENDERER_RESTART_LIMIT, budget.remaining)
        assertEquals(0, budget.restarts)
    }

    @Test
    fun consumingRestartsUntilLimitThenRefusesFurtherRebuilds() {
        var budget = RendererRestartBudget()
        repeat(RENDERER_RESTART_LIMIT) { budget = budget.consume() }

        assertEquals(RENDERER_RESTART_LIMIT, budget.restarts)
        assertEquals(0, budget.remaining)
        assertFalse(budget.canRetry)

        // 到上限后再点也不会继续重建：状态保持不变
        assertEquals(budget, budget.consume())
    }

    @Test
    fun eachConsumeCountsExactlyOneRestart() {
        val first = RendererRestartBudget().consume()
        assertEquals(1, first.restarts)
        assertEquals(RENDERER_RESTART_LIMIT - 1, first.remaining)
        assertTrue(first.canRetry)

        val second = first.consume()
        assertEquals(2, second.restarts)
        assertEquals(RENDERER_RESTART_LIMIT - 2, second.remaining)
    }

    @Test
    fun messageBeforeLimitStatesRemainingRestarts() {
        assertEquals(
            "页面渲染进程已崩溃，请点「重试」重新加载（还可重试 3 次）",
            rendererCrashMessage(RendererRestartBudget())
        )
        assertEquals(
            "页面渲染进程已崩溃，请点「重试」重新加载（还可重试 1 次）",
            rendererCrashMessage(RendererRestartBudget().consume().consume())
        )
    }

    @Test
    fun messageAtLimitTellsUserRetryIsPointless() {
        var budget = RendererRestartBudget()
        repeat(RENDERER_RESTART_LIMIT) { budget = budget.consume() }

        assertEquals(
            // 首崩 + 3 次重建后再崩 = 4 次，文案不能再按「重建次数」算
            "页面渲染进程已连续崩溃 4 次，重试无法恢复。请返回后重新进入此页面，或改用其他登录方式。",
            rendererCrashMessage(budget)
        )
    }
}
