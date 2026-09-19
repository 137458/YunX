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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunx.app.data.db.BookmarkEntity
import com.yunx.app.data.network.ShareLinkParser
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.ui.viewmodel.BookmarkViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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

    val filtered = remember(bookmarks, selectedCategory) {
        val cat = selectedCategory
        if (cat == null) bookmarks else bookmarks.filter { it.category == cat }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = "收藏网盘链接",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加收藏")
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
                item(key = "empty") {
                    EmptyBookmark(onAdd = { showAddDialog = true })
                }
            } else {
                items(filtered, key = { it.id }) { bookmark ->
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
                    SnackbarController.show("链接已复制")
                },
                onEditCategory = {
                    menuBookmark = null
                    editingBookmark = bookmark
                },
                onDelete = {
                    menuBookmark = null
                    viewModel.delete(bookmark.id)
                },
                onDismiss = { menuBookmark = null }
            )
        }
    }
}

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
            label = "全部",
            onClick = { onSelect(null) }
        )
        categories.forEach { cat ->
            CategoryChip(
                selected = selected == cat,
                label = cat,
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
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bookmark.platform.isNotBlank()) {
                    Surface(
                        shape = SquircleShape(6.dp),
                        color = MiuixTheme.colorScheme.surfaceVariant
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
                    shape = SquircleShape(6.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = bookmark.category,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
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

/** 空状态 */
@Composable
private fun EmptyBookmark(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MiuixTheme.colorScheme.dividerLine
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "还没有收藏任何网盘链接",
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "可在解析页点击「添加至收藏」，或点击右上角 + 手动添加",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("添加收藏")
        }
    }
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
        title = "添加收藏",
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
                label = "网盘链接",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            TextField(
                value = title,
                onValueChange = { title = it },
                label = "标题（可选）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            TextField(
                value = pwd,
                onValueChange = { pwd = it },
                label = "提取码（可选）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "分类",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    selected = isCustom,
                    label = "自定义",
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.filter { it != CUSTOM_CATEGORY }.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = cat,
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
                    label = "自定义分类",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = "取消", onClick = onDismiss)
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
                    Text("收藏")
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
        title = "添加至收藏",
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
                label = "标题（可选）",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                text = "分类",
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(
                    selected = isCustom,
                    label = "自定义",
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.filter { it != CUSTOM_CATEGORY }.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = cat,
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
                    label = "自定义分类",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = "取消", onClick = onDismiss)
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
                    Text("收藏")
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
        title = "修改分类",
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
                    label = "自定义",
                    onClick = { selectedCategory = CUSTOM_CATEGORY }
                )
                categories.forEach { cat ->
                    CategoryChip(
                        selected = selectedCategory == cat,
                        label = cat,
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
                    label = "自定义分类",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !(isCustom && customCategory.isBlank()),
                    onClick = {
                        onConfirm(if (isCustom) customCategory.trim() else selectedCategory)
                    }
                ) {
                    Text("确定")
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
                text = "解析",
                onClick = onResolve,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "复制链接",
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "修改分类",
                onClick = onEditCategory,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = "删除",
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(text = "取消", onClick = onDismiss)
            }
        }
    }
}

/** 平台枚举名 → 展示名 */
internal fun bookmarkPlatformLabel(platform: String): String = when (platform) {
    "QUARK" -> "夸克网盘"
    "UC" -> "UC网盘"
    "XUNLEI" -> "迅雷网盘"
    "BAIDU" -> "百度网盘"
    "C139" -> "139网盘"
    "PAN123" -> "123云盘"
    else -> "网盘"
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("yunx_bookmark", text))
}