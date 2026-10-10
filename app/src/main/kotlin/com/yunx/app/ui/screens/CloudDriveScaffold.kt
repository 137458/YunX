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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.components.BreadcrumbBar
// CloudStepHeader / DrivePickerErrorPane 已下沉为 ui/drive 通用件；
// 此处别名引入，保持同包调用点（CloudDriveScreen.kt）不变
import com.yunx.app.ui.drive.CloudStepHeader as CloudStepHeader
import com.yunx.app.ui.drive.DrivePickerErrorPane as DrivePickerErrorPane
import com.yunx.app.ui.components.BreadcrumbItem
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.components.ScrollToTopButton
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.items.MultiSelectBar
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.theme.SquircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 6 大云盘通用内容骨架：
 * - 面包屑导航（BreadcrumbBar）
 * - 多选控制条（MultiSelectBar）
 * - 下拉刷新（PullToRefresh）
 * - 列表消费 LocalScaffoldPadding
 * - 返回顶部按钮（ScrollToTopButton）
 * - 目录级滚动位置保存与恢复
 */
@Composable
internal fun CloudDriveScaffold(
    driveTitle: String,
    files: List<ShareFile>,
    pathNames: List<String>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    multiSelectMode: Boolean,
    selectedFiles: Set<ShareFile>,
    scrollBehavior: ScrollBehavior,
    onExit: () -> Unit,
    onBackToParent: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
    onToggleSelectAll: (List<ShareFile>) -> Unit,
    onExitMultiSelect: () -> Unit,
    onFileClick: (ShareFile) -> Unit,
    onFileMoreClick: ((ShareFile) -> Unit)? = null,
    onFileLongClick: ((ShareFile) -> Unit)? = null,
    multiSelectActions: List<MultiSelectAction>,
    /** 批量操作执行中：禁用多选工具栏按钮，防止连点重复入队 */
    multiSelectBusy: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    scrollPositions: MutableMap<String, Int> = remember { mutableStateMapOf() },
    modifier: Modifier = Modifier
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }

    // 搜索过滤放到 Default 调度器并做 120ms 防抖：云盘目录动辄上千条，
    // 在组合期同步 filter 会让每次输入都掉帧。
    val displayFiles by produceState(initialValue = files, files, searchQuery) {
        val q = searchQuery.trim()
        value = if (q.isEmpty()) {
            files
        } else {
            delay(120)
            withContext(Dispatchers.Default) { files.filter { it.fname.contains(q, ignoreCase = true) } }
        }
    }

    val currentDirKey = remember(pathNames) { pathNames.joinToString("/") }
    LaunchedEffect(currentDirKey, displayFiles.size) {
        // 索引必须钳制：开启搜索后列表项变少，历史保存的下标可能越界
        val saved = scrollPositions[currentDirKey] ?: 0
        listState.scrollToItem(saved.coerceIn(0, displayFiles.size.coerceAtLeast(0)))
    }

    val recordScroll = {
        scrollPositions[currentDirKey] = listState.firstVisibleItemIndex
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefresh(
            pullToRefreshState = rememberPullToRefreshState(),
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            contentPadding = scaffoldPadding,
            topAppBarScrollBehavior = scrollBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + scaffoldPadding.calculateStartPadding(layoutDirection),
                    top = 16.dp + scaffoldPadding.calculateTopPadding(),
                    end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = (if (multiSelectMode) 96.dp else 16.dp) + scaffoldPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (multiSelectMode) {
                                IconButton(onClick = onExitMultiSelect) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "取消选择",
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "已选 ${selectedFiles.size} 项",
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (displayFiles.isNotEmpty() && selectedFiles.size == displayFiles.size) "已全选" else "点击选择更多文件",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                TextButton(
                                    text = if (displayFiles.isNotEmpty() && selectedFiles.size == displayFiles.size) "取消全选" else "全选",
                                    onClick = { onToggleSelectAll(displayFiles) }
                                )
                            } else {
                                IconButton(onClick = onExit) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回",
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = driveTitle,
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (searchQuery.isBlank()) "共 ${files.size} 项"
                                        else "匹配 ${displayFiles.size} / ${files.size} 项",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                IconButton(onClick = { showSearch = !showSearch }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = if (showSearch) "关闭搜索" else "搜索文件",
                                        tint = if (showSearch) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                            }
                        }

                        if (!multiSelectMode) {
                            val breadcrumbItems = remember(pathNames, driveTitle) {
                                buildList {
                                    add(BreadcrumbItem(path = "root", text = driveTitle))
                                    pathNames.forEachIndexed { index, name ->
                                        add(BreadcrumbItem(path = (index + 1).toString(), text = name))
                                    }
                                }
                            }
                            BreadcrumbBar(
                                items = breadcrumbItems,
                                onItemClick = { index ->
                                    recordScroll()
                                    onNavigateToLevel(index)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        AnimatedVisibility(
                            visible = showSearch && !multiSelectMode,
                            enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                            exit = shrinkVertically(tween(140)) + fadeOut(tween(120))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "搜索当前目录文件",
                                    useLabelAsPlaceholder = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "清空搜索",
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                if (pathNames.isNotEmpty()) {
                    item {
                        BackToParentItem(onClick = {
                            recordScroll()
                            onBackToParent()
                        })
                    }
                }

                if (displayFiles.isEmpty()) {
                    item {
                        CloudDriveEmptyView(
                            isEmpty = files.isEmpty(),
                            searchQuery = searchQuery
                        )
                    }
                }

                items(displayFiles, key = { it.fid }) { file ->
                    ShareFileRow(
                        file = file,
                        onClick = {
                            if (!multiSelectMode && file.isdir) {
                                recordScroll()
                            }
                            onFileClick(file)
                        },
                        onMore = if (!multiSelectMode && file.isdir && onFileMoreClick != null) {
                            { onFileMoreClick(file) }
                        } else null,
                        onLongClick = if (!multiSelectMode && onFileLongClick != null) {
                            { onFileLongClick(file) }
                        } else null,
                        selected = selectedFiles.contains(file),
                        showCheckbox = multiSelectMode
                    )
                }
            }
        }

        ScrollToTopButton(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp + scaffoldPadding.calculateEndPadding(layoutDirection),
                    bottom = (if (multiSelectMode) 104.dp else 16.dp) + scaffoldPadding.calculateBottomPadding()
                )
        )

        AnimatedVisibility(
            visible = multiSelectMode,
            enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scaffoldPadding.calculateBottomPadding())
        ) {
            MultiSelectBar(actions = multiSelectActions, busy = multiSelectBusy)
        }
    }
}

