package com.yunx.app.util

import com.yunx.app.R
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * 默认昵称展示规则：落库只存空串，展示时回退到当前语言；
 * 旧版本按语言写死进库的默认昵称要能被识别成「未设置」，避免切语言后停在旧语言。
 */
class AccountNamesTest {

    private val legacyDefaults = listOf(
        "139用户", "139用戶", "139 user", "139 ユーザー", "Utilisateur 139"
    )

    private fun withCurrentLanguage(value: String, block: () -> Unit) {
        AppStrings.installResolverForTest { _, _ -> value }
        try {
            block()
        } finally {
            AppStrings.installResolverForTest(null)
        }
    }

    @Test
    fun `空昵称回退到当前语言默认名`() {
        withCurrentLanguage("139 user") {
            assertEquals(
                "139 user",
                AccountNames.display(R.string.er_c139_default_nickname, "", legacyDefaults)
            )
            assertEquals(
                "139 user",
                AccountNames.display(R.string.er_c139_default_nickname, "   ", legacyDefaults)
            )
        }
    }

    @Test
    fun `旧语言写死的默认昵称视为未设置`() {
        withCurrentLanguage("139 user") {
            AccountNames.display(R.string.er_c139_default_nickname, "139用户", legacyDefaults)
                .let { assertEquals("139 user", it) }
            AccountNames.display(R.string.er_c139_default_nickname, "139 ユーザー", legacyDefaults)
                .let { assertEquals("139 user", it) }
            AccountNames.display(R.string.er_c139_default_nickname, "Utilisateur 139", legacyDefaults)
                .let { assertEquals("139 user", it) }
        }
    }

    @Test
    fun `真实昵称原样保留`() {
        withCurrentLanguage("139 user") {
            assertEquals(
                "老王",
                AccountNames.display(R.string.er_c139_default_nickname, "老王", legacyDefaults)
            )
            assertEquals(
                " 老王 ",
                AccountNames.display(R.string.er_c139_default_nickname, " 老王 ", legacyDefaults)
            )
        }
    }
}
