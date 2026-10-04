package com.yagay.ysuite.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.settings.api.SettingsFeatureContract
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuitePageKind

class SettingsFeatureUiRegistration(
    private val repository: AppSettingsRepository,
) : YSuiteFeatureUiRegistration {
    override val contract = SettingsFeatureContract

    override val pageKind =
        YSuitePageKind.Settings

    @Composable
    override fun label(): String =
        stringResource(R.string.settings_title)

    @Composable
    override fun Content() {
        SettingsFeatureScreen(repository = repository)
    }
}
