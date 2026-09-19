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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.theme.AppThemeManager
import com.yunx.app.ui.theme.SquircleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 预置主题色（Material 风格种子色） */
private val presetColors = listOf(
    "蓝色" to 0xFF415F91L,
    "靛蓝" to 0xFF3F51B5L,
    "紫色" to 0xFF6750A4L,
    "玫红" to 0xFFC2185BL,
    "红色" to 0xFFB3261EL,
    "橙色" to 0xFFF4631CL,
    "金黄" to 0xFFF9A825L,
    "绿色" to 0xFF38761DL,
    "青色" to 0xFF00897BL,
    "天蓝" to 0xFF0288D1L,
)

/** 底部导航栏样式选项（顺序与 BottomBarStyle.ordinal 一一对应，不可调序） */
private val bottomBarStyleOptions = listOf("液态玻璃", "毛玻璃", "标准")

/** 各档位说明文案（与 bottomBarStyleOptions 同序） */
private val bottomBarStyleHints = listOf(
    "悬浮胶囊 + 透镜折射与色散光晕，随手机倾斜产生镜面高光；需 Android 13+，低版本自动降级为毛玻璃。",
    "悬浮胶囊 + 背景高斯模糊，观感接近液态玻璃但不含折射，兼容全部机型。",
    "Miuix 原生贴底导航栏，无背景采样与着色器开销，最省电。"
)

/**
 * 主题与外观设置页（Miuix 规范重构）：
 * - 外观模式：Squircle 胶囊单选（跟随系统 / 浅色 / 深色）
 * - 主题色：Miuix Card 分组，动态色彩开关（Android 12+）+ LazyRow 色圆选择 + 自定义调色盘（Miuix OverlayDialog + Slider）
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
                    title = "主题与外观",
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            ) {
            // ---------- 外观模式 ----------
            SmallTitle(text = "外观模式")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择应用的明暗外观",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf("跟随系统", "浅色", "深色")
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
            }

            // ---------- 主题色彩 ----------
            SmallTitle(text = "主题颜色")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 动态色彩开关（仅 Android 12+）
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "动态色彩",
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "从系统壁纸自动取色",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            Switch(
                                checked = AppThemeManager.colorMode == 0,
                                onCheckedChange = { on ->
                                    AppThemeManager.setColorMode(context, if (on) 0 else 1)
                                }
                            )
                        }
                    }

                    // 主题色选择（动态色彩开启时折叠）
                    AnimatedVisibility(visible = effectiveColorMode != 0) {
                        Column(modifier = Modifier.padding(top = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 16.dp else 0.dp)) {
                            Text(
                                text = "推荐颜色",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp)
                            ) {
                                itemsIndexed(presetColors) { _, (name, color) ->
                                    val isSelected = (effectiveColorMode == 1 && color == 0xFF415F91L) ||
                                        (effectiveColorMode == 2 && AppThemeManager.seedColor == color)
                                    ColorSelectionItem(
                                        color = color,
                                        name = name,
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
            }

            // ---------- 底部导航栏 ----------
            SmallTitle(text = "底部导航栏")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择底部导航栏样式",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bottomBarStyleOptions.forEachIndexed { index, label ->
                            SmoothFilterChip(
                                selected = AppThemeManager.bottomBarStyle == index,
                                label = label,
                                onClick = { AppThemeManager.setBottomBarStyle(context, index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = bottomBarStyleHints.getOrElse(AppThemeManager.bottomBarStyle) { "" },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            // ---------- 桌面图标 ----------
            SmallTitle(text = "桌面图标")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        AppIconOption(
                            iconRes = R.drawable.icon,
                            name = "经典图标",
                            isSelected = appIconVariant == 0,
                            onClick = {
                                appIconVariant = 0
                                switchAppIcon(0)
                            }
                        )
                        AppIconOption(
                            iconRes = R.drawable.icon2,
                            name = "新图标",
                            isSelected = appIconVariant == 1,
                            onClick = {
                                appIconVariant = 1
                                switchAppIcon(1)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Android 12+ 立即生效；部分设备需回到桌面或重启启动器后查看。",
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
        targetValue = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant,
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
        modifier = modifier.height(38.dp),
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

/** 预置色圆点（Squircle 形状 + 名称，选中高亮并显示对勾） */
@Composable
private fun ColorSelectionItem(
    color: Long,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
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
                    tint = Color.White,
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
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SquircleShape(14.dp))
                .background(
                    if (isSelected) Color(customColor.toInt())
                    else MiuixTheme.colorScheme.surfaceVariant
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
                tint = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "自定义",
            style = MiuixTheme.textStyles.footnote1,
            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

// ================= 调色盘 Dialog（Miuix OverlayDialog + Miuix ColorPicker） =================

@Composable
private fun ColorPickerDialog(
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
        title = "自定义主题色",
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
                        .background(MiuixTheme.colorScheme.surfaceVariant)
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
                    text = "取消",
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onColorSelected(currentColor.toArgb().toLong() and 0xFFFFFFFFL) }
                ) {
                    Text("应用")
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        contentDescription = "已选择",
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