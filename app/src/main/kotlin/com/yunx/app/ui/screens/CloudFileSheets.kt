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

package com.yunx.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.BreadcrumbBar
import com.yunx.app.ui.components.BreadcrumbItem
// CloudStepHeader / DrivePickerErrorPane 已下沉为 ui/drive 通用件（唯一实现）
import com.yunx.app.ui.drive.CloudStepHeader
import com.yunx.app.ui.drive.DrivePickerErrorPane
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.viewmodel.QuarkCloudUiState
import com.yunx.app.ui.viewmodel.QuarkCloudViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 文件操作菜单类型（FileActionSheet 内切换） */
private enum class ActionStep { MENU, MOVE, SHARE, RENAME, DELETE }

/** 有效期选项：名称 + expired_type 值 */
private val expireOptions = listOf(
    "永久有效" to 1,
    "1 天" to 2,
    "7 天" to 3,
    "30 天" to 4
)

/**
 * 夸克云盘文件操作抽屉（Miuix OverlayBottomSheet）：更多按钮 → 操作菜单（下载/分享/移动/重命名/删除），
 * 内部按步骤切换：移动选目录 / 分享设置 / 重命名输入 / 删除确认。
 */
@Composable
fun FileActionSheet(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(ActionStep.MENU) }
    val moveState by viewModel.moveUiState.collectAsState()
    val operating = viewModel.isOperating

    val closeSheet = {
        if (!operating) {
            step = ActionStep.MENU
            onDismiss()
        }
    }

    OverlayBottomSheet(
        show = true,
        onDismissRequest = closeSheet
    ) {
        when (step) {
            ActionStep.MENU -> ActionMenu(
                file = file,
                onDownload = {
                    viewModel.downloadFile()
                    closeSheet()
                },
                onDownloadFolder = {
                    viewModel.downloadFolder()
                    closeSheet()
                },
                onShare = { step = ActionStep.SHARE },
                onMove = {
                    viewModel.openMoveRoot()
                    step = ActionStep.MOVE
                },
                onRename = { step = ActionStep.RENAME },
                onDelete = { step = ActionStep.DELETE }
            )

            ActionStep.MOVE -> MoveStep(
                file = file,
                viewModel = viewModel,
                moveState = moveState,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = closeSheet
            )

            ActionStep.SHARE -> ShareStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU }
            )

            ActionStep.RENAME -> RenameStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = closeSheet
            )

            ActionStep.DELETE -> DeleteStep(
                file = file,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = ActionStep.MENU },
                onDone = closeSheet
            )
        }
    }

    // 分享创建成功：展示链接与提取码（可复制，WindowDialog 避免抽屉内 Overlay 嵌套冲突）
    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }
}

