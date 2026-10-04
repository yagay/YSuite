package com.yagay.ysuite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ysuite.resources.LocaleController
import com.yagay.ysuite.settings.AppSettings
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode
import kotlinx.coroutines.flow.map

@Composable
fun YSuiteApplication(
    settingsRepository: AppSettingsRepository,
    featureRegistry: YSuiteFeatureRegistry,
    singleFeature: Boolean = false,
) {
    val loadedSettings by settingsRepository.settings
        .map<AppSettings, AppSettings?> { it }
        .collectAsStateWithLifecycle(initialValue = null)
    val settings = loadedSettings ?: return
    val context = LocalContext.current

    LaunchedEffect(settings.languageTag) {
        LocaleController.applyLanguageTag(
            context = context,
            languageTag = settings.languageTag,
        )
    }

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    YSuiteRoot(darkTheme = darkTheme) {
        if (singleFeature) {
            YSuiteSingleFeatureHost(registry = featureRegistry)
        } else {
            YSuiteFeatureHost(registry = featureRegistry)
        }
    }
}
