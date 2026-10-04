package com.yagay.ysuite.feature.template

import com.yagay.ysuite.presentation.YSuiteViewModel

data class TemplateUiState(
    val enabled: Boolean = false,
)

sealed interface TemplateEffect {
    data object SettingChanged : TemplateEffect
}

class TemplateViewModel : YSuiteViewModel<TemplateUiState, TemplateEffect>(
    initialState = TemplateUiState(),
) {
    fun setEnabled(enabled: Boolean) {
        updateState { it.copy(enabled = enabled) }
        emitEffect(TemplateEffect.SettingChanged)
    }
}
