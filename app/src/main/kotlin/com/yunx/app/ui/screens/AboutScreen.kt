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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
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
import com.yunx.app.R
import com.yunx.app.ui.theme.SquircleShape
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
    val versionCode = pkgInfo?.versionCode ?: 1

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
                            text = "云析是一款网盘分享链接解析与高速下载工具。" +
                                "粘贴分享链接，登录网盘账号后即可浏览分享内容并直接高速下载文件。",
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
                    summary = "github.com/CYQawa/YunX",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/CYQawa/YunX"))
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
                            MiuixTheme.colorScheme.surfaceVariant
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
            text = "网盘链接解析与高速下载",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

/** 支持平台内容 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlatformContent() {
    val platforms = listOf(
        "夸克网盘" to Icons.Outlined.Cloud,
        "UC 网盘" to Icons.Outlined.Storage,
        "迅雷网盘" to Icons.Outlined.Speed,
        "百度网盘" to Icons.Outlined.Link,
        "139 网盘" to Icons.Outlined.Cloud,
        "123云盘" to Icons.Outlined.Cloud
    )
    Column(modifier = Modifier.padding(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            platforms.forEach { (name, icon) ->
                Surface(
                    shape = SquircleShape(10.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MiuixTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = name,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** 功能特性内容 */
@Composable
private fun FeatureContent() {
    val features = listOf(
        "一键解析分享链接" to "夸克 / UC / 迅雷 / 百度 / 139 / 123 分享直链识别",
        "高速分片下载" to "多线程并发 + 断点续传，充分利用带宽",
        "取链即删" to "转存后立即清理，不留残留",
        "凭证本地化" to "Cookie 加密落库，仅存本机"
    )
    Column(modifier = Modifier.padding(16.dp)) {
        features.forEachIndexed { index, (title, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
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
    val techs = listOf("Kotlin", "Jetpack Compose", "Miuix", "Room", "OkHttp", "KSP")
    Column(modifier = Modifier.padding(16.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            techs.forEach { name ->
                Surface(
                    shape = SquircleShape(8.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
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
        color = MiuixTheme.colorScheme.surfaceVariant
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