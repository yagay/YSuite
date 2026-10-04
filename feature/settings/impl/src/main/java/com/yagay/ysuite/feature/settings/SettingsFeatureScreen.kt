package com.yagay.ysuite.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteDataRow
import com.yagay.ysuite.designsystem.component.YSuiteItemKind
import com.yagay.ysuite.designsystem.component.YSuitePanel
import com.yagay.ysuite.designsystem.component.YSuiteSegmentOption
import com.yagay.ysuite.designsystem.component.YSuiteSegmentedControl
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode
import com.yagay.ysuite.ui.YSuiteSettingsScreen

@Composable
fun SettingsFeatureScreen(
    repository: AppSettingsRepository,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(repository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    YSuiteSettingsScreen(
        title = stringResource(R.string.settings_title),
        subtitle = stringResource(R.string.settings_summary),
    ) { _ ->
        val themeLabel =
            when (state.settings.themeMode) {
                AppThemeMode.System ->
                    stringResource(R.string.settings_theme_system)
                AppThemeMode.Light ->
                    stringResource(R.string.settings_theme_light)
                AppThemeMode.Dark ->
                    stringResource(R.string.settings_theme_dark)
            }

        YSuitePanel(
            title = stringResource(R.string.settings_theme),
        ) {
            YSuiteDataRow(
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel,
                kind = YSuiteItemKind.Settings,
            )
            YSuiteSegmentedControl(
                options = listOf(
                    YSuiteSegmentOption(
                        AppThemeMode.System.name,
                        stringResource(R.string.settings_theme_system),
                    ),
                    YSuiteSegmentOption(
                        AppThemeMode.Light.name,
                        stringResource(R.string.settings_theme_light),
                    ),
                    YSuiteSegmentOption(
                        AppThemeMode.Dark.name,
                        stringResource(R.string.settings_theme_dark),
                    ),
                ),
                selectedId = state.settings.themeMode.name,
                onSelected = { selected ->
                    viewModel.setThemeMode(AppThemeMode.valueOf(selected))
                },
            )
        }

        val languageId = state.settings.languageTag ?: "system"
        val languageLabel =
            when (languageId) {
                "en" ->
                    stringResource(R.string.settings_language_english)
                "zh-Hans" ->
                    stringResource(R.string.settings_language_chinese)
                else ->
                    stringResource(R.string.settings_language_system)
            }

        YSuitePanel(
            title = stringResource(R.string.settings_language),
        ) {
            YSuiteDataRow(
                title = stringResource(R.string.settings_language),
                subtitle = languageLabel,
                kind = YSuiteItemKind.Settings,
            )
            YSuiteSegmentedControl(
                options = listOf(
                    YSuiteSegmentOption(
                        "system",
                        stringResource(R.string.settings_language_system),
                    ),
                    YSuiteSegmentOption(
                        "en",
                        stringResource(R.string.settings_language_english),
                    ),
                    YSuiteSegmentOption(
                        "zh-Hans",
                        stringResource(R.string.settings_language_chinese),
                    ),
                ),
                selectedId = languageId,
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
