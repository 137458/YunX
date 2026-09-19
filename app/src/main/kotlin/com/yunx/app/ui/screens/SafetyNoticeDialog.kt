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
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunx.app.util.TextCipher
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SafetyNoticeDialog() {
    val context = LocalContext.current
    val lockSeconds = 2
    var visible by remember { mutableStateOf(!isAcknowledged(context)) }
    var seconds by remember { mutableIntStateOf(lockSeconds) }

    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
    }

    if (!visible) return

    WindowDialog(
        show = visible,
        onDismissRequest = {
            if (seconds <= 0) {
                visible = false
                acknowledge(context)
            }
        },
        title = TextCipher.dTitle,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                top.yukonga.miuix.kmp.basic.Surface(
                    shape = com.yunx.app.ui.theme.SquircleShape(6.dp),
                    color = MiuixTheme.colorScheme.secondaryVariant
                ) {
                    Text(
                        text = TextCipher.dOfficial,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSecondaryVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = TextCipher.dBody,
                style = MiuixTheme.textStyles.body1,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            top.yukonga.miuix.kmp.basic.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = com.yunx.app.ui.theme.SquircleShape(10.dp),
                color = MiuixTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    text = TextCipher.dUrl,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = TextCipher.dCopy,
                    onClick = { copyUrl(context) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        visible = false
                        acknowledge(context)
                    },
                    enabled = seconds <= 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        text = if (seconds > 0) {
                            TextCipher.dCountdown.replace("%d", seconds.toString())
                        } else {
                            TextCipher.dBtn
                        },
                        style = MiuixTheme.textStyles.button
                    )
                }
            }
        }
    }
}

private fun isAcknowledged(context: Context): Boolean {
    return runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .getBoolean(TextCipher.pNoticeFlag, false)
    }.getOrDefault(false)
}

private fun acknowledge(context: Context) {
    runCatching {
        context.getSharedPreferences("yunx_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(TextCipher.pNoticeFlag, true)
            .apply()
    }
}

private fun copyUrl(context: Context) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(TextCipher.dUrl, TextCipher.dUrl))
        Toast.makeText(context, TextCipher.dCopied, Toast.LENGTH_SHORT).show()
    }
}