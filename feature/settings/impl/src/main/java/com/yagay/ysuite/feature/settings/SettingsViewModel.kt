package com.yagay.ysuite.feature.settings

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.presentation.YSuiteViewModel
import com.yagay.ysuite.settings.AppSettings
import com.yagay.ysuite.settings.AppSettingsRepository
import com.yagay.ysuite.settings.AppThemeMode
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
)

class SettingsViewModel(
    private val repository: AppSettingsRepository,
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

    fun setLanguageTag(languageTag: String?) {
        viewModelScope.launch {
            repository.setLanguageTag(languageTag)
        }
    }
}
