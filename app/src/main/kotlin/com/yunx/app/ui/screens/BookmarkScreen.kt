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

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.R
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.EmptyState
import com.yunx.app.ui.components.rememberBlurBackdrop
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.viewmodel.BookmarkViewModel
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「自定义分类」虚拟选项标识（不参与持久化，仅用于弹窗交互） */
private const val CUSTOM_CATEGORY = "__custom__"

/**
 * 收藏网盘链接页：分类筛选 + 收藏列表，支持新增 / 解析 / 复制 / 修改分类 / 删除。
 * Miuix 规范重构：Scaffold、TopAppBar、Card、TextField、Button、OverlayDialog 驱动。
 */
@Composable
fun BookmarkScreen(
    viewModel: BookmarkViewModel,
    onBack: () -> Unit,
    /** 点击收藏 → 关闭本页并切到解析页自动解析该链接 */
    onResolve: (link: String, pwd: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    // null 表示「全部」
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var menuBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    // 待确认删除的收藏：删除不可逆，先弹确认框再落库
    var pendingDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

    val filtered = remember(bookmarks, selectedCategory) {
        val cat = selectedCategory
        if (cat == null) bookmarks else bookmarks.filter { it.category == cat }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    // 返回由 NavHost 处理（本页已是 destination），系统预测性返回动画得以保留

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = AppStrings.get(R.string.rs_bookmarks_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.get(R.string.csa_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = AppStrings.get(R.string.rs_add_bookmark))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .blurBackdropSource(backdrop)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            // 分类筛选：作为列表头部，随列表一起上下滚动
            item(key = "categories") {
                CategoryFilterBar(
                    categories = categories,
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it }
                )
            }

            if (filtered.isEmpty()) {
                // 区分「完全没有收藏」与「当前分类下没有收藏」：两者文案与出口操作都不同，
                // 否则筛选为空时会误报「还没有收藏任何网盘链接」，用户无法理解。
                item(key = "empty") {
                    EmptyBookmark(
                        filtered = bookmarks.isNotEmpty(),
                        onAdd = { showAddDialog = true },
                        onClearFilter = { selectedCategory = null }
                    )
                }
            } else {
                items(filtered, key = { it.id }, contentType = { "bookmark" }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onClick = { onResolve(bookmark.link, bookmark.pwd) },
                        onLongClick = { menuBookmark = bookmark }
                    )
                }
            }
        }
        }

        // 添加收藏弹窗（Miuix OverlayDialog）
        AddBookmarkDialog(
            show = showAddDialog,
            categories = categories,
            onConfirm = { link, title, category, pwd ->
                showAddDialog = false
                val parsed = ShareLinkParser.parse(link)
                viewModel.addBookmark(
                    link = link,
                    title = title,
                    platform = parsed?.platform?.name.orEmpty(),
                    pwd = pwd.ifBlank { parsed?.pwd.orEmpty() },
                    category = category
                )
            },
            onDismiss = { showAddDialog = false }
        )

        // 修改分类弹窗（Miuix OverlayDialog）
        editingBookmark?.let { bookmark ->
            EditCategoryDialog(
                show = true,
                currentCategory = bookmark.category,
                categories = categories,
                onConfirm = { category ->
                    viewModel.updateCategory(bookmark.id, category)
                    editingBookmark = null
                },
                onDismiss = { editingBookmark = null }
            )
        }

        // 长按操作菜单（Miuix OverlayDialog）
        menuBookmark?.let { bookmark ->
            BookmarkMenuDialog(
                show = true,
                bookmark = bookmark,
                onResolve = {
                    menuBookmark = null
                    onResolve(bookmark.link, bookmark.pwd)
                },
                onCopy = {
                    menuBookmark = null
                    copyToClipboard(context, bookmark.link)
                    SnackbarController.show(AppStrings.get(R.string.rs_link_copied))
                },
                onEditCategory = {
                    menuBookmark = null
                    editingBookmark = bookmark
                },
                onDelete = {
                    // 先关菜单再弹确认框，避免两个 OverlayDialog 同时挂载
                    menuBookmark = null
                    pendingDelete = bookmark
                },
                onDismiss = { menuBookmark = null }
            )
        }

        // 删除确认：收藏删除不可逆，先二次确认再落库
        pendingDelete?.let { target ->
            DeleteBookmarkDialog(
                show = true,
                title = target.title.ifBlank { target.link },
                onConfirm = {
                    viewModel.delete(target.id)
                    pendingDelete = null
                    SnackbarController.show(AppStrings.get(R.string.vm_bookmark_deleted))
                },
                onDismiss = { pendingDelete = null }
            )
        }
    }
}

