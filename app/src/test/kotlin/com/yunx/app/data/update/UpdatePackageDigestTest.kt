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

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePackageDigestTest {

    private val helloDigest = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    private val hello = "hello".toByteArray()

    @Test
    fun extractsSha256FromAssetDigestField() {
        assertEquals(helloDigest, UpdatePackageDigest.expectedSha256("sha256:$helloDigest"))
        assertEquals(helloDigest, UpdatePackageDigest.expectedSha256(" sha256:${helloDigest.uppercase()} "))
        assertEquals(helloDigest, UpdatePackageDigest.expectedSha256(helloDigest))
    }

    @Test
    fun rejectsNonSha256AndMalformedDigests() {
        assertEquals(null, UpdatePackageDigest.expectedSha256(""))
        assertEquals(null, UpdatePackageDigest.expectedSha256("sha512:$helloDigest"))
        assertEquals(null, UpdatePackageDigest.expectedSha256("sha256:not-a-hex-digest"))
        assertEquals(null, UpdatePackageDigest.expectedSha256("sha256:${helloDigest}00"))
    }

    @Test
    fun computesSha256WithoutLoadingWholeFile() {
        assertEquals(helloDigest, UpdatePackageDigest.sha256(hello.inputStream()))
    }

    @Test
    fun verifyReportsMatchAndMismatch() {
        assertEquals(
            UpdatePackageDigest.Verdict.Matched,
            UpdatePackageDigest.verify(hello.inputStream(), "sha256:$helloDigest"),
        )
        val mismatch = UpdatePackageDigest.verify(
            "tampered".toByteArray().inputStream(),
            "sha256:$helloDigest",
        )
        assertEquals(
            UpdatePackageDigest.Verdict.Mismatched(expected = helloDigest, actual = UpdatePackageDigest.sha256("tampered".toByteArray().inputStream())),
            mismatch,
        )
    }

    @Test
    fun verifySkipsWhenDigestOrStreamMissing() {
        assertEquals(
            UpdatePackageDigest.Verdict.Skipped,
            UpdatePackageDigest.verify(hello.inputStream(), ""),
        )
        assertEquals(
            UpdatePackageDigest.Verdict.Skipped,
            UpdatePackageDigest.verify(null, "sha256:$helloDigest"),
        )
    }
}
