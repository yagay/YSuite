package com.yagay.ysuite.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.productui.settings.ComposeSettingsChoice
import com.yagay.ysuite.productui.settings.ComposeSettingsChoiceGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsSurface
import com.yagay.ysuite.productui.settings.ComposeSettingsSwitch
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

    ComposeSettingsSurface(
        title = stringResource(R.string.settings_title),
        navigationIcon = { YSuiteHostNavigationButton() },
    ) {
        ComposeSettingsGroup(
            title =
                stringResource(
                    R.string.settings_appearance,
                ),
        ) {
            ComposeSettingsChoiceGroup(
                title =
                    stringResource(
                        R.string.settings_theme,
                    ),
                selectedId =
                    state.settings.themeMode.name,
                choices =
                    listOf(
                        ComposeSettingsChoice(
                            AppThemeMode.System.name,
                            stringResource(
                                R.string
                                    .settings_theme_system,
                            ),
                        ),
                        ComposeSettingsChoice(
                            AppThemeMode.Light.name,
                            stringResource(
                                R.string
                                    .settings_theme_light,
                            ),
                        ),
                        ComposeSettingsChoice(
                            AppThemeMode.Dark.name,
                            stringResource(
                                R.string
                                    .settings_theme_dark,
                            ),
                        ),
                    ),
                onSelected = { selected ->
                    viewModel.setThemeMode(
                        AppThemeMode.valueOf(
                            selected,
                        ),
                    )
                },
            )

            ComposeSettingsSwitch(
                title =
                    stringResource(
                        R.string
                            .settings_dynamic_color,
                    ),
                subtitle =
                    stringResource(
                        R.string
                            .settings_dynamic_color_desc,
                    ),
                checked =
                    state.settings
                        .dynamicColorEnabled,
                onCheckedChange =
                    viewModel::
                        setDynamicColorEnabled,
            )
        }

        ComposeSettingsChoiceGroup(
            title =
                stringResource(
                    R.string.settings_language,
                ),
            selectedId = state.settings.languageTag ?: "system",
            choices = listOf(
                ComposeSettingsChoice(
                    "system",
                    stringResource(R.string.settings_language_system),
                ),
                ComposeSettingsChoice(
                    "en",
                    stringResource(R.string.settings_language_english),
                ),
                ComposeSettingsChoice(
                    "zh-Hans",
                    stringResource(R.string.settings_language_chinese),
                ),
            ),
            onSelected = { selected ->
                viewModel.setLanguageTag(
                    selected.takeUnless { it == "system" },
                )
            },
        )
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
