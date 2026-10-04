package com.yagay.ysuite.resources

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLocale(val languageTag: String?) {
    System(null),
    English("en"),
    SimplifiedChinese("zh-Hans"),
}

object LocaleController {
    fun apply(context: Context, locale: AppLocale) {
        applyLanguageTag(context, locale.languageTag)
    }

    fun applyLanguageTag(context: Context, languageTag: String?) {
        val locales =
            if (languageTag.isNullOrBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }

        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
