package com.yagay.ysuite.feature.yfloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yfloat.runtime.YFloatRuntimeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class YFloatUiState(
    val snapshot: YFloatSnapshot? = null,
    val hookSummary: String = "",
    val statusToken: String? = null,
)

internal class YFloatViewModel(
    private val repository: YFloatRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(YFloatUiState())
    val state: StateFlow<YFloatUiState> =
        mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    snapshot =
                        repository.snapshot(),
                    hookSummary =
                        repository.lsposedSummary(),
                )
        }
    }

    fun service(value: Boolean) =
        mutate {
            repository.setService(value)
        }

    fun bool(key: String, value: Boolean) =
        mutate {
            repository.putBoolean(
                key,
                value,
            )
            if (
                key in
                    setOf(
                        YFloatRuntimeBridge.K_ENHANCED_MODE,
                        YFloatRuntimeBridge.K_LSPOSED_ENABLED,
                        YFloatRuntimeBridge.K_LSPOSED_SECURE_SCREENSHOT,
                        YFloatRuntimeBridge.K_DIAGNOSTIC,
                    )
            ) {
                viewModelScope.launch {
                    repository.syncHooks()
                    refresh()
                }
            }
        }

    fun int(key: String, value: Int) =
        mutate {
            repository.putInt(key, value)
        }

    fun string(key: String, value: String) =
        mutate {
            repository.putString(key, value)
        }

    fun openOverlayPermission() =
        repository.openOverlayPermission()

    fun openAccessibility() =
        repository.openAccessibility()

    fun checkRoot() {
        repository.checkRoot {
            mutableState.value =
                mutableState.value.copy(
                    statusToken =
                        if (it) {
                            "root_granted"
                        } else {
                            "root_denied"
                        },
                )
            refresh()
        }
    }

    fun syncHooks() {
        viewModelScope.launch {
            val result = repository.syncHooks()
            mutableState.value =
                mutableState.value.copy(
                    statusToken =
                        if (
                            result is
                            com.yagay.ysuite.common.Outcome.Success
                        ) {
                            "hook_synced"
                        } else {
                            "hook_sync_failed"
                        },
                )
            refresh()
        }
    }

    private fun mutate(block: () -> Unit) {
        block()
        refresh()
    }

    class Factory(
        private val repository: YFloatRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YFloatViewModel(repository) as T
    }
}
