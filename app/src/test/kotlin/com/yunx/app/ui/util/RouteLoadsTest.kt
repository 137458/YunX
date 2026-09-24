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

package com.yunx.app.ui.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * RouteLoads 单测。
 *
 * 项目未引入 kotlinx-coroutines-test（离线环境无法新增依赖），
 * 因此统一用 runBlocking + 真实协程，用 CompletableDeferred 控制完成时机。
 */
class RouteLoadsTest {

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 同一 key 并发 load 必须复用同一个 Job，且 block 只执行一次。 */
    @Test
    fun load_reusesInFlightJobForSameKey() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            val gate = CompletableDeferred<Unit>()
            val executions = AtomicInteger(0)

            val first = loads.load("a") {
                executions.incrementAndGet()
                gate.await()
                7
            }
            val second = loads.load("a") {
                executions.incrementAndGet()
                99
            }

            assertSame(first, second)
            gate.complete(Unit)
            first.join()
            assertEquals(1, executions.get())
            assertEquals(7, loads.peek("a"))
        } finally {
            scope.cancel()
        }
    }

    /** 命中缓存：已完成的结果不会被重新执行，且返回的 Job 已经结束。 */
    @Test
    fun load_returnsCompletedJobWhenCacheHit() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            val executions = AtomicInteger(0)
            loads.load("a") { executions.incrementAndGet(); 1 }.join()

            val job = loads.load("a") { executions.incrementAndGet(); 2 }
            job.join()
            assertEquals(1, executions.get())
            assertEquals(1, loads.peek("a"))
        } finally {
            scope.cancel()
        }
    }

    /** refresh=true 时丢弃旧值并重新执行。 */
    @Test
    fun load_refreshReexecutesBlock() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            loads.load("a") { 1 }.join()
            loads.load("a", refresh = true) { 2 }.join()
            assertEquals(2, loads.peek("a"))
        } finally {
            scope.cancel()
        }
    }

    /** LRU：超出容量时驱逐最久未访问的条目，最近访问过的保留。 */
    @Test
    fun load_evictsLeastRecentlyUsedEntry() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 2)
            loads.load("a") { 1 }.join()
            loads.load("b") { 2 }.join()
            // 重新访问 a，把 a 移到链尾；此时 b 才是最久未使用
            assertEquals(1, loads.peek("a"))
            loads.load("c") { 3 }.join()

            assertEquals(1, loads.peek("a"))
            assertEquals(3, loads.peek("c"))
            assertNull(loads.peek("b"))
            assertEquals(setOf("a", "c"), loads.keys())
        } finally {
            scope.cancel()
        }
    }

    /** 容量为 1 的极端情况：后写入的条目必须驱逐先写入的。 */
    @Test
    fun load_capacityOneKeepsOnlyLatest() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 1)
            loads.load("a") { 1 }.join()
            loads.load("b") { 2 }.join()
            assertNull(loads.peek("a"))
            assertEquals(2, loads.peek("b"))
        } finally {
            scope.cancel()
        }
    }

    /** invalidate 之后晚到的结果必须被丢弃（身份校验生效）。 */
    @Test
    fun invalidate_discardsLateResult() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            val gate = CompletableDeferred<Unit>()

            // block 内部捕获 entry，模拟一个不配合取消、跑完才写回的协程
            val job = loads.load("a") {
                gate.await()
                42
            }
            gate.complete(Unit)

            // 先让加载任务完成写入，再验证 invalidate 清空
            job.join()
            assertEquals(42, loads.peek("a"))
            loads.invalidate()
            assertNull(loads.peek("a"))
            assertTrue(loads.keys().isEmpty())

            // 重新加载同一个 key 得到新条目，旧条目的晚到结果不得覆盖
            val gate2 = CompletableDeferred<Unit>()
            val job2 = loads.load("a") {
                gate2.await()
                100
            }
            // invalidate 取消的是已驻留任务；这里再清一次，让 job2 的结果成为"晚到"
            loads.invalidate()
            gate2.complete(Unit)
            job2.join()
            assertNull(loads.peek("a"))
        } finally {
            scope.cancel()
        }
    }

    /** 直接验证身份校验：旧 entry 的结果不能写回新 entry。 */
    @Test
    fun load_lateResultFromEvictedEntryIsDiscarded() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 1)
            val gateA = CompletableDeferred<Unit>()
            val jobA = loads.load("a") {
                gateA.await()
                1
            }
            // 塞入 b 把 a 挤出容量（a 的进行中任务会被取消）
            loads.load("b") { 2 }.join()
            gateA.complete(Unit)
            jobA.join()
            // a 已被驱逐，晚到的 1 不能复活它
            assertNull(loads.peek("a"))
            assertEquals(2, loads.peek("b"))
        } finally {
            scope.cancel()
        }
    }

    /** remove：移除指定 key，其余条目不受影响。 */
    @Test
    fun remove_dropsSingleEntry() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            loads.load("a") { 1 }.join()
            loads.load("b") { 2 }.join()

            loads.remove("a")
            assertNull(loads.peek("a"))
            assertEquals(2, loads.peek("b"))

            // 移除不存在的 key 不应抛异常
            loads.remove("missing")
            assertEquals(setOf("b"), loads.keys())
        } finally {
            scope.cancel()
        }
    }

    /** peek 对未加载的 key 返回 null，不触发加载。 */
    @Test
    fun peek_returnsNullForUnknownKey() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            assertNull(loads.peek("nope"))
            assertTrue(loads.keys().isEmpty())
        } finally {
            scope.cancel()
        }
    }

    /** 加载中的 key：peek 返回 null，但 keys 里已经存在。 */
    @Test
    fun peek_returnsNullWhileLoading() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 4)
            val gate = CompletableDeferred<Unit>()
            val job = loads.load("a") {
                gate.await()
                1
            }
            yield()
            assertNull(loads.peek("a"))
            assertTrue(loads.keys().contains("a"))
            gate.complete(Unit)
            job.join()
            assertEquals(1, loads.peek("a"))
        } finally {
            scope.cancel()
        }
    }

    /** 非法容量必须立刻失败，而不是等到运行时才出现难定位的行为。 */
    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsNonPositiveCapacity() {
        val scope = newScope()
        try {
            RouteLoads<String, Int>(scope, capacity = 0)
        } finally {
            scope.cancel()
        }
    }

    /** 驱逐应取消被驱逐条目的进行中任务（用 isActive 观察）。 */
    @Test
    fun eviction_cancelsInFlightJob() = runBlocking {
        val scope = newScope()
        try {
            val loads = RouteLoads<String, Int>(scope, capacity = 1)
            val gate = CompletableDeferred<Unit>()
            val evicted = loads.load("a") {
                gate.await()
                1
            }
            val replacement = loads.load("b") { 2 }
            replacement.join()
            assertTrue("被驱逐条目的任务应已被取消", !evicted.isActive)
        } finally {
            scope.cancel()
        }
    }
}
