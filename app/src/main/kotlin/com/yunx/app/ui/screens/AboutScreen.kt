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

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.R
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.rememberBlurBackdrop
import com.yunx.app.ui.items.logoRes
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppLinks
import com.yunx.app.util.AppStrings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于云析页：应用介绍、支持平台、功能特性、技术栈与免责声明。
 * Miuix 规范重构：Scaffold、TopAppBar、Card、SmallTitle、ArrowPreference 分组展示。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onPreviewOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onBack() }
    val pkgInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: "1.0"
    // versionCode 字段已弃用：compat 取 Long 后 toInt() 收窄，仅用于展示，保持 AppHeader 的 Int 签名不变
    val versionCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it).toInt() } ?: 1

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
                    title = AppStrings.get(R.string.vm_about_title),
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
            // ---------- App 头部 ----------
            AppHeader(versionName = versionName, versionCode = versionCode)
            Spacer(modifier = Modifier.height(16.dp))

            // ---------- 简介 ----------
            SmallTitle(text = AppStrings.get(R.string.vm_about_intro_title))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CardIcon(Icons.Outlined.Cloud)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = AppStrings.get(R.string.vm_about_app_name),
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppStrings.get(R.string.vm_about_intro),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // ---------- 支持平台 ----------
            SmallTitle(text = AppStrings.get(R.string.vm_about_supported_platforms))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                PlatformContent()
            }

            // ---------- 功能特性 ----------
            SmallTitle(text = AppStrings.get(R.string.vm_about_features))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                FeatureContent()
            }

            // ---------- 技术栈 ----------
            SmallTitle(text = AppStrings.get(R.string.vm_about_tech_stack))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                TechContent()
            }

            // ---------- 相关操作 ----------
            SmallTitle(text = AppStrings.get(R.string.vm_link_open_shortcuts))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                ArrowPreference(
                    title = AppStrings.get(R.string.vm_about_preview_onboarding_title),
                    summary = AppStrings.get(R.string.vm_about_preview_onboarding_desc),
                    onClick = onPreviewOnboarding
                )
                ArrowPreference(
                    title = AppStrings.get(R.string.rs_open_source_repo),
                    summary = AppLinks.GITHUB_REPO_DISPLAY,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_REPO))
                        context.startActivity(intent)
                    }
                )
            }

            // ---------- 免责声明 ----------
            SmallTitle(text = AppStrings.get(R.string.rs_disclaimer_title))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CardIcon(Icons.Outlined.Shield)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = AppStrings.get(R.string.vm_about_terms_title),
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = AppStrings.get(R.string.vm_about_disclaimer_body),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // ---------- 开源协议与页脚 ----------
            Text(
                text = AppStrings.get(R.string.vm_about_license),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = AppStrings.get(R.string.vm_about_version, versionName),
                style = MiuixTheme.textStyles.footnote1,
                // dividerLine 是分隔线专用色，深色下几乎不可见，正文必须用次级内容色
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Center
            )
        }
        }
    }
}

/** App 头部：渐变图标 + 应用名 + 版本 + 标语 */
@Composable
private fun AppHeader(versionName: String, versionCode: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MiuixTheme.colorScheme.primary,
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        )
                    ),
                    shape = SquircleShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.icon),
                contentDescription = AppStrings.get(R.string.rs_app_icon_desc),
                modifier = Modifier
                    .size(88.dp)
                    .clip(SquircleShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = AppStrings.get(R.string.rs_app_name),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = AppStrings.get(
                R.string.vm_about_version_name,
                versionName,
                versionCode
            ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = AppStrings.get(R.string.vm_about_tagline),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

/** 平台胶囊条目：各平台官方 logo 资源 + 展示名（数据来自 SharePlatform / VideoPlatform） */
private data class PlatformChip(@DrawableRes val logoRes: Int, val name: String)

/** 支持平台内容：网盘来自 SharePlatform（GitHub 不算网盘），视频解析来自 VideoPlatform */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformContent() {
    val drives = SharePlatform.entries
        .filter { it != SharePlatform.GITHUB }
        .map { PlatformChip(it.logoRes(), it.displayName) }
    val videos = VideoPlatform.entries.map { PlatformChip(it.logoRes(), it.displayName) }
    Column(modifier = Modifier.padding(16.dp)) {
        PlatformChipGroup(label = AppStrings.get(R.string.rs_platform_generic), chips = drives)
        Spacer(modifier = Modifier.height(12.dp))
        PlatformChipGroup(label = AppStrings.get(R.string.csb_drive_section_video), chips = videos)
    }
}

/** 平台分组：分组小标题 + 一组 Miuix 胶囊（官方图标按自身形状原样画，不裁圆、不垫底色） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformChipGroup(label: String, chips: List<PlatformChip>) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            Surface(
                shape = SquircleShape(10.dp),
                color = MiuixTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(chip.logoRes),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = chip.name,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/** 功能特性内容 */
@Composable
private fun FeatureContent() {
    val features = listOf(
        AppStrings.get(R.string.vm_about_feature_netdisk_title) to AppStrings.get(R.string.vm_about_feature_netdisk_desc),
        AppStrings.get(R.string.vm_about_feature_video_title) to AppStrings.get(R.string.vm_about_feature_video_desc),
        AppStrings.get(R.string.vm_about_feature_preview_title) to AppStrings.get(R.string.vm_about_feature_preview_desc),
        AppStrings.get(R.string.vm_about_feature_mux_title) to AppStrings.get(R.string.vm_about_feature_mux_desc),
        AppStrings.get(R.string.vm_about_feature_download_title) to AppStrings.get(R.string.vm_about_feature_download_desc),
        AppStrings.get(R.string.vm_about_feature_cleanup_title) to AppStrings.get(R.string.vm_about_feature_cleanup_desc),
        AppStrings.get(R.string.vm_about_feature_credentials_title) to AppStrings.get(R.string.vm_about_feature_credentials_desc)
    )
    Column(modifier = Modifier.padding(16.dp)) {
        features.forEachIndexed { index, (title, desc) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(6.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = desc,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            if (index < features.lastIndex) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/** 技术栈内容 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechContent() {
    val techs = listOf(
        "Kotlin", "Jetpack Compose", "Miuix", "Room", "OkHttp", "KSP",
        "Media3 ExoPlayer", "Coil 3"
    )
    Column(modifier = Modifier.padding(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            techs.forEach { name ->
                Surface(
                    shape = SquircleShape(8.dp),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

/** 卡片图标圆形底 */
@Composable
private fun CardIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = SquircleShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.primary
            )
        }
    }
}
