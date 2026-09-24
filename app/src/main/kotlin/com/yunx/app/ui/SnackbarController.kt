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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Snackbar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

/**
 * 全局 Snackbar 通道：任何位置（Composable / 工具函数）调用 show() 即可显示。
 * 页面层使用 GlobalSnackbarHost() / rememberGlobalSnackbarHostState() 渲染监听。
 *
 * 修复要点：多条消息并发时不会互相覆盖——每条 show() 都生成自增序号的独立事件，
 * consume(seq) 只清空"刚显示的那条"，期间新来的消息保留排队，前一条消失后继续显示。
 * 多个宿主（MainScreen / 各 Sheet / 各 LoginScreen）同时活跃时共享广播，各自渲染。
 */
object SnackbarController {
    internal data class Event(
        val seq: Long,
        val message: String,
        /** 可选动作按钮文案（如「撤销」）；给出后提示条停留时间自动延长 */
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    )

    private val _events = MutableStateFlow<Event?>(null)
    private var seq = 0L

    internal val events: StateFlow<Event?> = _events

    /**
     * 显示一条提示。
     *
     * @param actionLabel 动作按钮文案；与 [onAction] 成对给出才会渲染按钮
     * @param onAction 用户点动作按钮后的回调（在宿主协程上下文执行）
     */
    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        // 每次 show 都是新 Event（seq 递增），StateFlow 值必然变化 → 所有收集者都会收到
        _events.value = Event(++seq, message, actionLabel.takeIf { onAction != null }, onAction)
    }

    /**
     * 消费（清空）事件：仅当当前事件就是刚刚显示的那条时置空，
     * 避免清空动作覆盖期间新 show 进来的消息（这是旧实现丢失第二条的根因）。
     */
    fun consume(shownSeq: Long) {
        val cur = _events.value
        if (cur != null && cur.seq == shownSeq) _events.value = null
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

/** 供 Scaffold(snackbarHost = { AppSnackbarHost(state) }) 或页面 Box 使用的宿主状态（自动监听全局事件） */
@Composable
fun rememberGlobalSnackbarHostState(): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
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
                SnackbarController.consume(event.seq)
            }
        }
    }
    return hostState
}