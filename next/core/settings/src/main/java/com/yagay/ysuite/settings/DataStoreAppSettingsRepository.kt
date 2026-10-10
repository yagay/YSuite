package com.yagay.ysuite.settings

import android.content.Context
import com.yagay.suite.core.SuiteCommonSetting
import com.yagay.suite.core.SuiteCommonSettings
import com.yagay.yui.YAppearanceStore
import com.yagay.yui.YSettingKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Legacy-compatible settings adapter. Theme and dynamic color are owned by YAppearanceStore,
 * and language by SuiteCommonSettings; this class no longer saves duplicate preferences.
 */
class DataStoreAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val appearance = YAppearanceStore(context)
    private val common = SuiteCommonSettings(context)

    private fun snapshot(): AppSettings {
        val theme = when (appearance.value(YSettingKey.THEME)) {
            "dark" -> AppThemeMode.Dark
            "light" -> AppThemeMode.Light
            else -> AppThemeMode.System
        }
        val language = common.value(SuiteCommonSetting.LANGUAGE)
        return AppSettings(
            themeMode = theme,
            dynamicColorEnabled = appearance.value(YSettingKey.DYNAMIC_COLOR) == "true",
            languageTag = language.takeIf { it != "system" },
        )
    }

    override val settings: Flow<AppSettings> = callbackFlow {
        val notifyChanged: () -> Unit = { trySend(snapshot()) }
        val stopVisual = appearance.listen(notifyChanged)
        val stopCommon = common.observe(notifyChanged)
        notifyChanged()
        awaitClose {
            stopVisual()
            stopCommon()
        }
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: AppThemeMode) {
        appearance.set(
            YSettingKey.THEME,
            when (mode) {
                AppThemeMode.System -> "system"
                AppThemeMode.Light -> "light"
                AppThemeMode.Dark -> "dark"
            },
        )
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        appearance.set(YSettingKey.DYNAMIC_COLOR, enabled.toString())
    }

    override suspend fun setLanguageTag(languageTag: String?) {
        val selected = languageTag?.takeIf(String::isNotBlank) ?: "system"
        common.set(SuiteCommonSetting.LANGUAGE, selected)
    }
}
