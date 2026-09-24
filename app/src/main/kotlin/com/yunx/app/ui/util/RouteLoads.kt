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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 按键去重 + 有界 LRU 的异步结果缓存（纯 Kotlin，不依赖 Compose / Android）。
 *
 * 适用场景：路由级数据加载——同一 key 的并发请求合并成一次，加载完成后结果驻留，
 * 超出容量时淘汰最久未访问的条目，避免路由来回切换时无限增长。
 *
 * @param scope 承载加载任务的协程作用域，通常是 ViewModel 的 viewModelScope
 * @param capacity 最大驻留条目数；必须为正数
 */
class RouteLoads<K : Any, T>(
    private val scope: CoroutineScope,
    private val capacity: Int
) {
    init {
        require(capacity > 0) { "capacity 必须为正数，实际为 $capacity" }
    }

    private class Entry<T> {
        var value: T? = null
        var job: Job? = null

        val isCompleted: Boolean
            get() = job == null
    }

    // accessOrder = true：每次读取都会把条目移到链尾，链头即最久未使用
    private val entries = LinkedHashMap<K, Entry<T>>(capacity.coerceAtLeast(1), 0.75f, true)

    /** 已完成的缓存值；未加载或加载中返回 null。会刷新该 key 的 LRU 位置。 */
    @Synchronized
    fun peek(key: K): T? = entries[key]?.takeIf { it.isCompleted }?.value

    /** 已驻留的 key 集合（含加载中的），便于测试与调试。 */
    @Synchronized
    fun keys(): Set<K> = entries.keys.toSet()

    /**
     * 加载或复用 [key] 对应的数据。
     *
     * - 已有完成结果且 refresh=false：直接返回一个已完成的 Job，不重新执行 block；
     * - 已有进行中任务：复用同一个 Job，并发调用不会重复请求；
     * - 其余情况新建任务，并在写入结果前做身份校验。
     *
     * @param refresh 为真时丢弃已有完成结果强制重新加载（进行中的任务仍然复用）
     */
    @Synchronized
    fun load(key: K, refresh: Boolean = false, block: suspend () -> T): Job {
        val existing = entries[key]
        if (existing != null) {
            if (existing.job != null) return existing.job!!
            if (!refresh) return scope.launch { /* 命中缓存：空任务，语义上"结果已就绪" */ }
        }

        // 重新加载时先清掉旧值：即使新任务失败，也不会让调用方读到过期结果
        existing?.value = null

        val entry = existing ?: Entry<T>().also { entries[key] = it }
        entry.job = scope.launch {
            val result = block()
            // 身份校验：条目可能已被 invalidate/remove/容量驱逐替换，
            // 晚到的结果必须丢弃，否则会写回一个已失效的缓存槽
            synchronized(this@RouteLoads) {
                if (entries[key] === entry) {
                    entry.value = result
                    entry.job = null
                    trimToCapacity()
                }
            }
        }
        trimToCapacity()
        return entry.job!!
    }

    /**
     * 清空全部缓存并取消所有进行中任务。
     *
     * 顺序必须是先 clear 再 cancel：clear 先断掉 entries[key] === entry 的身份校验，
     * 于是那些不响应取消（例如阻塞在不可中断 IO）的协程即使跑完，结果也会被丢弃；
     * 若先 cancel 再 clear，晚到的结果仍能通过身份校验写回。
     */
    @Synchronized
    fun invalidate() {
        val jobs = entries.values.mapNotNull { it.job }
        entries.clear()
        jobs.forEach { it.cancel() }
    }

    /** 移除单个 key（进行中任务一并取消）。 */
    @Synchronized
    fun remove(key: K) {
        val entry = entries.remove(key) ?: return
        entry.job?.cancel()
    }

    /** 超出容量时从最久未使用的一端驱逐，并取消其进行中的任务。调用方需持有锁。 */
    private fun trimToCapacity() {
        while (entries.size > capacity) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            val removed = entries.remove(oldestKey)
            removed?.job?.cancel()
        }
    }
}