/** 错误重试视图（全屏居中，消费 LocalScaffoldPadding） */
@Composable
fun CloudDriveErrorView(
    message: String,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                // 错误文案直接来自异常 message，可能含 URL/JSON 而极长：
                // 限行 + 省略号，并允许滚动，避免撑破屏幕或顶出可视区。
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onExit) {
                    Text("返回", style = MiuixTheme.textStyles.button)
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("重试", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/** 加载中视图（全屏居中，消费 LocalScaffoldPadding） */
@Composable
fun CloudDriveLoadingView(modifier: Modifier = Modifier) {
    val scaffoldPadding = LocalScaffoldPadding.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(scaffoldPadding),
        contentAlignment = Alignment.Center
    ) {
        InfiniteProgressIndicator()
    }
}

/** 空目录 / 无匹配结果文本项 */
@Composable
fun CloudDriveEmptyView(
    isEmpty: Boolean,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    Text(
        text = if (isEmpty) "此目录为空" else "未找到匹配「${searchQuery.trim()}」的文件",
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        textAlign = TextAlign.Center
    )
}

/** 通用删除二次确认弹窗（WindowDialog，无 Scrim 叠黑与 Back 拦截冲突） */
@Composable
fun CloudDriveDeleteDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String = "删除文件",
    message: String,
    onConfirm: () -> Unit
) {
    if (!show) return
    WindowDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body1,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onDismissRequest()
                        onConfirm()
                    },
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
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

/** 通用重命名弹窗（WindowDialog + Miuix TextField） */
@Composable
fun CloudDriveRenameDialog(
    show: Boolean,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return
    var name by remember(initialName) { mutableStateOf(initialName) }
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = "重命名"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                label = "新文件名",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onDismiss()
                        if (name.isNotBlank() && name != initialName) {
                            onConfirm(name.trim())
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text("确定", style = MiuixTheme.textStyles.button)
                }
            }
        }
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

/** 抽屉内通用操作项行（Squircle 图标底板 + 标题 + 副标题） */
@Composable
fun CloudActionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = SquircleShape(10.dp),
            color = tint.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/** Miuix 纯原生胶囊选择标签（替代 MD3 FilterChip） */
@Composable
fun CloudSelectChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape(10.dp),
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
