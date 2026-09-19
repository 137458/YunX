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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.ui.theme.rememberSquircleShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局 Snackbar 通道：任何位置（Composable / 工具函数）调用 show() 即可显示。
 * 页面层使用 GlobalSnackbarHost() / rememberGlobalSnackbarHostState() 渲染监听。
 *
 * 修复要点：多条消息并发时不会互相覆盖——每条 show() 都生成自增序号的独立事件，
 * consume(seq) 只清空"刚显示的那条"，期间新来的消息保留排队，前一条消失后继续显示。
 * 多个宿主（MainScreen / 各 Sheet / 各 LoginScreen）同时活跃时共享广播，各自渲染。
 */
object SnackbarController {
    internal data class Event(val seq: Long, val message: String)

    private val _events = MutableStateFlow<Event?>(null)
    private var seq = 0L

    internal val events: StateFlow<Event?> = _events

    fun show(message: String) {
        // 每次 show 都是新 Event（seq 递增），StateFlow 值必然变化 → 所有收集者都会收到
        _events.value = Event(++seq, message)
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
 * 现代 Miuix / HyperOS 风格悬浮胶囊 Toast 卡片。
 *
 * 抛弃 Material 3 生硬全宽拉伸的暗黑方块，采用超椭圆圆角（Squircle）、
 * 高级半透明材质、柔和深邃阴影与微光描边，尺寸自适应内容且水平居中。
 */
@Composable
fun AppToast(
    data: SnackbarData,
    modifier: Modifier = Modifier
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val shape = rememberSquircleShape(corner = 20.dp)

    val containerColor = if (isDark) {
        Color(0xEE2A2A2D)
    } else {
        Color(0xF6FFFFFF)
    }
    val contentColor = MiuixTheme.colorScheme.onSurface
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 340.dp)
                .dropShadow(
                    shape = shape,
                    shadow = Shadow(
                        radius = 16.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.35f else 0.12f
                    )
                )
                .border(
                    width = 0.75.dp,
                    color = borderColor,
                    shape = shape
                )
                .background(
                    color = containerColor,
                    shape = shape
                )
                .clip(shape)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = data.visuals.message,
                    color = contentColor,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                data.visuals.actionLabel?.let { actionLabel ->
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = actionLabel,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { data.performAction() }
                    )
                }
            }
        }
    }
}

/**
 * 现代 Miuix 风格 SnackbarHost，承载 [AppToast] 动画与展示。
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            AppToast(data = data)
        }
    )
}

/** 渲染 SnackbarHost 并监听全局事件（放在页面最外层 Box 内即可，位于内容之上、不拦截点击） */
@Composable
fun GlobalSnackbarHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(hostState) {
        SnackbarController.events.collect { event ->
            if (event != null) {
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
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
                hostState.showSnackbar(event.message)
                SnackbarController.consume(event.seq)
            }
        }
    }
    return hostState
}