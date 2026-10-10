package com.yagay.ysuite.ui

import androidx.compose.runtime.Composable
import com.yagay.ysuite.settings.AppSettingsRepository

const val YSUITE_EXTRA_INITIAL_FEATURE_ID =
    "com.yagay.ysuite.extra.INITIAL_FEATURE_ID"

@Suppress("UNUSED_PARAMETER")
@Composable
fun YSuiteApplication(
    settingsRepository: AppSettingsRepository,
    featureRegistry: YSuiteFeatureRegistry,
    singleFeature: Boolean = false,
    initialFeatureId: String? = null,
) {
    // Single appearance owner: YAppearanceStore, inherited by both rebuilt feature hosts.
    YSuiteRoot {
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
