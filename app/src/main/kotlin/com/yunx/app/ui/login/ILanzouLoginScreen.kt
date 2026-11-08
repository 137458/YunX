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

package com.yunx.app.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.R
import com.yunx.app.util.AppStrings
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.viewmodel.ILanzouAccountViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 蓝奏云优享版登录页（账号密码登录，文档 §2.2）：获取设备标识 uuid → /unproved/login 换 appToken。
 */
@Composable
fun ILanzouLoginScreen(
    viewModel: ILanzouAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var account by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTutorial by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showTutorial = true }

    BackHandler { if (!isSaving) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = AppStrings.get(R.string.lg_ilanzou_title),
                navigationIcon = {
                    IconButton(onClick = { if (!isSaving) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppStrings.get(R.string.lg_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showTutorial = true }, enabled = !isSaving) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = AppStrings.get(R.string.lg_login_tutorial),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )
                    }
                },
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = AppStrings.get(R.string.lg_ilanzou_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )

            TextField(
                value = account,
                onValueChange = {
                    account = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = AppStrings.get(R.string.lg_username),
                useLabelAsPlaceholder = true,
                singleLine = true,
                enabled = !isSaving
            )

            TextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = AppStrings.get(R.string.lg_password),
                useLabelAsPlaceholder = true,
                singleLine = true,
                enabled = !isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) AppStrings.get(R.string.lg_hide_password) else AppStrings.get(R.string.lg_show_password)
                        )
                    }
                }
            )

            error?.let {
                Text(
                    text = it,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        error = null
                        val (ok, msg) = viewModel.login(account.trim(), password)
                        isSaving = false
                        if (ok) {
                            SnackbarController.show(AppStrings.get(R.string.lg_login_success))
                            onSaved()
                        } else {
                            error = msg
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = account.isNotBlank() && password.isNotBlank() && !isSaving,
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                if (isSaving) {
                    LoginLoadingIndicator(text = AppStrings.get(R.string.lg_logging_in))
                } else {
                    Text(AppStrings.get(R.string.lg_login), style = MiuixTheme.textStyles.button)
                }
            }
        }
    }

    OverlayDialog(
        show = showTutorial,
        title = AppStrings.get(R.string.lg_login_instructions),
        onDismissRequest = { showTutorial = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(AppStrings.get(R.string.lg_ilanzou_tutorial_1), style = MiuixTheme.textStyles.body2)
            Text(
                AppStrings.get(R.string.lg_ilanzou_tutorial_2),
                style = MiuixTheme.textStyles.body2
            )
            Text(
                AppStrings.get(R.string.lg_ilanzou_tutorial_3),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                text = AppStrings.get(R.string.lg_got_it),
                onClick = { showTutorial = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
