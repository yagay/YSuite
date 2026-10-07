package com.yagay.ysuite.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yagay.ysuite.settings.AppSettings
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode

const val YSUITE_EXTRA_INITIAL_FEATURE_ID =
    "com.yagay.ysuite.extra.INITIAL_FEATURE_ID"

@Composable
fun YSuiteApplication(
    settingsRepository: AppSettingsRepository,
    featureRegistry: YSuiteFeatureRegistry,
    singleFeature: Boolean = false,
    initialFeatureId: String? = null,
) {
    val settings by
        settingsRepository.settings
            .collectAsStateWithLifecycle(
                initialValue = AppSettings(),
            )
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    YSuiteRoot(
        darkTheme = darkTheme,
        dynamicColorEnabled =
            settings.dynamicColorEnabled,
    ) {
        if (singleFeature) {
            YSuiteSingleFeatureHost(registry = featureRegistry)
        } else {
            YSuiteFeatureHost(
                registry = featureRegistry,
                initialFeatureId = initialFeatureId,
            )
        }
    }
}
