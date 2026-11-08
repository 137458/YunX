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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.components.BlurredBar
import com.yunx.app.ui.components.blurBackdropSource
import com.yunx.app.ui.components.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「多账号下载」设置子页：两个**实验性**开关。
 *
 * - C1 多账号下载：批量下载时每个文件轮换用不同账号取链，绕开单账号的并发/限速；
 * - C2 单文件多源分片：同一个文件用多个账号各取一条直链、分片轮流交给不同直链（依赖 C1）。
 *
 * ★ 两个开关都改的是「取链/下载策略」，异常时只会表现为风控（验证码、限流），用户很难自己
 *   反推是哪一项造成的，所以开启前一律先弹二次风险确认；关 C1 时顺带强制关 C2（见 [SettingsRepository.disableMultiSource]）。
 * ★ 这里每次切换都做两件事：改本地状态（驱动 UI）、写 prefs（真正生效的口径），两者必须同步。
 */
@Composable
fun MultiAccountDownloadScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    // 进页面读一次：之后以本地状态为准（同时写回 prefs）
    var multiAccount by remember { mutableStateOf(settingsRepo.multiAccountDownload) }
    var multiSource by remember { mutableStateOf(settingsRepo.multiSourceDownload) }
    // 非 null = 正在二次确认哪一项（只有「开启」才需要确认，关闭直接生效）
    var pendingRisk by remember { mutableStateOf<SwitchRisk?>(null) }

    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()

    BackHandler { onBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior
            ) {
                TopAppBar(
                    title = AppStrings.get(R.string.dl_ma_title),
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) androidx.compose.ui.graphics.Color.Transparent else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.get(R.string.csa_back))
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
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurBackdropSource(backdrop)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp)
        ) {
            SmallTitle(text = AppStrings.get(R.string.vm_link_open_features))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = AppStrings.get(R.string.dl_ma_intro),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppStrings.get(R.string.dl_ma_desc1),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppStrings.get(R.string.dl_ma_desc2),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = AppStrings.get(R.string.dl_ma_desc3),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SmallTitle(text = AppStrings.get(R.string.dl_strategy_section))
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                SwitchPreference(
                    title = AppStrings.get(R.string.dl_ma_title),
                    summary = AppStrings.get(R.string.dl_ma_subtitle),
                    checked = multiAccount,
                    onCheckedChange = { on ->
                        if (on) {
                            // 开启前二次风险确认：取消时什么都没写，状态不变
                            pendingRisk = SwitchRisk.MULTI_ACCOUNT
                        } else {
                            val multiSourceWasOn = multiSource
                            multiAccount = false
                            settingsRepo.multiAccountDownload = false
                            // ★ C2 依赖 C1：关 C1 必须同时关 C2（prefs + 本地状态），
                            //   否则会留下「多账号下载关了、单文件还在多账号取链」的不一致状态
                            settingsRepo.disableMultiSource()
                            multiSource = false
                            SnackbarController.show(
                                if (multiSourceWasOn) {
                                    AppStrings.get(R.string.dl_ma_off_both)
                                } else {
                                    AppStrings.get(R.string.dl_ma_off)
                                }
                            )
                        }
                    }
                )

                SwitchPreference(
                    title = AppStrings.get(R.string.dl_ms_title),
                    summary = if (multiAccount) {
                        AppStrings.get(R.string.dl_ms_subtitle)
                    } else {
                        // C2 依赖 C1：C1 关闭时这一项不可操作，副标题直接说明原因
                        AppStrings.get(R.string.dl_ms_needs_ma)
                    },
                    checked = multiSource,
                    enabled = multiAccount,
                    onCheckedChange = { on ->
                        if (on) {
                            pendingRisk = SwitchRisk.MULTI_SOURCE
                        } else {
                            multiSource = false
                            settingsRepo.multiSourceDownload = false
                            SnackbarController.show(AppStrings.get(R.string.dl_ms_off))
                        }
                    }
                )
            }
        }
    }

    val currentRisk = pendingRisk
    if (currentRisk != null) {
        OverlayDialog(
            show = true,
            title = currentRisk.title,
            onDismissRequest = { pendingRisk = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = currentRisk.message,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(text = AppStrings.get(R.string.csa_cancel), onClick = { pendingRisk = null })
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (currentRisk) {
                                SwitchRisk.MULTI_ACCOUNT -> {
                                    multiAccount = true
                                    settingsRepo.multiAccountDownload = true
                                    SnackbarController.show(AppStrings.get(R.string.dl_ma_on))
                                }
                                SwitchRisk.MULTI_SOURCE -> {
                                    multiSource = true
                                    settingsRepo.multiSourceDownload = true
                                    SnackbarController.show(AppStrings.get(R.string.dl_ms_on))
                                }
                            }
                            pendingRisk = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.error,
                            contentColor = MiuixTheme.colorScheme.onError
                        )
                    ) {
                        Text(AppStrings.get(R.string.dl_confirm_enable), style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}

/**
 * 需要二次风险确认的两个开关（确认弹窗的标题与正文按它取，见 [SwitchRisk.message]）。
 *
 * 用枚举而不是两个布尔状态：弹窗同一时刻只会有一个，语义上不会出现「两个都待确认」的非法态。
 */
private enum class SwitchRisk(val title: String, val message: String) {
    // 参数顺序 = (title, message)
    MULTI_ACCOUNT(
        AppStrings.get(R.string.dl_ma_confirm_title),
        AppStrings.get(R.string.dl_ma_confirm_message)
    ),
    MULTI_SOURCE(
        AppStrings.get(R.string.dl_ms_confirm_title),
        AppStrings.get(R.string.dl_ms_confirm_message)
    )
}
