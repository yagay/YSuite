package com.yagay.ysuite.resources

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLocale(val languageTag: String?) {
    System(null),
    English("en"),
    SimplifiedChinese("zh-Hans"),
}

object LocaleController {
    fun apply(
        context: Context,
        locale: AppLocale,
    ) {
        applyLanguageTag(
            context,
            locale.languageTag,
        )
    }

    fun applyLanguageTag(
        context: Context,
        languageTag: String?,
    ) {
        context.applicationContext

        val requestedTag =
            languageTag
                ?.trim()
                .orEmpty()
        val current =
            AppCompatDelegate
                .getApplicationLocales()

        if (
            localesEquivalent(
                current = current,
                requestedTag = requestedTag,
            )
        ) {
            return
        }

        val locales =
            if (requestedTag.isBlank()) {
                LocaleListCompat
                    .getEmptyLocaleList()
            } else {
                LocaleListCompat
                    .forLanguageTags(
                        requestedTag,
                    )
            }

        AppCompatDelegate
            .setApplicationLocales(locales)
    }

    private fun localesEquivalent(
        current: LocaleListCompat,
        requestedTag: String,
    ): Boolean {
        if (requestedTag.isBlank()) {
            return current.isEmpty
        }

        val requested =
            Locale.forLanguageTag(
                requestedTag,
            )
        val active =
            current[0]
                ?: return false

        if (
            requested.language
                .lowercase() !=
            active.language
                .lowercase()
        ) {
            return false
        }

        val requestedScript =
            normalizedScript(requested)
        val activeScript =
            normalizedScript(active)

        if (requestedScript.isBlank()) {
            return true
        }
        return requestedScript ==
            activeScript
    }

    private fun normalizedScript(
        locale: Locale,
    ): String {
        locale.script
            .takeIf { it.isNotBlank() }
            ?.let { return it }

        if (locale.language != "zh") {
            return ""
        }

        return when (
            locale.country.uppercase()
        ) {
            "CN", "SG" -> "Hans"
            "TW", "HK", "MO" -> "Hant"
            else -> ""
        }
    }
}
