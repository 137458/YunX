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

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.R
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.rememberBlurBackdrop
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.readableSeedForeground
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 预置主题色（Material 风格种子色） */
private val presetColors = listOf(
    R.string.color_blue to 0xFF415F91L,
    R.string.color_indigo to 0xFF3F51B5L,
    R.string.color_purple to 0xFF6750A4L,
    R.string.color_rose to 0xFFC2185BL,
    R.string.color_red to 0xFFB3261EL,
    R.string.color_orange to 0xFFF4631CL,
    R.string.color_gold to 0xFFF9A825L,
    R.string.color_green to 0xFF38761DL,
    R.string.color_cyan to 0xFF00897BL,
    R.string.color_sky to 0xFF0288D1L,
)

/** 底部导航栏样式选项（顺序与 BottomBarStyle.ordinal 一一对应，不可调序） */
private val bottomBarStyleOptions = listOf(
    R.string.theme_bottom_bar_liquid,
    R.string.theme_bottom_bar_frosted,
    R.string.theme_bottom_bar_standard
)

/** 各档位说明文案（与 bottomBarStyleOptions 同序；模糊与折射均依赖 miuix-blur 的 RuntimeShader） */
private val bottomBarStyleHints = listOf(
    R.string.theme_bottom_bar_hint_liquid,
    R.string.theme_bottom_bar_hint_frosted,
    R.string.theme_bottom_bar_hint_standard
)

