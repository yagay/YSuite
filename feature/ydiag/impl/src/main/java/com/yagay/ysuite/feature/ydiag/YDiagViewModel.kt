package com.yagay.ysuite.feature.ydiag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ydiag.api.YDiagApp
import com.yagay.ysuite.feature.ydiag.api.YDiagCatalog
import com.yagay.ysuite.feature.ydiag.api.YDiagEvent
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YDiagAppFilter {
    All,
    User,
    System,
}

data class YDiagUiState(
    val apps: List<YDiagApp> = emptyList(),
    val appsLoadError: String? = null,
    val selectedPackage: String? = null,
    val query: String = "",
    val appFilter: YDiagAppFilter = YDiagAppFilter.All,
    val enabledOptions: Set<String> = YDiagCatalog.defaultEnabled,
    val presetId: String = "quick",
    val events: List<YDiagEvent> = emptyList(),
    val selectedEventId: String? = null,
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val running: Boolean = false,
    val liveSessionActive: Boolean = false,
    val exportUri: String? = null,
    val error: String? = null,
)

class YDiagViewModel(
    private val environment: YDiagEnvironment,
) : ViewModel() {
    private val repository =
        YDiagRepository(
            context = environment.applicationContext,
            root = environment.rootGateway,
            hooks = environment.hookGateway,
            logger = environment.logger,
        )
    private val mutableState = MutableStateFlow(YDiagUiState())
    val state: StateFlow<YDiagUiState> = mutableState.asStateFlow()

    init {
        refreshApps()
        viewModelScope.launch {
            val runtime = withContext(Dispatchers.IO) {
                Triple(
                    repository.liveSessionActive(),
                    runCatching { repository.rootStatus() }
                        .getOrDefault(CapabilityStatus.Error),
                    runCatching { repository.hookStatus() }
                        .getOrDefault(CapabilityStatus.Error),
                )
            }
            mutableState.value = mutableState.value.copy(
                liveSessionActive = runtime.first,
                rootStatus = runtime.second,
                hookStatus = runtime.third,
            )
        }
    }

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun setAppFilter(value: YDiagAppFilter) {
        mutableState.value = mutableState.value.copy(appFilter = value)
    }

    fun visibleApps(): List<YDiagApp> {
        val state = mutableState.value
        val needle = state.query.trim()
        return state.apps.filter { app ->
            val typeMatch = when (state.appFilter) {
                YDiagAppFilter.All -> true
                YDiagAppFilter.User -> !app.system
                YDiagAppFilter.System -> app.system
            }
            val searchMatch =
                needle.isBlank() ||
                    app.label.contains(needle, true) ||
                    app.packageName.contains(needle, true)
            typeMatch && searchMatch
        }
    }

    fun visibleEvents(): List<YDiagEvent> {
        val needle = mutableState.value.query.trim()
        return mutableState.value.events.filter {
            needle.isBlank() ||
                it.title.contains(needle, true) ||
                it.detail.contains(needle, true) ||
                it.optionId.contains(needle, true)
        }
    }

    fun selectPackage(value: String?) {
        mutableState.value = mutableState.value.copy(
            selectedPackage = value,
            events = emptyList(),
            selectedEventId = null,
            query = "",
            exportUri = null,
            error = null,
        )
    }

    fun selectEvent(value: YDiagEvent?) {
        mutableState.value = mutableState.value.copy(
            selectedEventId = value?.id,
        )
    }

    fun selectedEvent(): YDiagEvent? =
        mutableState.value.events.firstOrNull {
            it.id == mutableState.value.selectedEventId
        }

    fun applyPreset(id: String) {
        val preset =
            YDiagCatalog.presets.firstOrNull { it.id == id }
                ?: return
        mutableState.value = mutableState.value.copy(
            presetId = id,
            enabledOptions = preset.options,
        )
    }

    fun toggleOption(id: String, enabled: Boolean) {
        val old = mutableState.value.enabledOptions
        mutableState.value = mutableState.value.copy(
            presetId = "custom",
            enabledOptions =
                if (enabled) old + id else old - id,
        )
    }

    fun startLiveSession() {
        val packageName = mutableState.value.selectedPackage ?: return
        val options = mutableState.value.enabledOptions
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.startLiveSession(packageName, options)
                }
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    liveSessionActive = true,
                    error = null,
                )
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    liveSessionActive = false,
                    error = failure.message ?: failure.javaClass.simpleName,
                )
            }
        }
    }

    fun markProblem() {
        if (!mutableState.value.liveSessionActive) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.markProblem() }
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    error = failure.message ?: failure.javaClass.simpleName,
                )
            }
        }
    }

    fun stopLiveSession() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repository.stopLiveSession() }
            }.onSuccess {
                mutableState.value = mutableState.value.copy(
                    liveSessionActive = false,
                    error = null,
                )
            }.onFailure { failure ->
                mutableState.value = mutableState.value.copy(
                    error = failure.message ?: failure.javaClass.simpleName,
                )
            }
        }
    }

    fun runDiagnostics() {
        val packageName =
            mutableState.value.selectedPackage
                ?: return
        val options = mutableState.value.enabledOptions
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                running = true,
                events = emptyList(),
                selectedEventId = null,
                exportUri = null,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.collect(
                        packageName = packageName,
                        optionIds = options,
                    )
                }
            }.onSuccess { events ->
                mutableState.value = mutableState.value.copy(
                    running = false,
                    events = events,
                    rootStatus =
                        runCatching {
                            repository.rootStatus()
                        }.getOrDefault(CapabilityStatus.Error),
                    hookStatus =
                        runCatching {
                            repository.hookStatus()
                        }.getOrDefault(CapabilityStatus.Error),
                )
            }.onFailure {
                environment.logger.error(
                    "YSuite/YDiag",
                    "Diagnostic session failed",
                    it,
                )
                mutableState.value = mutableState.value.copy(
                    running = false,
                    error = it.message ?: it.javaClass.simpleName,
                )
            }
        }
    }

    fun export() {
        val state = mutableState.value
        val packageName = state.selectedPackage ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.export(
                        packageName,
                        state.enabledOptions,
                        state.events,
                    )
                }
            }.onSuccess {
                mutableState.value =
                    mutableState.value.copy(exportUri = it)
            }.onFailure {
                mutableState.value = mutableState.value.copy(
                    error = it.message ?: it.javaClass.simpleName,
                )
            }
        }
    }

    fun retryApps() = refreshApps()

    private fun refreshApps() {
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    repository.apps()
                }
                mutableState.value = mutableState.value.copy(
                    apps = apps,
                    appsLoadError = if (apps.isEmpty()) "no_visible_apps" else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    appsLoadError = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    class Factory(
        private val environment: YDiagEnvironment,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YDiagViewModel(environment) as T
    }
}
