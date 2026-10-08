package com.yagay.ysuite.feature.yfloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yfloat.runtime.YFloatRuntimeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    private val changeMutex = Mutex()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val (snapshot, summary) =
                withContext(Dispatchers.IO) {
                    repository.snapshot() to
                        repository.lsposedSummary()
                }
            mutableState.value =
                mutableState.value.copy(
                    snapshot = snapshot,
                    hookSummary = summary,
                )
        }
    }

    fun service(value: Boolean) =
        mutate {
            repository.setService(value)
        }

    fun bool(key: String, value: Boolean) =
        mutate(
            syncHooks =
                key in
                    setOf(
                        YFloatRuntimeBridge.K_ENHANCED_MODE,
                        YFloatRuntimeBridge.K_LSPOSED_ENABLED,
                        YFloatRuntimeBridge.K_LSPOSED_SECURE_SCREENSHOT,
                        YFloatRuntimeBridge.K_DIAGNOSTIC,
                    ),
        ) {
            repository.putBoolean(
                key,
                value,
            )
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
            val result =
                withContext(Dispatchers.IO) {
                    changeMutex.withLock {
                        repository.syncHooks()
                    }
                }
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

    private fun mutate(
        syncHooks: Boolean = false,
        block: () -> Unit,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    changeMutex.withLock {
                        block()
                        if (syncHooks) {
                            repository.syncHooks()
                        } else {
                            null
                        }
                    }
                }
            if (syncHooks) {
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
            }
            refresh()
        }
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
