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

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.util.PermissionState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 引导页「权限准备」区块：全项目的权限申请统一收在这里（首次启动一次性过一遍），
 * 各功能内部只留"兜底"：通知权限（可选）、后台运行/忽略电池优化（可选）、存储权限（Android 9- 为**必要权限**）。
 * 通知与后台运行可以跳过；存储权限拿不到时不给跳过 —— Android 9 及以下未授权时，底部「开始使用」
 * 按钮会置灰禁用（文案不变），授权入口就是存储权限卡片；授权成功返回后按钮自动恢复可点。
 *
 * ★ 存储权限的状态（storageGranted / storageDenied / onRequestStorage）提升在 [OnboardingScreen]：
 *   它同时要控制底部按钮的可用性，不能在本区块里自生自灭。
 *
 * ★ 状态必须在 `ON_RESUME` 时重查：授权框 / 系统设置页都会让 Activity 走一次
 *   onPause → onResume，只靠 `remember` 初值会出现"授权完返回，卡片还显示未授权"。
 */
@Composable
internal fun PermissionSection(
    storageGranted: Boolean,
    storageDenied: Boolean,
    onRequestStorage: () -> Unit
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(PermissionState.notificationsEnabled(context)) }
    var batteryIgnored by remember { mutableStateOf(PermissionState.batteryOptimizationIgnored(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = PermissionState.notificationsEnabled(context)
                batteryIgnored = PermissionState.batteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 通知：13+ 才有可申请的运行时权限，其余情况跳系统设置页（存储权限的发起在 OnboardingScreen）
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = PermissionState.notificationsEnabled(context)
    }

    val storageRequired = PermissionState.storagePermissionRequired()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = AppStrings.get(R.string.rs_permissions_title),
            style = MiuixTheme.textStyles.headline2,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (storageRequired) {
                AppStrings.get(R.string.rs_permissions_intro_required)
            } else {
                AppStrings.get(R.string.rs_permissions_intro_optional)
            },
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            icon = Icons.Outlined.Notifications,
            title = AppStrings.get(R.string.rs_perm_notification_title),
            desc = AppStrings.get(R.string.rs_perm_notification_desc),
            granted = notificationsEnabled,
            grantedText = AppStrings.get(R.string.em_log_enabled),
            actionText = if (PermissionState.canRequestNotifications(context)) AppStrings.get(R.string.rs_perm_grant) else AppStrings.get(R.string.rs_perm_go_settings),
            onAction = {
                if (PermissionState.canRequestNotifications(context)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // 13+ 已授权但被系统/用户关闭，或低版本通知总开关被关：只能去系统设置开
                    PermissionState.openNotificationSettings(context)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = Icons.Outlined.BatteryChargingFull,
            title = AppStrings.get(R.string.rs_perm_background_title),
            desc = AppStrings.get(R.string.rs_perm_background_desc),
            granted = batteryIgnored,
            grantedText = AppStrings.get(R.string.rs_perm_whitelisted),
            actionText = AppStrings.get(R.string.rs_perm_go_settings),
            onAction = { PermissionState.requestIgnoreBatteryOptimizations(context) }
        )
        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = Icons.Outlined.Storage,
            title = AppStrings.get(R.string.rs_perm_storage_title),
            desc = if (storageRequired) {
                AppStrings.get(R.string.rs_perm_storage_desc_required)
            } else {
                AppStrings.get(R.string.rs_perm_storage_desc_optional)
            },
            granted = storageGranted,
            grantedText = if (storageRequired) AppStrings.get(R.string.rs_perm_authorized) else AppStrings.get(R.string.rs_perm_no_need),
            actionText = if (storageRequired && !storageGranted) {
                // 授权框已被拒过一次：再点也弹不出来（或被勾了"不再询问"），只能去应用详情页手动开
                if (storageDenied) AppStrings.get(R.string.rs_perm_go_settings_grant) else AppStrings.get(R.string.rs_perm_grant)
            } else {
                null
            },
            onAction = onRequestStorage
        )

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = if (storageRequired) {
                AppStrings.get(R.string.rs_perm_skip_impact_required)
            } else {
                AppStrings.get(R.string.rs_perm_skip_impact_optional)
            },
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            lineHeight = 20.sp
        )
    }
}

/** 权限卡片：图标 + 标题 +（已授权徽标 / 操作按钮）+ 说明 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    grantedText: String,
    actionText: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (granted) {
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = grantedText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = desc,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                lineHeight = 20.sp
            )
            // ★ 已授权就不显示操作按钮：「去设置」只在真的需要用户动手时才出现
            if (!granted && actionText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    text = actionText,
                    onClick = onAction
                )
            }
        }
    }
}
