package com.yagay.YSuite

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.yagay.suite.core.SuiteCommonSetting
import com.yagay.suite.core.SuiteCommonSettings
import java.util.Locale

/** Language for the integrated YSuite host; standalone APKs have independent system locales. */
internal object SuiteLocaleController {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val CHINESE = "zh-CN"

    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val tags = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales?.toLanguageTags().orEmpty()
            if (tags.isEmpty()) return SYSTEM
            return if (Locale.forLanguageTag(tags.substringBefore(',')).language == "zh") CHINESE else ENGLISH
        }
        return SuiteCommonSettings(context).value(SuiteCommonSetting.LANGUAGE)
    }

    fun localizedContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = SuiteCommonSettings(base).value(SuiteCommonSetting.LANGUAGE)
        if (tag == SYSTEM) return base
        val language = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(language)
        config.setLayoutDirection(language)
        return base.createConfigurationContext(config)
    }

    fun initializeLegacyDelegates(context: Context) {
        if (Build.VERSION.SDK_INT >= 33) return
        val tag = SuiteCommonSettings(context).value(SuiteCommonSetting.LANGUAGE)
        val requested = if (tag == SYSTEM) "" else tag
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != requested) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(requested))
        }
    }

    fun set(activity: Activity, language: String) {
        SuiteCommonSettings(activity).set(SuiteCommonSetting.LANGUAGE, language)
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = if (language == SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language)
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales = locales
        } else {
            val tags = if (language == SYSTEM) "" else language
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
            activity.recreate()
        }
    }
}
