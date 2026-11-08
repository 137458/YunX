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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.data.network.model.ShareInfo
import com.yunx.app.data.upload.UploadPhase
import com.yunx.app.data.upload.UploadProgress
import com.yunx.app.R
import com.yunx.app.ui.AppSnackbarHost
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.theme.SquircleShape
import com.yunx.app.util.AppStrings
import com.yunx.app.util.ByteFormat
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 分享结果（WindowDialog）：链接 + 提取码 + 复制 */
@Composable
internal fun ShareResultDialog(
    info: ShareInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val platformName = when {
        info.shareUrl.contains("139.com") -> AppStrings.get(R.string.csa_platform_139)
        info.shareUrl.contains("123pan") || info.shareUrl.contains("123865") -> AppStrings.get(R.string.csa_platform_123)
        info.shareUrl.contains("uc.cn") -> AppStrings.get(R.string.csa_platform_uc)
        info.shareUrl.contains("xunlei.com") -> AppStrings.get(R.string.csa_platform_xunlei)
        info.shareUrl.contains("baidu.com") -> AppStrings.get(R.string.csa_platform_baidu)
        else -> AppStrings.get(R.string.csa_platform_quark)
    }
    val shareText = buildString {
        append(AppStrings.get(R.string.csa_share_text_platform, platformName, info.title))
        append(AppStrings.get(R.string.csa_share_text_link, info.shareUrl))
        if (info.passcode.isNotBlank()) {
            append(AppStrings.get(R.string.csa_share_passcode_line, info.passcode))
        }
    }
    WindowDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = AppStrings.get(R.string.csa_share_success)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(12.dp)
            ) {
                Text(
                    text = shareText,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    lineHeight = 22.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = AppStrings.get(R.string.csa_share_expiry_label) + expireLabel(info.expiredType),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = AppStrings.get(R.string.csa_copy_all),
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("share_text", shareText))
                        SnackbarController.show(AppStrings.get(R.string.csa_share_text_copied))
                    },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(AppStrings.get(R.string.csa_done), style = MiuixTheme.textStyles.button)
                }
            }
        }
        AppSnackbarHost(rememberGlobalSnackbarHostState())
    }
}

private fun expireLabel(type: Int): String = when (type) {
    2 -> AppStrings.get(R.string.csa_expire_1day)
    3 -> AppStrings.get(R.string.csa_expire_7days)
    4 -> AppStrings.get(R.string.csa_expire_30days)
    else -> AppStrings.get(R.string.csa_expire_forever)
}

/**
 * 「上传文件」的选文件器：封装系统多选文件对话框，选完把 uri 列表交给 [onPicked]。
 *
 * 用 `OpenMultipleDocuments` 而不是 `GetContent`：前者给的是可长期读取的 uri，
 * 一次选多个文件后逐个上传期间都不会出现「授权已失效」；后者是单次临时授权。
 */
@Composable
internal fun rememberUploadPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    return remember(launcher) {
        { launcher.launch(arrayOf("*/*")) }
    }
}

/**
 * 上传进度弹窗：第几个/共几个、文件名、字节进度与阶段，只留一个「取消」。
 *
 * 点外部不关闭、没有「确定」——上传是真在后台跑的，随手关掉弹窗会让人误以为停了。
 * 进度条用 miuix 表面色自绘（`LinearProgressIndicator` 的进度态在 miuix 里没有稳定 API，
 * 这里只画一条 4dp 的圆角进度轨，观感与设置页的进度条一致）。
 */
@Composable
internal fun UploadProgressDialog(progress: UploadProgress, onCancel: () -> Unit) {
    WindowDialog(
        show = true,
        // 上传在后台真跑，随手关弹窗会让人误以为停了：仅「取消」能结束
        onDismissRequest = { },
        title = if (progress.count > 1) {
            AppStrings.get(R.string.csa_uploading_progress, progress.index, progress.count)
        } else {
            AppStrings.get(R.string.csa_uploading)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = progress.name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 进度轨：底色 + 按 fraction 铺满的前景色
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(SquircleShape(2.dp))
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.fraction)
                        .height(4.dp)
                        .clip(SquircleShape(2.dp))
                        .background(MiuixTheme.colorScheme.primary)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (progress.phase) {
                        UploadPhase.PREPARING -> AppStrings.get(R.string.csa_upload_phase_preparing)
                        UploadPhase.UPLOADING -> AppStrings.get(R.string.csa_uploading)
                        UploadPhase.FINISHING -> AppStrings.get(R.string.csa_upload_phase_finishing)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = ByteFormat.size(progress.sent) + " / " + ByteFormat.size(progress.size),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onCancel,
                enabled = !progress.cancelling,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (progress.cancelling) AppStrings.get(R.string.csa_cancelling) else AppStrings.get(R.string.csa_cancel_upload),
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}