/** 操作菜单主界面 */
@Composable
private fun ActionMenu(
    file: ShareFile,
    onDownload: () -> Unit,
    onDownloadFolder: (() -> Unit)? = null,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = SquircleShape(12.dp),
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.isdir) Icons.Outlined.Folder else Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fname,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (file.isdir) "文件夹" else "文件",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!file.isdir) {
            CloudActionItem(
                icon = Icons.Outlined.Download,
                title = "下载",
                desc = "使用内置下载功能保存到本机",
                tint = MiuixTheme.colorScheme.primary,
                onClick = onDownload
            )
        } else if (onDownloadFolder != null) {
            CloudActionItem(
                icon = Icons.Outlined.Download,
                title = "下载文件夹",
                desc = "递归下载整个文件夹，保持目录结构",
                tint = MiuixTheme.colorScheme.primary,
                onClick = onDownloadFolder
            )
        }
        CloudActionItem(
            icon = Icons.Outlined.Share,
            title = "分享",
            desc = "生成分享链接（可设提取码/有效期）",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onShare
        )
        CloudActionItem(
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            title = "移动到",
            desc = "移动到网盘的其他目录",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onMove
        )
        CloudActionItem(
            icon = Icons.Outlined.Edit,
            title = "重命名",
            desc = "修改文件名",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onRename
        )
        CloudActionItem(
            icon = Icons.Outlined.Delete,
            title = "删除",
            desc = "移入回收站",
            tint = MiuixTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

/** 移动：浏览目标目录并确认（独立浏览状态 moveUiState，不影响主列表） */
@Composable
private fun MoveStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    moveState: QuarkCloudUiState,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "移动到", subtitle = file.fname, onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))
        val movePathNames = (moveState as? QuarkCloudUiState.Loaded)?.pathNames ?: emptyList()
        val moveBreadcrumbs = remember(movePathNames) {
            buildList {
                add(BreadcrumbItem(path = "root", text = "根目录"))
                movePathNames.forEachIndexed { index, name ->
                    add(BreadcrumbItem(path = (index + 1).toString(), text = name))
                }
            }
        }
        BreadcrumbBar(
            items = moveBreadcrumbs,
            onItemClick = { viewModel.moveNavigateToLevel(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if ((moveState as? QuarkCloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
            BackToParentItem(onClick = { viewModel.moveBack() })
            Spacer(modifier = Modifier.height(4.dp))
        }
        AnimatedContent(
            targetState = moveState,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "moveState"
        ) { s ->
            when (s) {
                is QuarkCloudUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) { InfiniteProgressIndicator() }

                // 目录选择失败必须给重试出口，否则用户既看不到目录也无法继续（死胡同）
                is QuarkCloudUiState.Error -> DrivePickerErrorPane(
                    message = s.message,
                    onRetry = { viewModel.retryMove() },
                    height = 180.dp
                )

                is QuarkCloudUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                    if (dirs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当前目录没有子文件夹，可直接移动到此处",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dirs, key = { it.fid }) { dir ->
                                ShareFileRow(file = dir, onClick = { viewModel.openMoveFolder(dir) })
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val dirName = (moveState as? QuarkCloudUiState.Loaded)?.pathNames?.lastOrNull() ?: "根目录"
        Button(
            onClick = {
                val to = (moveState as? QuarkCloudUiState.Loaded)?.dirFid ?: "0"
                viewModel.moveFile(to)
                onDone()
            },
            enabled = !operating,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (operating) {
                InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("移动到此处（$dirName）", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 分享：提取码 + 有效期设置 */
@Composable
private fun ShareStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "分享文件", subtitle = file.fname, onBack = onBack)

        Spacer(modifier = Modifier.height(16.dp))

        Text("提取码", style = MiuixTheme.textStyles.headline2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudSelectChip(
                selected = !withPassword,
                label = "无提取码",
                onClick = { withPassword = false }
            )
            CloudSelectChip(
                selected = withPassword,
                label = "设置提取码",
                onClick = {
                    withPassword = true
                    if (passcode.isBlank()) passcode = randomPasscode()
                }
            )
        }
        if (withPassword) {
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = passcode,
                onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = "4 位提取码",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("有效期", style = MiuixTheme.textStyles.headline2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            expireOptions.forEach { (name, value) ->
                CloudSelectChip(
                    selected = expiredType == value,
                    label = name,
                    onClick = { expiredType = value }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.shareFile(
                    urlType = if (withPassword) 2 else 1,
                    passcode = passcode,
                    expiredType = expiredType
                )
            },
            enabled = !operating && (!withPassword || passcode.length == 4),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (operating) {
                InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 重命名输入 */
@Composable
/**
 * 云盘文件/文件夹名称校验（创建与重命名共用）：非空、不是 `.`/`..`、不含 `/` `\` 与控制字符、不超过 255 字符。
 *
 * @return 不合法的原因（给用户看）；合法返回 null
 */
internal fun cloudNameError(raw: String): String? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> "名称不能为空"
        name == "." || name == ".." -> "名称不能是 . 或 .."
        name.length > 255 -> "名称最多 255 个字符"
        name.any { it == '/' || it == '\\' || it.code < 0x20 } -> "名称不能含 / \\ 或控制字符"
        else -> null
    }
}

@Composable
private fun RenameStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    var name by remember { mutableStateOf(file.fname) }
    val nameError = cloudNameError(name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "重命名", subtitle = file.fname, onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = "新文件名",
            useLabelAsPlaceholder = true,
            singleLine = true
        )
        // 名称不合法时给出原因（与创建文件夹同一套校验规则）
        if (nameError != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nameError,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.renameFile(name.trim())
                onDone()
            },
            enabled = !operating && nameError == null && name.trim() != file.fname,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Text(if (operating) "重命名中…" else "确认重命名", style = MiuixTheme.textStyles.button)
        }
    }
}

/** 删除确认（WindowDialog，彻底解决抽屉内嵌套 Overlay 冲突） */
@Composable
private fun DeleteStep(
    file: ShareFile,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    WindowDialog(
        show = true,
        onDismissRequest = { if (!operating) onBack() },
        title = "删除文件"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "确定要删除「${file.fname}」吗？删除后将移入回收站。",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.deleteFile()
                        onDone()
                    },
                    enabled = !operating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError
                    )
                ) {
                    Text("删除", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** 分享结果（WindowDialog）：链接 + 提取码 + 复制 */
@Composable
internal fun ShareResultDialog(
    info: com.yunx.app.data.network.model.ShareInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val platformName = when {
        info.shareUrl.contains("139.com") -> "139网盘"
        info.shareUrl.contains("123pan") || info.shareUrl.contains("123865") -> "123云盘"
        info.shareUrl.contains("uc.cn") -> "UC网盘"
        info.shareUrl.contains("xunlei.com") -> "迅雷网盘"
        info.shareUrl.contains("baidu.com") -> "百度网盘"
        else -> "夸克网盘"
    }
    val shareText = buildString {
        append("我用${platformName}分享了「${info.title}」\n")
        append("链接：${info.shareUrl}")
        if (info.passcode.isNotBlank()) {
            append("\n提取码：${info.passcode}")
        }
    }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "分享成功"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(12.dp)
            ) {
                Text(
                    text = shareText,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "有效期：" + expireLabel(info.expiredType),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "复制全部",
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("share_text", shareText))
                        SnackbarController.show("分享文案已复制")
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("完成", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

private fun randomPasscode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..4).map { chars.random() }.joinToString("")
}

private fun expireLabel(type: Int): String = when (type) {
    2 -> "1 天"
    3 -> "7 天"
    4 -> "30 天"
    else -> "永久有效"
}

/** 批量操作步骤类型 */
internal enum class BatchStep { MENU, SHARE, MOVE, DELETE }

/**
 * 批量操作抽屉（Miuix OverlayBottomSheet）：下载 / 分享 / 移动 / 删除。
 * 分享/移动/删除复用与单文件一致的表单与独立目录浏览。
 * @param initialStep 初始步骤（底部栏点击下载/删除直接执行，分享/移动传入对应步骤）
 */
@Composable
internal fun BatchActionSheet(
    viewModel: QuarkCloudViewModel,
    onDismiss: () -> Unit,
    initialStep: BatchStep = BatchStep.MENU
) {
    var step by remember { mutableStateOf(initialStep) }
    val moveState by viewModel.moveUiState.collectAsState()
    val operating = viewModel.isOperating
    val count = viewModel.selected.size

    val closeSheet = {
        if (!operating) {
            step = initialStep
            onDismiss()
        }
    }

    OverlayBottomSheet(
        show = true,
        onDismissRequest = closeSheet
    ) {
        when (step) {
            BatchStep.MENU -> BatchMenu(
                count = count,
                onDownload = {
                    viewModel.downloadSelected()
                    closeSheet()
                },
                onShare = { step = BatchStep.SHARE },
                onMove = {
                    viewModel.openMoveRoot()
                    step = BatchStep.MOVE
                },
                onDelete = { step = BatchStep.DELETE }
            )

            BatchStep.SHARE -> BatchShareStep(
                count = count,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = BatchStep.MENU }
            )

            BatchStep.MOVE -> BatchMoveStep(
                count = count,
                viewModel = viewModel,
                moveState = moveState,
                operating = operating,
                onBack = { step = BatchStep.MENU },
                onDone = closeSheet
            )

            BatchStep.DELETE -> BatchDeleteStep(
                count = count,
                viewModel = viewModel,
                operating = operating,
                onBack = { step = BatchStep.MENU },
                onDone = closeSheet
            )
        }
    }

    // 分享创建成功：展示链接与提取码（WindowDialog）
    viewModel.shareResult?.let { info ->
        ShareResultDialog(
            info = info,
            onDismiss = { viewModel.dismissShareResult() }
        )
    }
}

/** 批量操作菜单主界面 */
@Composable
private fun BatchMenu(
    count: Int,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = SquircleShape(12.dp),
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "批量操作",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "已选 $count 项",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CloudActionItem(
            icon = Icons.Outlined.Download,
            title = "下载",
            desc = "批量下载到本机",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onDownload
        )
        CloudActionItem(
            icon = Icons.Outlined.Share,
            title = "分享",
            desc = "将选中项创建为一个分享链接",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onShare
        )
        CloudActionItem(
            icon = Icons.AutoMirrored.Outlined.DriveFileMove,
            title = "移动到",
            desc = "批量移动到网盘的其他目录",
            tint = MiuixTheme.colorScheme.primary,
            onClick = onMove
        )
        CloudActionItem(
            icon = Icons.Outlined.Delete,
            title = "删除",
            desc = "批量移入回收站",
            tint = MiuixTheme.colorScheme.error,
            onClick = onDelete
        )
    }
}

/** 批量分享：提取码 + 有效期 */
@Composable
private fun BatchShareStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit
) {
    var withPassword by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }
    var expiredType by remember { mutableStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "分享文件", subtitle = "已选 $count 项", onBack = onBack)

        Spacer(modifier = Modifier.height(16.dp))

        Text("提取码", style = MiuixTheme.textStyles.headline2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudSelectChip(
                selected = !withPassword,
                label = "无提取码",
                onClick = { withPassword = false }
            )
            CloudSelectChip(
                selected = withPassword,
                label = "设置提取码",
                onClick = {
                    withPassword = true
                    if (passcode.isBlank()) passcode = randomPasscode()
                }
            )
        }
        if (withPassword) {
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = passcode,
                onValueChange = { passcode = it.take(4).filter { c -> c.isLetterOrDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = "4 位提取码",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("有效期", style = MiuixTheme.textStyles.headline2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            expireOptions.forEach { (name, value) ->
                CloudSelectChip(
                    selected = expiredType == value,
                    label = name,
                    onClick = { expiredType = value }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.shareSelected(
                    urlType = if (withPassword) 2 else 1,
                    passcode = passcode,
                    expiredType = expiredType
                )
            },
            enabled = !operating && (!withPassword || passcode.length == 4),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (operating) {
                InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建分享", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 批量移动：浏览目标目录并确认 */
@Composable
private fun BatchMoveStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    moveState: QuarkCloudUiState,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.openMoveRoot()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
    ) {
        CloudStepHeader(title = "移动到", subtitle = "已选 $count 项", onBack = onBack)

        Spacer(modifier = Modifier.height(8.dp))
        val batchMovePathNames = (moveState as? QuarkCloudUiState.Loaded)?.pathNames ?: emptyList()
        val batchMoveBreadcrumbs = remember(batchMovePathNames) {
            buildList {
                add(BreadcrumbItem(path = "root", text = "根目录"))
                batchMovePathNames.forEachIndexed { index, name ->
                    add(BreadcrumbItem(path = (index + 1).toString(), text = name))
                }
            }
        }
        BreadcrumbBar(
            items = batchMoveBreadcrumbs,
            onItemClick = { viewModel.moveNavigateToLevel(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if ((moveState as? QuarkCloudUiState.Loaded)?.pathNames?.isNotEmpty() == true) {
            BackToParentItem(onClick = { viewModel.moveBack() })
            Spacer(modifier = Modifier.height(4.dp))
        }
        AnimatedContent(
            targetState = moveState,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "batchMoveState"
        ) { s ->
            when (s) {
                is QuarkCloudUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) { InfiniteProgressIndicator() }

                // 目录选择失败必须给重试出口，否则用户既看不到目录也无法继续（死胡同）
                is QuarkCloudUiState.Error -> DrivePickerErrorPane(
                    message = s.message,
                    onRetry = { viewModel.retryMove() },
                    height = 180.dp
                )

                is QuarkCloudUiState.Loaded -> {
                    val dirs = s.files.filter { it.isdir }
                    if (dirs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "当前目录没有子文件夹，可直接移动到此处",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(dirs, key = { it.fid }) { dir ->
                                ShareFileRow(file = dir, onClick = { viewModel.openMoveFolder(dir) })
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val dirName = (moveState as? QuarkCloudUiState.Loaded)?.pathNames?.lastOrNull() ?: "根目录"
        Button(
            onClick = {
                val to = (moveState as? QuarkCloudUiState.Loaded)?.dirFid ?: "0"
                viewModel.moveSelected(to)
                onDone()
            },
            enabled = !operating,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            if (operating) {
                InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("移动到此处（$dirName）", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/** 批量删除确认（WindowDialog） */
@Composable
private fun BatchDeleteStep(
    count: Int,
    viewModel: QuarkCloudViewModel,
    operating: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    WindowDialog(
        show = true,
        onDismissRequest = { if (!operating) onBack() },
        title = "删除文件"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "确定要删除选中的 $count 项吗？删除后将移入回收站。",
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        onDone()
                    },
                    enabled = !operating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError
                    )
                ) {
                    Text("删除", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}