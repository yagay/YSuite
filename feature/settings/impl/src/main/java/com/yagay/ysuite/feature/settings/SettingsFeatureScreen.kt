package com.yagay.ysuite.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.productui.settings.YSettingsSurface
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun SettingsFeatureScreen(
    repository: AppSettingsRepository,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    YSettingsSurface(
        title = stringResource(R.string.settings_title),
        navigationIcon = { YSuiteHostNavigationButton() },
    ) {
        YSuiteSection(
            title = stringResource(R.string.settings_appearance),
        ) {
            YSuiteListItem(
                title = stringResource(R.string.settings_theme),
            )
            YSuiteFilterBar(
                options = listOf(
                    YSuiteFilterOption(
                        AppThemeMode.System.name,
                        stringResource(R.string.settings_theme_system),
                    ),
                    YSuiteFilterOption(
                        AppThemeMode.Light.name,
                        stringResource(R.string.settings_theme_light),
                    ),
                    YSuiteFilterOption(
                        AppThemeMode.Dark.name,
                        stringResource(R.string.settings_theme_dark),
                    ),
                ),
                selectedId = state.settings.themeMode.name,
                onSelected = { selected ->
                    viewModel.setThemeMode(
                        AppThemeMode.valueOf(selected),
                    )
                },
            )

            YSuiteListItem(
                title = stringResource(R.string.settings_language),
            )
            YSuiteFilterBar(
                options = listOf(
                    YSuiteFilterOption(
                        "system",
                        stringResource(R.string.settings_language_system),
                    ),
                    YSuiteFilterOption(
                        "en",
                        stringResource(R.string.settings_language_english),
                    ),
                    YSuiteFilterOption(
                        "zh-Hans",
                        stringResource(R.string.settings_language_chinese),
                    ),
                ),
                selectedId = state.settings.languageTag ?: "system",
                onSelected = { selected ->
                    viewModel.setLanguageTag(
                        selected.takeUnless { it == "system" },
                    )
                },
            )
        }
    }
}

private class SettingsViewModelFactory(
    private val repository: AppSettingsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        SettingsViewModel(repository) as T
}
