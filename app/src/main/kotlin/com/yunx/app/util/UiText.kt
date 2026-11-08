package com.yunx.app.util

import androidx.annotation.StringRes

/**
 * ViewModel 里保存的「未解析」提示文案。
 *
 * 直接存 `AppStrings.get(...)` 的结果会把当时的语言固化进状态，切语言（Activity 重建）后
 * 旧消息仍是旧语言；存资源 id + 参数则在渲染时按当前语言解析。
 * 外部异常自带的 message 无法映射到资源，用 [Raw] 原样透传。
 */
sealed interface UiText {
    fun resolve(): String

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        override fun resolve(): String = AppStrings.get(id, *args.toTypedArray())
    }

    data class Raw(val value: String) : UiText {
        override fun resolve(): String = value
    }

    /** 由多段文案拼接而成（例如「成功 N 个、失败 M 个」+「原因：…」） */
    data class Join(val parts: List<UiText>) : UiText {
        override fun resolve(): String = parts.joinToString("") { it.resolve() }
    }
}
