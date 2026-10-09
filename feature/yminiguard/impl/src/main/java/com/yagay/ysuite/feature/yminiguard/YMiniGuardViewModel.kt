package com.yagay.ysuite.feature.yminiguard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardApp
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardEngineStatus
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardSettings
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class YMiniGuardFilter { All, Protected, Playback, ForceSupport, User, System }

data class YMiniGuardUiState(
    val apps: List<YMiniGuardApp> = emptyList(),
    val appLoadError: String? = null,
    val settings: YMiniGuardSettings = YMiniGuardSettings(),
    val query: String = "",
    val filter: YMiniGuardFilter = YMiniGuardFilter.All,
    val selectedPackage: String? = null,
    val rootStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val engineStatus: YMiniGuardEngineStatus = YMiniGuardEngineStatus(),
    val statusToken: String? = null,
    val diagnostics: String = "",
)

internal class YMiniGuardViewModel(
    private val repository: YMiniGuardRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(YMiniGuardUiState())
    val state: StateFlow<YMiniGuardUiState> = mutableState.asStateFlow()
    private val syncMutex = Mutex()
    private var refreshJob: Job? = null
    private var syncRequestId = 0L

    init { refresh() }

    fun refresh() {
        // Latest refresh wins when a setting changes during inventory loading.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    YMiniGuardUiState(
                        apps = repository.apps(),
                        settings = repository.settings(),
                        rootStatus = runCatching { repository.rootStatus() }
                            .getOrDefault(CapabilityStatus.Error),
                        hookStatus = runCatching { repository.hookStatus() }
                            .getOrDefault(CapabilityStatus.Error),
                        // Engine diagnostics must never prevent the installed
                        // app inventory from being displayed.
                        engineStatus = runCatching { repository.engineStatus() }
                            .getOrDefault(mutableState.value.engineStatus),
                    )
                }
                mutableState.value = mutableState.value.copy(
                    apps = snapshot.apps,
                    settings = snapshot.settings,
                    rootStatus = snapshot.rootStatus,
                    hookStatus = snapshot.hookStatus,
                    engineStatus = snapshot.engineStatus,
                    appLoadError = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    appLoadError = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun setFilter(value: YMiniGuardFilter) {
        mutableState.value = mutableState.value.copy(filter = value)
    }

    fun visibleApps(): List<YMiniGuardApp> {
        val state = mutableState.value
        val q = state.query.trim()
        return state.apps.filter { app ->
            val match = when (state.filter) {
                YMiniGuardFilter.All -> true
                YMiniGuardFilter.Protected -> app.alwaysForeground
                YMiniGuardFilter.Playback -> app.backgroundPlayback
                YMiniGuardFilter.ForceSupport -> app.forceFlexibleSupport
                YMiniGuardFilter.User -> !app.system
                YMiniGuardFilter.System -> app.system
            }
            match && (q.isBlank() ||
                app.label.contains(q, true) ||
                app.packageName.contains(q, true))
        }
    }

    fun select(packageName: String?) {
        mutableState.value = mutableState.value.copy(selectedPackage = packageName)
    }

    fun updateApp(
        packageName: String,
        foreground: Boolean? = null,
        playback: Boolean? = null,
        forceSupport: Boolean? = null,
    ) {
        repository.updateApp(packageName, foreground, playback, forceSupport)
        mutableState.value =
            mutableState.value.copy(
                apps = mutableState.value.apps.map { app ->
                    if (app.packageName != packageName) app
                    else app.copy(
                        alwaysForeground = foreground ?: app.alwaysForeground,
                        backgroundPlayback = playback ?: app.backgroundPlayback,
                        forceFlexibleSupport = forceSupport ?: app.forceFlexibleSupport,
                    )
                },
            )
        sync(false)
    }

    fun updateSettings(transform: (YMiniGuardSettings) -> YMiniGuardSettings) {
        val next = transform(repository.settings())
        repository.setSettings(next)
        mutableState.value =
            mutableState.value.copy(settings = next)
        sync(false)
    }

    fun reloadEngine() = sync(true)

    private fun sync(reload: Boolean) {
        val requestId = ++syncRequestId
        viewModelScope.launch {
            try {
            val (result, previous) = withContext(Dispatchers.IO) {
                syncMutex.withLock {
                    val before = repository.engineStatus()
                    repository.sync(reload) to before
                }
            }
            val engine = if (result is com.yagay.ysuite.common.Outcome.Success) {
                withContext(Dispatchers.IO) {
                    var current = repository.engineStatus()
                    repeat(8) {
                        if (current.pid > 0 && current.hookCount > 0 &&
                            (!reload || current.generation > previous.generation)
                        ) return@withContext current
                        delay(250L)
                        current = repository.engineStatus()
                    }
                    current
                }
            } else null
            val token = when {
                result is com.yagay.ysuite.common.Outcome.Failure -> "sync_failed"
                engine == null || engine.pid <= 0 || engine.hookCount <= 0 ->
                    "hook_inactive"
                reload && engine.generation <= previous.generation ->
                    "reload_pending"
                reload -> "reloaded"
                else -> "config_pending"
            }
            // Older operations must not override the result shown for a
            // newer settings edit or explicit reload.
            if (requestId == syncRequestId) {
                mutableState.value = mutableState.value.copy(statusToken = token)
                refresh()
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == syncRequestId) {
                    mutableState.value = mutableState.value.copy(
                        statusToken = "sync_failed",
                        appLoadError = error.message ?: error.javaClass.simpleName,
                    )
                }
            }
        }
    }

    fun diagnostics() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { repository.diagnostics() }
            mutableState.value = mutableState.value.copy(diagnostics = text)
        }
    }

    class Factory(
        private val repository: YMiniGuardRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YMiniGuardViewModel(repository) as T
    }
}
