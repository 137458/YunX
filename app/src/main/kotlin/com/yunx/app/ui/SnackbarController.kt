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

package com.yunx.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/** 顺序保存提示；仅最上层仍在组合中的宿主有权显示和消费。 */
internal class SnackbarEventQueue {
    internal data class Event(
        val seq: Long,
        val message: String,
        /** 可选动作按钮文案（如「撤销」）；给出后提示条停留时间自动延长 */
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    )
    internal data class Snapshot(val events: List<Event> = emptyList(), val hosts: List<Any> = emptyList())
    private val lock = Any()
    private var sequence = 0L
    internal val state = MutableStateFlow(Snapshot())

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) = synchronized(lock) {
        state.value = state.value.copy(
            events = state.value.events + Event(++sequence, message, actionLabel.takeIf { onAction != null }, onAction)
        )
    }

    fun register(host: Any) = synchronized(lock) {
        state.value = state.value.copy(hosts = state.value.hosts.filterNot { it === host } + host)
    }

    fun unregister(host: Any) = synchronized(lock) {
        state.value = state.value.copy(hosts = state.value.hosts.filterNot { it === host })
    }

    fun consume(host: Any, sequence: Long) = synchronized(lock) {
        val current = state.value
        if (current.hosts.lastOrNull() === host && current.events.firstOrNull()?.seq == sequence) {
            state.value = current.copy(events = current.events.drop(1))
        }
    }

    fun eventsFor(host: Any) = state.map { snapshot ->
        snapshot.events.firstOrNull().takeIf { snapshot.hosts.lastOrNull() === host }
    }.distinctUntilChanged()
}

/** 全局入口保持不变；连续调用按顺序排队，弹窗和覆盖页接管提示宿主。 */
object SnackbarController {
    internal val queue = SnackbarEventQueue()

    /**
     * 显示一条提示（排队显示，不会覆盖之前未展示完的消息）。
     *
     * @param actionLabel 动作按钮文案；与 [onAction] 成对给出才会渲染按钮
     * @param onAction 用户点动作按钮后的回调（在宿主协程上下文执行）
     */
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        queue.show(message, actionLabel, onAction)
    }
}

/**
 * 提示条宿主：直接使用 miuix-ui 原生 SnackbarHost + Snackbar。
 *
 * 不自绘材质：圆角、配色、进入/退出动画、滑动消失与无障碍播报都由组件库统一维护，
 * 自绘版本在浅色模式下与系统 HyperOS 提示条不一致，且无法滑动关闭。
 *
 * @param hostState 由 rememberGlobalSnackbarHostState() 或页面自建，均可持续监听全局事件
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        state = hostState,
        modifier = modifier,
        // 允许上滑关闭：长文案不必等到超时才消失
        canSwipeToDismiss = true
    ) { data ->
        Snackbar(data = data)
    }
}

/** 渲染 SnackbarHost 并监听全局事件（放在页面最外层 Box 内即可，位于内容之上、不拦截点击） */
@Composable
fun GlobalSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = rememberGlobalSnackbarHostState()
    Box(modifier = modifier.fillMaxSize()) {
        AppSnackbarHost(
            hostState = hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/** 宿主切换取消旧显示，但保留队首消息，避免遮挡、重复显示和丢失。 */
@Composable
fun rememberGlobalSnackbarHostState(): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    val host = remember { Any() }
    DisposableEffect(host) {
        SnackbarController.queue.register(host)
        onDispose { SnackbarController.queue.unregister(host) }
    }
    LaunchedEffect(hostState, host) {
        SnackbarController.queue.eventsFor(host).collectLatest { event ->
            if (event != null) {
                val result = hostState.showSnackbar(
                    message = event.message,
                    actionLabel = event.actionLabel,
                    // 带动作按钮时给足停留时间，否则用户还没看清就消失了
                    duration = if (event.actionLabel != null) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    }
                )
                if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
                SnackbarController.queue.consume(host, event.seq)
            }
        }
    }
    return hostState
}
