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

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 触摸目标最小尺寸约束。
 *
 * 视觉上可以画得更小（如图标按钮、列表尾部的小箭头），但可点击区域必须达到 48dp，
 * 否则单手操作时命中率显著下降。放在链条最前面，后续 padding/size 只影响视觉，不缩小命中区。
 */
fun Modifier.accessibleTouchTarget(minSize: Dp = 48.dp): Modifier =
    this.defaultMinSize(minWidth = minSize, minHeight = minSize)

/**
 * 让一个列表项被读屏当作整体朗读。
 *
 * 列表项内部通常由图标 + 标题 + 副标题 + 右侧箭头组成，逐个节点朗读会碎片化。
 * 这里用 mergeDescendants = true 把子树合并成一个语义节点，再用 description 覆盖朗读文本。
 *
 * @param role 语义角色，默认按钮（可点击项）
 * @param description 合并后朗读的内容描述
 * @param onClickLabel 点击动作的朗读标签，如「打开」
 * @param longClickLabel 长按动作的朗读标签；给出它就必须同时给出 onLongClick
 * @param onLongClick 长按回调；同时作为无障碍自定义动作暴露，供读屏用户触发
 */
fun Modifier.mergeItemSemantics(
    role: Role = Role.Button,
    description: String,
    onClickLabel: String? = null,
    longClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null
): Modifier = this.semantics(mergeDescendants = true) {
    this.role = role
    contentDescription = description
    if (onClickLabel != null) {
        onClick(label = onClickLabel) { false }
    }
    if (onLongClick != null) {
        // 返回 true：表示该语义节点已消费长按，避免无障碍服务再回退到默认手势
        onLongClick(label = longClickLabel) {
            onLongClick()
            true
        }
        // 自定义动作给读屏用户一个不依赖手势的等价入口
        customActions = listOf(
            CustomAccessibilityAction(label = longClickLabel ?: "长按") {
                onLongClick()
                true
            }
        )
    }
}
