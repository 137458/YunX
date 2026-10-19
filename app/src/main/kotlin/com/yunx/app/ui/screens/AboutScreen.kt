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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.yunx.app.R
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.network.video.VideoPlatform
import com.yunx.app.ui.items.logoRes
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppLinks
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
                    title = "关于云析",
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
            // ---------- App 头部 ----------
            AppHeader(versionName = versionName, versionCode = versionCode)
            Spacer(modifier = Modifier.height(16.dp))

            // ---------- 简介 ----------
            SmallTitle(text = "应用简介")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CardIcon(Icons.Outlined.Cloud)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "云析（YunX）",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "云析（YunX）是一款网盘 / 视频分享链接解析与高速下载工具。" +
                                "粘贴链接即可：网盘分享能浏览内容并高速下载，视频链接能解析出直链与原图，" +
                                "图片 / 视频 / 音频都支持在线预览播放。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // ---------- 支持平台 ----------
            SmallTitle(text = "支持平台")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                PlatformContent()
            }

            // ---------- 功能特性 ----------
            SmallTitle(text = "功能特性")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                FeatureContent()
            }

            // ---------- 技术栈 ----------
            SmallTitle(text = "技术栈")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                TechContent()
            }

            // ---------- 相关操作 ----------
            SmallTitle(text = "快捷入口")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                ArrowPreference(
                    title = "重新预览欢迎界面",
                    summary = "重新展示首次启动引导页",
                    onClick = onPreviewOnboarding
                )
                ArrowPreference(
                    title = "开源仓库",
                    summary = AppLinks.GITHUB_REPO_DISPLAY,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_REPO))
                        context.startActivity(intent)
                    }
                )
            }

            // ---------- 免责声明 ----------
            SmallTitle(text = "免责声明")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CardIcon(Icons.Outlined.Shield)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "使用条款与声明",
                            style = MiuixTheme.textStyles.subtitle,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "本应用仅供个人学习与技术交流使用，请勿用于任何商业用途。" +
                                "下载内容版权归原作者所有，请于下载后 24 小时内删除。" +
                                "使用本应用产生的任何后果由使用者自行承担。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // ---------- 开源协议与页脚 ----------
            Text(
                text = "本项目基于 GNU AGPL-3.0 协议开源",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "云析 v$versionName · Made with DeepSeek",
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
                contentDescription = "云析图标",
                modifier = Modifier
                    .size(88.dp)
                    .clip(SquircleShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "云析",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "YunX · v$versionName ($versionCode)",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "网盘 / 视频链接解析与高速下载",
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
        PlatformChipGroup(label = "网盘", chips = drives)
        Spacer(modifier = Modifier.height(12.dp))
        PlatformChipGroup(label = "视频解析", chips = videos)
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
        "网盘分享解析" to "夸克 / UC / 迅雷 / 百度 / 139 / 123 / 115 / 光鸭 / 蓝奏云 / 蓝奏云优享版 直链识别",
        "视频链接解析" to "抖音 / 小红书 / 快手 / 微博 / AcFun 免登录；哔哩哔哩登录后画质更高，" +
            "Pixiv 未登录只能解析部分公开作品、粘贴浏览器 Cookie 后可解析全部作品",
        "在线预览与播放" to "图片 / 视频 / 音频在线看（自动带请求头），网盘里的文本文件也能打开",
        "音视频合轨" to "哔哩哔哩高清是音视频分轨的，应用内部合轨，不需要额外装 ffmpeg",
        "高速分片下载" to "多线程并发 + 断点续传，充分利用带宽",
        "取链即删" to "转存后立即清理，不留残留",
        "凭证本地化" to "Cookie 加密落库，仅存本机"
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