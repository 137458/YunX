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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.network.model.ShareFile
import com.yunx.app.R
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.components.LocalScaffoldPadding
import com.yunx.app.ui.components.ScrollToTopButton
import com.yunx.app.ui.drive.CloudStepHeader as CloudStepHeader
import com.yunx.app.ui.drive.DrivePickerErrorPane as DrivePickerErrorPane
import com.yunx.app.ui.items.MultiSelectAction
import com.yunx.app.ui.items.MultiSelectBar
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.resolve.BackToParentItem
import com.yunx.app.ui.resolve.ShareFileRow
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
// CloudStepHeader / DrivePickerErrorPane 已下沉为 ui/drive 通用件；
// 此处别名引入，保持同包调用点（CloudDriveScreen.kt）不变

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
    /** 点「+」→「新建文件夹」；与 [onUploadFile] 同为 null 时不显示「+」入口 */
    onCreateFolder: ((String) -> Unit)? = null,
    /** 点「+」→「上传文件」（选文件器由调用方弹，选中后回调）；null 时该项不显示 */
    onUploadFile: (() -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    scrollPositions: MutableMap<String, Int> = remember { mutableStateMapOf() },
    modifier: Modifier = Modifier
) {
    val scaffoldPadding = LocalScaffoldPadding.current
    val layoutDirection = LocalLayoutDirection.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // 「+」菜单与「新建文件夹」输入弹窗的开合状态
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }

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
                                        contentDescription = AppStrings.get(R.string.csa_cd_cancel_selection),
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = AppStrings.get(R.string.csa_selected_count, selectedFiles.size),
                                        style = MiuixTheme.textStyles.title4,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (displayFiles.isNotEmpty() && selectedFiles.size == displayFiles.size) AppStrings.get(R.string.csa_all_selected) else AppStrings.get(R.string.csa_click_select_more),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                TextButton(
                                    text = if (displayFiles.isNotEmpty() && selectedFiles.size == displayFiles.size) AppStrings.get(R.string.csa_deselect_all) else AppStrings.get(R.string.csa_select_all),
                                    onClick = { onToggleSelectAll(displayFiles) }
                                )
                            } else {
                                IconButton(onClick = onExit) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = AppStrings.get(R.string.csa_back),
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
                                        text = if (searchQuery.isBlank()) AppStrings.get(R.string.csa_total_count, files.size)
                                        else AppStrings.get(R.string.csa_match_count, displayFiles.size, files.size),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                                // 「+」：新建文件夹 / 上传文件（与上游各平台页的位置一致，放在搜索左边）
                                if (onCreateFolder != null || onUploadFile != null) {
                                    IconButton(onClick = { showAddMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = AppStrings.get(R.string.csa_new_or_upload),
                                            tint = MiuixTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                IconButton(onClick = { showSearch = !showSearch }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = if (showSearch) AppStrings.get(R.string.csa_cd_close_search) else AppStrings.get(R.string.csa_cd_search_files),
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
                                    label = AppStrings.get(R.string.csa_search_current_dir),
                                    useLabelAsPlaceholder = true,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = AppStrings.get(R.string.csa_cd_clear_search),
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

        // 「+」菜单：新建文件夹 / 上传文件（底部抽屉，与文件操作抽屉同一套视觉）
        if (showAddMenu) {
            OverlayBottomSheet(show = true, onDismissRequest = { showAddMenu = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 32.dp)
                ) {
                    if (onCreateFolder != null) {
                        CloudActionItem(
                            icon = Icons.Outlined.CreateNewFolder,
                            title = AppStrings.get(R.string.csa_new_folder),
                            desc = AppStrings.get(R.string.csa_new_folder_desc),
                            tint = MiuixTheme.colorScheme.primary,
                            onClick = {
                                showAddMenu = false
                                showCreateFolder = true
                            }
                        )
                    }
                    if (onUploadFile != null) {
                        CloudActionItem(
                            icon = Icons.Outlined.Upload,
                            title = AppStrings.get(R.string.csa_upload_file),
                            desc = AppStrings.get(R.string.csa_upload_desc),
                            tint = MiuixTheme.colorScheme.primary,
                            onClick = {
                                showAddMenu = false
                                onUploadFile()
                            }
                        )
                    }
                }
            }
        }
    }

    // 「新建文件夹」输入弹窗：复用重命名弹窗，只改标题与占位文案
    val createFolder = onCreateFolder
    if (createFolder != null) {
        CloudDriveRenameDialog(
            show = showCreateFolder,
            initialName = "",
            onConfirm = { name -> createFolder(name) },
            onDismiss = { showCreateFolder = false },
            title = AppStrings.get(R.string.csa_new_folder),
            label = AppStrings.get(R.string.csa_folder_name)
        )
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
                    Text(AppStrings.get(R.string.csa_back), style = MiuixTheme.textStyles.button)
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(AppStrings.get(R.string.csa_retry), style = MiuixTheme.textStyles.button)
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
        text = if (isEmpty) AppStrings.get(R.string.csa_dir_empty) else AppStrings.get(R.string.csa_no_match_files, searchQuery.trim()),
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
    title: String = AppStrings.get(R.string.csa_delete_file_title),
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
                    text = AppStrings.get(R.string.csa_cancel),
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
                    Text(AppStrings.get(R.string.csa_delete), style = MiuixTheme.textStyles.button)
                }
            }
        }
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

/**
 * 云盘文件/文件夹名称校验（创建与重命名共用）：非空、不是 `.`/`..`、不含 `/` `\` 与控制字符、不超过 255 字符。
 *
 * @return 不合法的原因（给用户看）；合法返回 null
 */
internal fun cloudNameError(raw: String): String? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> AppStrings.get(R.string.csa_name_error_empty)
        name == "." || name == ".." -> AppStrings.get(R.string.csa_name_error_dots)
        name.length > 255 -> AppStrings.get(R.string.csa_name_error_too_long)
        name.any { it == '/' || it == '\\' || it.code < 0x20 } -> AppStrings.get(R.string.csa_name_error_invalid_chars)
        else -> null
    }
}

/** 通用重命名弹窗（WindowDialog + Miuix TextField） */
@Composable
fun CloudDriveRenameDialog(
    show: Boolean,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    /** 弹窗标题；新建文件夹时传「新建文件夹」即可复用本弹窗 */
    title: String = AppStrings.get(R.string.csa_rename),
    /** 输入框占位文案 */
    label: String = AppStrings.get(R.string.csa_new_file_name),
) {
    if (!show) return
    var name by remember(initialName) { mutableStateOf(initialName) }
    var nameEdited by remember(initialName) { mutableStateOf(false) }
    val nameError = cloudNameError(name)
    WindowDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameEdited = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (nameError == null || !nameEdited) 16.dp else 6.dp),
                label = label,
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            if (nameError != null && nameEdited) {
                Text(
                    text = nameError,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onDismiss()
                        if (nameError == null && name.trim() != initialName) {
                            onConfirm(name.trim())
                        }
                    },
                    enabled = nameError == null && name.trim() != initialName,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(AppStrings.get(R.string.rs_confirm), style = MiuixTheme.textStyles.button)
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
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
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
