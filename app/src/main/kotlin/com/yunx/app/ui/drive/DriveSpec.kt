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

package com.yunx.app.ui.drive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.ui.graphics.vector.ImageVector
import com.yunx.app.data.network.model.ShareFile

/**
 * 网盘平台差异的数据载体。
 *
 * 各平台只有文案、图标、有效期编码与提取码策略不同，界面结构完全一致；
 * 把这些差异集中到本类，通用实现（CloudDriveScreen / ShareFormSheet / CloudPickerSheet）
 * 就不再需要任何平台分支。
 */
data class DriveSpec(
    /** 云盘浏览页标题，同时作为面包屑根节点文案 */
    val title: String,
    /** 操作抽屉「分享」项的副标题：各平台提取码策略不同，文案随之不同 */
    val shareDesc: String,
    /** 分享表单顶部的策略说明；null 表示不显示说明行 */
    val shareHint: String?,
    /** 有效期选项：名称 + 平台原始编码值（各平台编码不同，必须逐条保留） */
    val expiryOptions: List<DriveExpiryOption<Any?>>,
    /** 提取码策略 */
    val passcode: PasscodePolicy,
    /** 文件夹图标（操作抽屉头部的文件夹图标；移动按钮统一使用 [DriveIcons.move]） */
    val folderIcon: ImageVector = Icons.Outlined.DriveFileMove,
    /** 浏览页空目录提示 */
    val emptyText: String = "此目录为空"
)

/** 有效期选项：名称 + 平台原始编码值（值为 Any? 以同时容纳 Int 与 Int? 两套编码） */
data class DriveExpiryOption<T>(val label: String, val value: T)

/** 各平台共用图标：与 CloudDriveScaffold / 文件操作抽屉保持一致 */
object DriveIcons {
    val move: ImageVector = Icons.Outlined.DriveFileMove
}

/**
 * 提取码策略：各平台对提取码的强制程度不同，无法统一成单一开关。
 */
sealed interface PasscodePolicy {
    /** 是否显示提取码输入框 */
    val showsField: Boolean

    /** 百度/迅雷：分享必须带 4 位提取码，输入框始终显示 */
    data class Fixed(val label: String, val length: Int = 4, val filter: Boolean = true) : PasscodePolicy {
        override val showsField: Boolean get() = true
    }

    /** UC/123：可选择「无提取码」或「设置提取码」，设置时自动填充随机码 */
    data class Optional(val label: String, val length: Int = 4, val autoGenerate: Boolean = true) : PasscodePolicy {
        override val showsField: Boolean get() = true
    }

    /** C139：提取码由服务端自动生成，界面不提供输入 */
    data object Auto : PasscodePolicy {
        override val showsField: Boolean get() = false
    }
}

/**
 * 生成 4 位提取码。
 * 去掉了 0/O/1/l/I 等易混淆字符，与各平台原有实现保持同一字符集。
 */
internal fun randomPasscode(length: Int = 4): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..length).map { chars.random() }.joinToString("")
}

/**
 * 云盘浏览页需要触达的 ViewModel 能力。
 *
 * ViewModel 位于 ui/viewmodel/ 且六个平台的状态类型互不相同，无法抽出公共接口，
 * 因此这里只声明「能力」并以 lambda 传入，由各平台薄包装负责绑定到自己的 ViewModel。
 * 需要视图状态（selected / 目录名等）的场景由包装层直接读取 ViewModel 后传入。
 */
class DriveActions(
    /** 下拉刷新当前目录 */
    val refresh: () -> Unit,
    /** 返回上一级目录 */
    val back: () -> Unit,
    /** 面包屑跳转到指定层级 */
    val navigateToLevel: (Int) -> Unit,
    /** 全选/取消全选 */
    val toggleSelectAll: (List<ShareFile>) -> Unit,
    /** 退出多选 */
    val exitMultiSelect: () -> Unit,
    /** 打开文件夹 */
    val openFolder: (ShareFile) -> Unit,
    /** 多选模式下切换单个文件选中态 */
    val toggleSelect: (ShareFile) -> Unit,
    /** 长按进入多选 */
    val enterMultiSelect: (ShareFile) -> Unit,
    /** 打开文件操作菜单（写入 actionFile） */
    val openActions: (ShareFile) -> Unit,
    /** 关闭文件操作菜单 */
    val dismissActions: () -> Unit,
    /** 开始浏览移动目标目录（回到根目录） */
    val openMoveRoot: () -> Unit,
    /** 移动目标目录：进入子目录 */
    val openMoveFolder: (ShareFile) -> Unit,
    /** 移动目标目录：返回上一级 */
    val moveBack: () -> Unit,
    /** 移动目标目录：面包屑跳转 */
    val moveNavigateToLevel: (Int) -> Unit,
    /** 移动目标目录加载失败后重试 */
    val retryMove: () -> Unit,
    /** 确认移动；[multiSelect] 为 true 时移动全部选中项 */
    val move: (to: String, multiSelect: Boolean) -> Unit,
    /** 重命名当前操作文件 */
    val rename: (String) -> Unit,
    /** 删除当前操作文件 */
    val deleteFile: () -> Unit,
    /** 删除全部选中项 */
    val deleteSelected: () -> Unit,
    /** 分享当前操作文件；[expiry] 为平台原始有效期编码，[passcode] 为提取码（可为空） */
    val shareFile: (expiry: Any?, passcode: String) -> Unit,
    /** 批量分享选中项 */
    val shareSelected: (expiry: Any?, passcode: String) -> Unit,
    /** 下载当前操作文件 */
    val downloadFile: () -> Unit,
    /** 下载当前操作文件夹 */
    val downloadFolder: () -> Unit,
    /** 批量下载选中项 */
    val downloadSelected: () -> Unit
)

/**
 * 云盘浏览页统一视图状态。
 *
 * 五个平台各自的 UiState 只是 Loaded 的目录标识字段不同（dirPath / dirFid / dirId），
 * 包装层在转换时把该字段解析成 [CloudDriveUiState.Loaded.dir]，通用实现因此无需类型判断。
 */
sealed interface CloudDriveUiState {
    data object Loading : CloudDriveUiState

    data class Loaded(
        val files: List<ShareFile>,
        val pathNames: List<String>,
        /** 当前目录标识：移动/转存确认时回传给各平台 ViewModel */
        val dir: String
    ) : CloudDriveUiState

    data class Error(val message: String) : CloudDriveUiState
}