/** 预置分类按当前语言展示；用户自建分类原样返回 */
private fun categoryLabel(category: String): String =
    BookmarkEntity.presetLabelRes(category)?.let { AppStrings.get(it) } ?: category

/** 分类筛选胶囊：全部 + 各分类 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilterBar(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CategoryChip(
            selected = selected == null,
            label = AppStrings.get(R.string.rs_all),
            onClick = { onSelect(null) }
        )
        categories.forEach { cat ->
            CategoryChip(
                selected = selected == cat,
                label = categoryLabel(cat),
                onClick = { onSelect(cat) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = SquircleShape(10.dp),
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 收藏列表项：平台 / 分类标签 + 标题 + 链接，点击解析、长按打开菜单 */
@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // 用 Miuix Card 自带的手势参数：Card 内部 pressable 会拦截 combinedClickable，
    // 直接套 combinedClickable 会导致点击/长按无反应。
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onLongPress = onLongClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bookmark.platform.isNotBlank()) {
                    Surface(
                        shape = SquircleShape(6.dp),
                        color = MiuixTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = bookmarkPlatformLabel(bookmark.platform),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Surface(
                    // 分类名由用户自定义，必须限宽 + 单行省略，否则长分类名会撑破卡片行
                    modifier = Modifier.widthIn(max = 120.dp),
                    shape = SquircleShape(6.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = categoryLabel(bookmark.category),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bookmark.title.ifBlank { bookmark.link },
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (bookmark.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = bookmark.link,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 空状态。
 * @param filtered true 表示"有收藏但当前分类下为空"，此时应提示清除筛选而不是引导添加。
 */
@Composable
private fun EmptyBookmark(
    filtered: Boolean,
    onAdd: () -> Unit,
    onClearFilter: () -> Unit
) {
    EmptyState(
        icon = Icons.Outlined.BookmarkBorder,
        title = if (filtered) AppStrings.get(R.string.vm_bookmark_empty_filtered) else AppStrings.get(R.string.rs_no_bookmarks),
        hint = if (filtered) {
            AppStrings.get(R.string.vm_bookmark_empty_filtered_hint)
        } else {
            AppStrings.get(R.string.rs_no_bookmarks_hint)
        },
        action = {
            Button(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(AppStrings.get(R.string.rs_add_bookmark))
            }
        }
    )
}

/** 新增收藏弹窗（Miuix OverlayDialog） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddBookmarkDialog(
    show: Boolean,
    categories: List<String>,
    onConfirm: (link: String, title: String, category: String, pwd: String) -> Unit,
    onDismiss: () -> Unit
) {
    var link by remember(show) { mutableStateOf("") }
    var title by remember(show) { mutableStateOf("") }
    var pwd by remember(show) { mutableStateOf("") }
    var selectedCategory by remember(show) { mutableStateOf(BookmarkEntity.DEFAULT_CATEGORY) }
    var customCategory by remember(show) { mutableStateOf("") }
    val isCustom = selectedCategory == CUSTOM_CATEGORY
    val scrollState = rememberScrollState()

    LaunchedEffect(isCustom) {
        if (isCustom) {
            kotlinx.coroutines.delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    OverlayDialog(
        show = show,
        title = AppStrings.get(R.string.rs_add_bookmark),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 440.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = link,
                onValueChange = { link = it },
                label = AppStrings.get(R.string.rs_share_link_label),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                label = AppStrings.get(R.string.rs_title_optional),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = AppStrings.get(R.string.rs_extract_code_optional),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = AppStrings.get(R.string.rs_category),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    selected = isCustom,
                    label = AppStrings.get(R.string.rs_custom),
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.filter { it != CUSTOM_CATEGORY }.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = categoryLabel(cat),
                        onClick = { selectedCategory = cat }
                    )
                }
            }
            AnimatedVisibility(
                visible = isCustom,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                TextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = AppStrings.get(R.string.rs_custom_category),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = link.isNotBlank() && !(isCustom && customCategory.isBlank()),
                    onClick = {
                        onConfirm(
                            link.trim(),
                            title.trim(),
                            if (isCustom) customCategory.trim() else selectedCategory,
                            pwd.trim()
                        )
                    }
                ) {
                    Text(AppStrings.get(R.string.rs_bookmark))
                }
            }
        }
    }
}

/** 解析详情页「添加至收藏」弹窗（Miuix OverlayDialog） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddToBookmarkDialog(
    title: String,
    initialCategory: String,
    categories: List<String>,
    onConfirm: (title: String, category: String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf(title) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var customCategory by remember { mutableStateOf("") }
    val isCustom = selectedCategory == CUSTOM_CATEGORY

    OverlayDialog(
        show = true,
        title = AppStrings.get(R.string.rs_add_to_bookmark),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = AppStrings.get(R.string.rs_title_optional),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = AppStrings.get(R.string.rs_category),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    selected = isCustom,
                    label = AppStrings.get(R.string.rs_custom),
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.filter { it != CUSTOM_CATEGORY }.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = categoryLabel(cat),
                        onClick = { selectedCategory = cat }
                    )
                }
            }
            AnimatedVisibility(
                visible = isCustom,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                TextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = AppStrings.get(R.string.rs_custom_category),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !(isCustom && customCategory.isBlank()),
                    onClick = {
                        onConfirm(
                            titleInput.trim(),
                            if (isCustom) customCategory.trim() else selectedCategory
                        )
                    }
                ) {
                    Text(AppStrings.get(R.string.rs_bookmark))
                }
            }
        }
    }
}

/** 修改分类弹窗（Miuix OverlayDialog） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCategoryDialog(
    show: Boolean,
    currentCategory: String,
    categories: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember(show) { mutableStateOf(currentCategory) }
    var customCategory by remember(show) { mutableStateOf("") }
    val isCustom = selectedCategory == CUSTOM_CATEGORY
    val scrollState = rememberScrollState()

    LaunchedEffect(isCustom) {
        if (isCustom) {
            kotlinx.coroutines.delay(250)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    OverlayDialog(
        show = show,
        title = AppStrings.get(R.string.rs_edit_category),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(max = 440.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    selected = isCustom,
                    label = AppStrings.get(R.string.rs_custom),
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = categoryLabel(cat),
                        onClick = { selectedCategory = cat }
                    )
                }
            }
            AnimatedVisibility(
                visible = isCustom,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                TextField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    label = AppStrings.get(R.string.rs_custom_category),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !(isCustom && customCategory.isBlank()),
                    onClick = {
                        onConfirm(if (isCustom) customCategory.trim() else selectedCategory)
                    }
                ) {
                    Text(AppStrings.get(R.string.rs_confirm))
                }
            }
        }
    }
}

/** 长按操作菜单（Miuix OverlayDialog） */
@Composable
private fun BookmarkMenuDialog(
    show: Boolean,
    bookmark: BookmarkEntity,
    onResolve: () -> Unit,
    onCopy: () -> Unit,
    onEditCategory: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = bookmark.title.ifBlank { bookmark.link },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextButton(
                text = AppStrings.get(R.string.em_what_parse),
                onClick = onResolve,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.rs_copy_link),
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.rs_edit_category),
                onClick = onEditCategory,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.csa_delete),
                onClick = onDelete,
                textStyle = MiuixTheme.textStyles.button.copy(color = MiuixTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = onDismiss)
            }
        }
    }
}

/** 删除确认弹窗：收藏删除不可逆，必须二次确认（Miuix OverlayDialog） */
@Composable
private fun DeleteBookmarkDialog(
    show: Boolean,
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = AppStrings.get(R.string.vm_bookmark_delete_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.vm_bookmark_delete_message, title),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError
                    )
                ) {
                    Text(AppStrings.get(R.string.csa_delete))
                }
            }
        }
    }
}

/** 平台枚举名 → 展示名 */
internal fun bookmarkPlatformLabel(platform: String): String = when (platform) {
    "QUARK" -> AppStrings.get(R.string.platform_quark)
    "UC" -> AppStrings.get(R.string.platform_uc)
    "XUNLEI" -> AppStrings.get(R.string.platform_xunlei)
    "BAIDU" -> AppStrings.get(R.string.platform_baidu)
    "C139" -> AppStrings.get(R.string.platform_c139)
    "PAN123" -> AppStrings.get(R.string.platform_pan123)
    else -> AppStrings.get(R.string.rs_platform_generic)
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_bookmark", text))
}
