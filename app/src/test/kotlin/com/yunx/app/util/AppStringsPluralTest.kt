package com.yunx.app.util

import com.yunx.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 数量型文案必须按数量走单复数分支，而不是把数字硬拼进同一条固定文案。
 */
class AppStringsPluralTest {

    private fun withPluralResolver(block: () -> Unit) {
        AppStrings.installPluralResolverForTest { _, quantity, _ ->
            if (quantity == 1) "1 item" else "$quantity items"
        }
        try {
            block()
        } finally {
            AppStrings.installPluralResolverForTest(null)
        }
    }

    @Test
    fun `数量为一时使用单数文案`() {
        withPluralResolver {
            assertEquals("1 item", AppStrings.getQuantity(R.plurals.csa_total_count, 1, 1))
        }
    }

    @Test
    fun `数量大于一时使用复数文案并带上实际数量`() {
        withPluralResolver {
            assertEquals("3 items", AppStrings.getQuantity(R.plurals.csa_total_count, 3, 3))
            assertEquals("2 items", AppStrings.getQuantity(R.plurals.settings_max_tasks_desc_bg, 2, 2))
        }
    }
}
