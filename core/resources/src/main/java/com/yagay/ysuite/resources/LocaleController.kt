package com.yagay.ysuite.resources

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLocale(val languageTag: String?) {
    System(null),
    English("en"),
    SimplifiedChinese("zh-Hans"),
}

object LocaleController {
    fun apply(context: Context, locale: AppLocale) {
        val tags = locale.languageTag.orEmpty()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tags.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tags)
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (tags.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(tags)
            )
        }
    }
}
