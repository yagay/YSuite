package com.yagay.ysuite.feature.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.presentation.YSuiteViewModel
import com.yagay.ysuite.resources.LocaleController
import com.yagay.ysuite.settings.AppSettings
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

class SettingsViewModel(
    private val repository: AppSettingsRepository,
    private val applicationContext: Context,
) : YSuiteViewModel<SettingsUiState, Nothing>(
    initialState = SettingsUiState(),
) {
    init {
        viewModelScope.launch {
            repository.settings.collect { current ->
                updateState { it.copy(settings = current) }
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDynamicColorEnabled(enabled)
        }
    }

    fun setLanguageTag(languageTag: String?) {
        if (state.value.settings.languageTag == languageTag) return
        viewModelScope.launch {
            // Persist before AppCompat recreates the activity, or the write may be lost.
            repository.setLanguageTag(languageTag)
            LocaleController.applyLanguageTag(
                context = applicationContext,
                languageTag = languageTag,
            )
        }
    }
}
