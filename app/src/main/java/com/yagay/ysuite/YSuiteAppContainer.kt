package com.yagay.ysuite

import android.content.Context
import com.yagay.ysuite.feature.settings.SettingsFeatureUiRegistration
import com.yagay.ysuite.feature.template.TemplateFeatureUiRegistration
import com.yagay.ysuite.logging.AndroidYSuiteLogger
import com.yagay.ysuite.permissions.AndroidPermissionChecker
import com.yagay.ysuite.platform.android.DefaultPlatformServices
import com.yagay.ysuite.settings.DataStoreAppSettingsRepository
import com.yagay.ysuite.ui.YSuiteFeatureRegistry

class YSuiteAppContainer(
    context: Context,
) {
    val settings = DataStoreAppSettingsRepository(context)
    val logger = AndroidYSuiteLogger()
    val permissions = AndroidPermissionChecker(context)
    val platform = DefaultPlatformServices.create()

    val featureRegistry = YSuiteFeatureRegistry(
        listOf(
            TemplateFeatureUiRegistration,
            SettingsFeatureUiRegistration(settings),
        ),
    )
}
