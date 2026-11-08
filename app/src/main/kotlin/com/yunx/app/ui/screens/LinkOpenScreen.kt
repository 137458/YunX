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
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.R
import com.yunx.app.data.prefs.LinkOpenTarget
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.rememberBlurBackdrop
import com.yunx.app.util.AppStrings
import com.yunx.app.util.LinkOpener
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「链接唤起」（深链接注册项）设置页。
 *
 * Miuix 规范重构：Scaffold、TopAppBar、Card、SmallTitle、SwitchPreference 分组展示。
 * 每行对应清单里一个 activity-alias：关掉就是把那个 alias 置为 DISABLED，
 * 系统随即不再把那种链接派给云析（详见 [LinkOpenTarget] / [LinkOpener]）。
 *
 * ★ 页面只是开关的「用户意愿」，真正生效的组件状态由 [LinkOpener.apply] 立刻同步 —— 不写回就不会生效，
 *   所以这里每次切换都同时做三件事：改本地状态、写 prefs、同步组件。
 */
@Composable
fun LinkOpenScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    // 进页面读一次：之后以本地状态为准（写回 prefs + 同步组件）
    var states by remember {
        mutableStateOf(LinkOpenTarget.entries.associateWith { settingsRepo.linkOpen(it) })
    }
    val enabledCount = states.count { it.value }

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
                    title = stringResource(R.string.vm_link_open_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .blurBackdropSource(backdrop)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
        ) {
            SmallTitle(text = stringResource(R.string.vm_link_open_features))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.vm_link_open_intro),
                        style = MiuixTheme.textStyles.body1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 说清楚「已验证的链接为什么永远是 0」：验证要在对方服务器放校验文件，第三方 App 做不到
                    Text(
                        text = stringResource(R.string.vm_link_open_verified_zero),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vm_link_open_manual_associate),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.vm_link_open_disable_hint),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 20.sp
                    )
                }
            }

            SmallTitle(text = stringResource(R.string.vm_link_open_shortcuts))
            // 一键跳到系统的关联页：免选择器的唯一落地方式（域名验证做不到，只能手动关联）
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                ArrowPreference(
                    title = stringResource(R.string.vm_link_open_open_settings),
                    summary = stringResource(R.string.vm_link_open_settings_desc),
                    onClick = { openLinkHandlingSettings(context) }
                )
            }

            SmallTitle(
                text = stringResource(
                    R.string.vm_link_open_enabled_count,
                    enabledCount,
                    LinkOpenTarget.entries.size
                )
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
            ) {
                LinkOpenTarget.entries.forEach { target ->
                    val label = stringResource(target.labelRes)
                    val enabledToast = stringResource(R.string.vm_link_open_enabled_toast, label)
                    val disabledToast = stringResource(R.string.vm_link_open_disabled_toast, label)
                    SwitchPreference(
                        title = label,
                        summary = if (target.sampleRes != 0) {
                            stringResource(target.sampleRes)
                        } else {
                            target.sample
                        },
                        checked = states[target] == true,
                        onCheckedChange = { on ->
                            states = states + (target to on)
                            settingsRepo.setLinkOpen(target, on)
                            LinkOpener.apply(context, target, on)
                            SnackbarController.show(if (on) enabledToast else disabledToast)
                        }
                    )
                }
            }
        }
    }
}

/**
 * 打开系统的「默认打开 / 打开支持的链接」页 —— 用户在那里把网盘域名手动关联给云析。
 *
 * 这是免选择器直开的**唯一**落地方式：域名验证要求每个网盘域名自己托管 assetlinks.json，
 * 第三方 App 做不到（官方文档「在您的应用中打开您的应用无法验证的网域」一节给的就是手动关联）。
 * `ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` 是 API 31+，低版本没有这个页面，退到应用详情页。
 */
private fun openLinkHandlingSettings(context: android.content.Context) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkgUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        SnackbarController.show(AppStrings.get(R.string.vm_link_open_settings_failed))
    }
}
