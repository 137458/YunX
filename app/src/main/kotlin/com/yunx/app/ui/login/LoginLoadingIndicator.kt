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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 提交中指示器：登录页里所有「保存 / 登录」等待态共用，圈固定 18dp、线宽 2dp */
private val LoginLoadingSize = 18.dp
private val LoginLoadingStroke = 2.dp
private val LoginLoadingTextGap = 8.dp

/**
 * 统一的登录 / 保存等待指示器。
 *
 * 登录页此前按页面各自写 20dp / 18dp / 16dp 的圈，顶栏位置还额外带左右 12dp 内边距，
 * 同一件事三种尺寸。这里固定圈径与线宽；[text] 非空时在圈后补一段说明（主登录按钮传
 * 「登录中…」），顶栏动作位与纯图标按钮不传文字 —— 是否带文字只由这个参数决定。
 */
@Composable
fun LoginLoadingIndicator(
    modifier: Modifier = Modifier,
    text: String? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(LoginLoadingSize),
            strokeWidth = LoginLoadingStroke
        )
        if (text != null) {
            Spacer(modifier = Modifier.width(LoginLoadingTextGap))
            Text(text = text, style = MiuixTheme.textStyles.button)
        }
    }
}