/**
 * 主题与外观设置页（Miuix 规范重构）：
 * - 外观模式：Squircle 胶囊单选（跟随系统 / 浅色 / 深色）+ AMOLED 纯黑开关（仅深色可用）
 * - 主题色：Miuix Card 分组，动态色彩开关（Android 12+）+ LazyRow 色圆选择 + 自定义调色盘（Miuix OverlayDialog + Slider）
 * - 实时预览：用当前 colorScheme 绘制主色/表面/文字示例，设置改动后立即刷新
 * - 底部导航栏：液态玻璃 / 毛玻璃 / 标准三档切换
 * - 桌面图标：Miuix Card 分组，Squircle 预览
 * - 对接 AppThemeManager 内存与持久化状态
 */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }
    var showColorPicker by remember { mutableStateOf(false) }

    // Android 12- 动态色不可用，视为默认蓝色
    val effectiveColorMode = if (AppThemeManager.colorMode == 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        1
    } else {
        AppThemeManager.colorMode
    }

    // 当前是否呈现深色外观：AMOLED 纯黑只在深色下有意义，浅色时开关禁用
    val darkAppearance = when (AppThemeManager.darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    // ---------- 桌面图标动态切换 ----------
    val settingsRepo = remember { SettingsRepository(context) }
    var appIconVariant by remember { mutableStateOf(settingsRepo.appIconVariant) }
    val switchAppIcon: (Int) -> Unit = { variant ->
        val pm = context.packageManager
        val main = ComponentName(context, "com.yunx.app.MainActivity")
        val alias = ComponentName(context, "com.yunx.app.MainActivityIcon2")
        if (variant == 1) {
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(main, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } else {
            pm.setComponentEnabledSetting(main, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        settingsRepo.appIconVariant = variant
    }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = AppStrings.get(R.string.theme_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.get(R.string.csa_back))
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp
                    )
            ) {
            // ---------- 外观模式 ----------
            SmallTitle(text = AppStrings.get(R.string.section_appearance_mode))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.get(R.string.appearance_mode_hint),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(AppStrings.get(R.string.language_follow_system), AppStrings.get(R.string.appearance_light), AppStrings.get(R.string.appearance_dark))
                        modes.forEachIndexed { index, label ->
                            SmoothFilterChip(
                                selected = AppThemeManager.darkMode == index,
                                label = label,
                                onClick = { AppThemeManager.setDarkMode(context, index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                // AMOLED 纯黑：只在深色外观下有意义，浅色时禁用并由 summary 说明原因
                SwitchPreference(
                    checked = AppThemeManager.isAmoled,
                    onCheckedChange = { AppThemeManager.setAmoled(context, it) },
                    title = AppStrings.get(R.string.theme_amoled_switch),
                    summary = if (darkAppearance) {
                        AppStrings.get(R.string.theme_amoled_on_desc)
                    } else {
                        AppStrings.get(R.string.theme_amoled_off_desc)
                    },
                    enabled = darkAppearance
                )
            }

            // ---------- 当前主题实时预览 ----------
            ThemePreviewCard()

            // ---------- 主题色彩 ----------
            SmallTitle(text = AppStrings.get(R.string.theme_color_palette_label))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                // 动态色彩开关（仅 Android 12+）：Miuix 规范使用 SwitchPreference
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchPreference(
                        title = AppStrings.get(R.string.theme_color_dynamic_switch),
                        summary = AppStrings.get(R.string.theme_color_dynamic_hint),
                        checked = AppThemeManager.colorMode == 0,
                        onCheckedChange = { on ->
                            AppThemeManager.setColorMode(context, if (on) 0 else 1)
                        }
                    )
                }

                // 主题色选择（动态色彩开启时折叠）
                AnimatedVisibility(visible = effectiveColorMode != 0) {
                    Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = AppStrings.get(R.string.theme_recommended_colors),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp)
                            ) {
                                itemsIndexed(presetColors) { _, (nameRes, color) ->
                                    val isSelected = (effectiveColorMode == 1 && color == 0xFF415F91L) ||
                                        (effectiveColorMode == 2 && AppThemeManager.seedColor == color)
                                    ColorSelectionItem(
                                        color = color,
                                        name = stringResource(nameRes),
                                        isSelected = isSelected,
                                        onClick = { AppThemeManager.setSeedColor(context, color) }
                                    )
                                }
                                item {
                                    val isCustomSelected = effectiveColorMode == 2 &&
                                        AppThemeManager.seedColor !in presetColors.map { it.second }
                                    CustomColorButton(
                                        isSelected = isCustomSelected,
                                        customColor = AppThemeManager.seedColor,
                                        onClick = {
                                            AppThemeManager.setColorMode(context, 2)
                                            showColorPicker = true
                                        }
                                    )
                                }
                            }
                        }
                    }
            }

            // ---------- 底部导航栏 ----------
            SmallTitle(text = AppStrings.get(R.string.theme_bottom_bar_section))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.get(R.string.theme_bottom_bar_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bottomBarStyleOptions.forEachIndexed { index, labelRes ->
                            SmoothFilterChip(
                                selected = AppThemeManager.bottomBarStyle == index,
                                label = stringResource(labelRes),
                                onClick = { AppThemeManager.setBottomBarStyle(context, index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = bottomBarStyleHints.getOrNull(AppThemeManager.bottomBarStyle)
                            ?.let { stringResource(it) } ?: "",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            // ---------- 桌面图标 ----------
            SmallTitle(text = AppStrings.get(R.string.section_desktop_icon))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        AppIconOption(
                            iconRes = R.drawable.icon,
                            name = AppStrings.get(R.string.icon_classic),
                            isSelected = appIconVariant == 0,
                            onClick = {
                                appIconVariant = 0
                                switchAppIcon(0)
                            }
                        )
                        AppIconOption(
                            iconRes = R.drawable.icon2,
                            name = AppStrings.get(R.string.icon_new),
                            isSelected = appIconVariant == 1,
                            onClick = {
                                appIconVariant = 1
                                switchAppIcon(1)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = AppStrings.get(R.string.icon_apply_hint),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
        }
    }

    // 自定义调色盘（Miuix OverlayDialog）
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = AppThemeManager.seedColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                AppThemeManager.setSeedColor(context, color)
                showColorPicker = false
            }
        )
    }
}

/** 胶囊单选按钮（SquircleShape 风格） */
@Composable
private fun SmoothFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = 200
    val containerColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
        label = "chipContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = duration, easing = LinearEasing),
        label = "chipContent"
    )
    Surface(
        onClick = onClick,
        // 触摸目标下限 48dp（无障碍规范）：高度不再用固定 38dp，命中区必须覆盖整个胶囊
        modifier = modifier.heightIn(min = 48.dp),
        shape = SquircleShape(12.dp),
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

/**
 * 当前主题实时预览卡片：直接用 MiuixTheme.colorScheme 绘制主色块、表面与文字示例，
 * 主题设置改动后随重组立即刷新，用来直观确认配色效果。
 */
@Composable
private fun ThemePreviewCard() {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = AppStrings.get(R.string.theme_preview_title),
                style = MiuixTheme.textStyles.subtitle,
                color = colors.onSurface
            )
            Text(
                text = AppStrings.get(R.string.theme_preview_desc),
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 主色示例块
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SquircleShape(14.dp))
                        .background(colors.primary)
                )
                // 表面 + 正文/次要文字示例
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.dividerLine, SquircleShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = AppStrings.get(R.string.theme_preview_body),
                            style = MiuixTheme.textStyles.body1,
                            color = colors.onSurface
                        )
                        Text(
                            text = AppStrings.get(R.string.theme_preview_caption),
                            style = MiuixTheme.textStyles.body2,
                            color = colors.onSurfaceSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 容器分层：AMOLED 下 High/Highest 仍保留灰阶，用于区分卡片分组边界
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewSwatch(color = colors.surfaceContainer, label = AppStrings.get(R.string.theme_preview_container), modifier = Modifier.weight(1f))
                PreviewSwatch(color = colors.surfaceContainerHigh, label = AppStrings.get(R.string.theme_preview_container_high), modifier = Modifier.weight(1f))
                PreviewSwatch(color = colors.surfaceContainerHighest, label = AppStrings.get(R.string.theme_preview_container_highest), modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 预览色块：容器色 + 名称，用于展示当前方案的分层灰阶 */
@Composable
private fun PreviewSwatch(
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(SquircleShape(8.dp))
                .background(color)
                .border(1.dp, MiuixTheme.colorScheme.dividerLine, SquircleShape(8.dp))
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

/** 预置色圆点（Squircle 形状 + 名称，选中高亮并显示对勾） */
@Composable
internal fun ColorSelectionItem(
    color: Long,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SquircleShape(14.dp))
                .background(Color(color.toInt()))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine,
                    shape = SquircleShape(14.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = readableSeedForeground(Color(color.toInt())),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MiuixTheme.textStyles.footnote1,
            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

/** 自定义颜色按钮（Squircle 形状 + 名称，显示 Palette 图标） */
@Composable
private fun CustomColorButton(
    isSelected: Boolean,
    customColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = AppStrings.get(R.string.rs_custom) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SquircleShape(14.dp))
                .background(
                    if (isSelected) Color(customColor.toInt())
                    else MiuixTheme.colorScheme.surfaceContainerHigh
                )
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine,
                    shape = SquircleShape(14.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = if (isSelected) {
                    readableSeedForeground(Color(customColor.toInt()))
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = AppStrings.get(R.string.rs_custom),
            style = MiuixTheme.textStyles.footnote1,
            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

// ================= 调色盘 Dialog（Miuix OverlayDialog + Miuix ColorPicker） =================

@Composable
internal fun ColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    var currentColor by remember { mutableStateOf(Color(initialColor.toInt())) }
    var hexInput by remember(currentColor) {
        mutableStateOf(colorToHex(currentColor).removePrefix("#"))
    }

    OverlayDialog(
        show = true,
        title = AppStrings.get(R.string.theme_custom_color_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 顶部：HEX 输入 + 色块预览
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#",
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                            hexInput = filtered
                            if (filtered.length == 6) {
                                runCatching {
                                    currentColor = hexToColor(filtered)
                                }
                            }
                        },
                        textStyle = MiuixTheme.textStyles.title1.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                        modifier = Modifier.width(130.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SquircleShape(10.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MiuixTheme.colorScheme.dividerLine, SquircleShape(10.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(currentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Miuix 官方取色器组件（色相/饱和度/明度调节）
            top.yukonga.miuix.kmp.basic.ColorPicker(
                color = currentColor,
                onColorChanged = { currentColor = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 底部操作按钮（Miuix Button & TextButton）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onColorSelected(currentColor.toArgb().toLong() and 0xFFFFFFFFL) }
                ) {
                    Text(AppStrings.get(R.string.action_apply))
                }
            }
        }
    }
}


// ================= 桌面图标选项 =================

/** 桌面图标选项：Squircle 图标预览 + 名称，选中高亮边框 + 角标对勾 */
@Composable
private fun AppIconOption(
    iconRes: Int,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(SquircleShape(18.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine,
                    shape = SquircleShape(18.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(SquircleShape(14.dp))
            )
            if (isSelected) {
                // 右上角选中角标（对勾 + 主题色圆底）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = AppStrings.get(R.string.content_desc_selected),
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MiuixTheme.textStyles.footnote1,
            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return "#%02X%02X%02X".format(red, green, blue)
}

private fun hexToColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    return Color(
        red = clean.substring(0, 2).toInt(16) / 255f,
        green = clean.substring(2, 4).toInt(16) / 255f,
        blue = clean.substring(4, 6).toInt(16) / 255f
    )
}
