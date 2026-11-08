package com.yunx.app.util

import androidx.annotation.StringRes
import com.yunx.app.ui.theme.AppLanguage

/**
 * 网盘账号默认昵称的展示规则（M2）。
 *
 * 早期版本把「139用户 / GuangYa user / 光鴨ユーザー」这类默认昵称直接写进了数据库，
 * 切语言后旧值不会跟着变。现在落库只写空串，展示时回退到当前语言的默认昵称；
 * 存量记录在这里识别——命中任意语言的默认昵称即视为「未设置」。
 */
object AccountNames {

    /** 可选语言的 BCP47 标签（不含「跟随系统」），用于收集默认昵称的全部语言变体。 */
    private val languageTags: List<String>
        get() = AppLanguage.values().map { it.tag }.filter { it.isNotEmpty() }

    /**
     * 展示用昵称：空值或旧语言默认名回退到当前语言默认名，其余原样返回。
     * @param legacyDefaults 存量默认昵称集合，默认取 [defaultRes] 在五种语言下的取值
     */
    fun display(
        @StringRes defaultRes: Int,
        stored: String,
        legacyDefaults: Collection<String> = defaultValues(defaultRes)
    ): String = if (isUnset(defaultRes, stored, legacyDefaults)) AppStrings.get(defaultRes) else stored

    /** 是否等于「未设置」：空串，或任意语言下该默认昵称的旧值。 */
    fun isUnset(
        @StringRes defaultRes: Int,
        stored: String,
        legacyDefaults: Collection<String> = defaultValues(defaultRes)
    ): Boolean {
        val value = stored.trim()
        return value.isEmpty() || legacyDefaults.any { it.trim() == value }
    }

    private val defaultsCache = java.util.concurrent.ConcurrentHashMap<Int, List<String>>()

    private fun defaultValues(@StringRes defaultRes: Int): List<String> =
        defaultsCache.computeIfAbsent(defaultRes) { res ->
            languageTags.map { AppStrings.getInLanguage(res, it) }.filter { it.isNotBlank() }
        }
}
