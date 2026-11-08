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

import com.yunx.app.R
import com.yunx.app.util.AppStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [cloudNameError] 的规则测试。
 *
 * 这条规则原先只存在于夸克专用的重命名步骤里，其他平台的重命名弹窗没有校验；
 * 迁移到共享弹窗后，规则一旦走样，用户会把含 `/` 的名称提交给网盘而不是在本地被拦下。
 */
class CloudNameErrorTest {
    /** 校验函数走 AppStrings，单测用中文模板替身还原真实取值 */
    @Before
    fun installStringResolver() {
        val templates = mapOf(
            R.string.csa_name_error_empty to "名称不能为空",
            R.string.csa_name_error_dots to "名称不能是 . 或 ..",
            R.string.csa_name_error_too_long to "名称最多 255 个字符",
            R.string.csa_name_error_invalid_chars to "名称不能含 / \\ 或控制字符",
        )
        AppStrings.installResolverForTest { resId, _ -> templates[resId] ?: resId.toString() }
    }

    @After
    fun clearStringResolver() {
        AppStrings.installResolverForTest(null)
    }

    @Test
    fun blankNameIsRejected() {
        assertEquals("名称不能为空", cloudNameError(""))
        assertEquals("名称不能为空", cloudNameError("   "))
    }

    @Test
    fun dotNamesAreRejected() {
        assertEquals("名称不能是 . 或 ..", cloudNameError("."))
        assertEquals("名称不能是 . 或 ..", cloudNameError(".."))
    }

    @Test
    fun overlongNameIsRejected() {
        assertEquals("名称最多 255 个字符", cloudNameError("a".repeat(256)))
        assertNull(cloudNameError("a".repeat(255)))
    }

    @Test
    fun pathSeparatorsAndControlCharsAreRejected() {
        assertEquals("名称不能含 / \\ 或控制字符", cloudNameError("a/b"))
        assertEquals("名称不能含 / \\ 或控制字符", cloudNameError("a\\b"))
        assertEquals("名称不能含 / \\ 或控制字符", cloudNameError("a\u0001b"))
    }

    @Test
    fun ordinaryNamesAreAccepted() {
        assertNull(cloudNameError("正常文件名.txt"))
        assertNull(cloudNameError("  trimmed  "))
    }
}
