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

package com.yunx.app.data.update

import java.io.InputStream
import java.security.MessageDigest

/**
 * 更新包完整性校验。
 *
 * GitHub 只在较新的 Release 响应里给 asset 的 `digest`（形如 `sha256:...`），
 * 老仓库 / 预览构造的 Release 没有这个字段。因此口径固定为「有 digest 才校验，
 * 没有就跳过」，绝不能因为拿不到摘要而把正常下载判成失败。
 */
object UpdatePackageDigest {

    private val SHA256_HEX = Regex("^[a-fA-F0-9]{64}$")
    private const val SHA256_PREFIX = "sha256:"

    /** 校验结论：Skipped 表示没有可用的 SHA-256 摘要，或调用方拿不到文件内容 */
    sealed interface Verdict {
        data object Skipped : Verdict
        data object Matched : Verdict
        data class Mismatched(val expected: String, val actual: String) : Verdict
    }

    /** 从 asset 的 digest 原文取 SHA-256 十六进制；不是 sha256 或格式不对返回 null（跳过校验） */
    fun expectedSha256(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // GitHub 给的是 `sha256:<hex>`；也容忍直接给 64 位十六进制的写法
        val value = if (trimmed.startsWith(SHA256_PREFIX, ignoreCase = true)) {
            trimmed.substring(SHA256_PREFIX.length).trim()
        } else {
            trimmed
        }
        return value.takeIf { SHA256_HEX.matches(it) }?.lowercase()
    }

    /** 流式计算 SHA-256，不把整个安装包读进内存 */
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /** 比对摘要；摘要缺失或流缺失都按 Skipped 放行 */
    fun verify(input: InputStream?, digest: String): Verdict {
        val expected = expectedSha256(digest) ?: return Verdict.Skipped
        if (input == null) return Verdict.Skipped
        val actual = input.use { sha256(it) }
        return if (actual == expected) {
            Verdict.Matched
        } else {
            Verdict.Mismatched(expected = expected, actual = actual)
        }
    }
}
