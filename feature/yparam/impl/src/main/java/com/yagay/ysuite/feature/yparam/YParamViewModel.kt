package com.yagay.ysuite.feature.yparam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yparam.api.YParamAppSummary
import com.yagay.ysuite.feature.yparam.api.YParamDefaults
import com.yagay.ysuite.feature.yparam.api.YParamOverrides
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YParamAppFilter {
    All,
    User,
    System,
    Overridden,
}

data class YParamUiState(
    val apps: List<YParamAppSummary> = emptyList(),
    val appLoadError: String? = null,
    val query: String = "",
    val filter: YParamAppFilter = YParamAppFilter.All,
    val selectedPackage: String? = null,
    val draft: YParamOverrides = YParamOverrides(),
    val defaults: YParamDefaults? = null,
    val baseline: String? = null,
    val diagnostics: YParamDiagnostics? = null,
    val hookStatus: CapabilityStatus = CapabilityStatus.Unavailable,
    val saving: Boolean = false,
    val message: String? = null,
)

class YParamViewModel(
    private val environment: YParamEnvironment,
) : ViewModel() {
    private val repository =
        YParamRepository(environment.applicationContext)
    private val mutableState = MutableStateFlow(YParamUiState())
    val state: StateFlow<YParamUiState> = mutableState.asStateFlow()
    private var selectionJob: Job? = null

    init {
        reloadApps()
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                hookStatus =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            environment.hookGateway.status()
                        }
                    }.getOrDefault(CapabilityStatus.Error),
            )
        }
    }

    fun setQuery(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun setFilter(value: YParamAppFilter) {
        mutableState.value = mutableState.value.copy(filter = value)
    }

    fun visibleApps(): List<YParamAppSummary> {
        val state = mutableState.value
        val needle = state.query.trim()
        return state.apps.filter { app ->
            val filterMatch = when (state.filter) {
                YParamAppFilter.All -> true
                YParamAppFilter.User -> !app.system
                YParamAppFilter.System -> app.system
                YParamAppFilter.Overridden -> app.overrideCount > 0
            }
            val searchMatch =
                needle.isBlank() ||
                    app.label.contains(needle, true) ||
                    app.packageName.contains(needle, true)
            filterMatch && searchMatch
        }
    }

    fun select(packageName: String?) {
        selectionJob?.cancel()
        mutableState.value = mutableState.value.copy(
            selectedPackage = packageName,
            draft = YParamOverrides(),
            defaults = null,
            baseline = null,
            diagnostics = null,
            message = null,
        )
        if (packageName == null) return
        selectionJob = viewModelScope.launch {
            val details = runCatching {
                withContext(Dispatchers.IO) {
                    val overrides = repository.read(packageName)
                    val defaults = repository.defaults(packageName)
                    val baseline = repository.baseline(packageName)
                    val diagnostics = runCatching {
                        repository.diagnostics(packageName)
                    }.getOrNull()
                    Pair(Triple(overrides, defaults, baseline), diagnostics)
                }
            }
            if (mutableState.value.selectedPackage != packageName) return@launch
            details.onSuccess { (values, diagnostics) ->
                mutableState.value = mutableState.value.copy(
                    draft = values.first,
                    defaults = values.second,
                    baseline = values.third,
                    diagnostics = diagnostics,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }

    fun applyPreset(id: String) {
        val old = mutableState.value.draft
        val value = when (id) {
            "tablet" ->
                old.copy(
                    densityDpi = 320,
                    smallestWidthDp = 600,
                    fontScale = 1.0f,
                )
            "compact" ->
                old.copy(
                    densityDpi = 380,
                    fontScale = 0.95f,
                )
            "uk" ->
                old.copy(
                    localeTag = "en-GB",
                    timeZoneId = "Europe/London",
                )
            else -> old
        }
        mutableState.value = mutableState.value.copy(draft = value)
    }

    fun update(key: String, raw: String?) {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() }
        val old = mutableState.value.draft
        val next = runCatching {
            when (key) {
                "densityDpi" -> old.copy(densityDpi = value?.toInt())
                "widthPixels" -> old.copy(widthPixels = value?.toInt())
                "heightPixels" -> old.copy(heightPixels = value?.toInt())
                "smallestWidthDp" -> old.copy(smallestWidthDp = value?.toInt())
                "screenWidthDp" -> old.copy(screenWidthDp = value?.toInt())
                "screenHeightDp" -> old.copy(screenHeightDp = value?.toInt())
                "fontScale" -> old.copy(fontScale = value?.toFloat())
                "xdpi" -> old.copy(xdpi = value?.toFloat())
                "ydpi" -> old.copy(ydpi = value?.toFloat())
                "refreshRate" -> old.copy(refreshRate = value?.toFloat())
                "localeTag" -> old.copy(localeTag = value)
                "timeZoneId" -> old.copy(timeZoneId = value)
                "nightMode" -> old.copy(nightMode = value)
                "orientation" -> old.copy(orientation = value)
                "userAgent" -> old.copy(userAgent = value)
                "allowScreenshots" ->
                    old.copy(allowScreenshots = value?.toBooleanStrictOrNull())
                "keepScreenOn" ->
                    old.copy(keepScreenOn = value?.toBooleanStrictOrNull())
                "locationMode" -> old.copy(locationMode = value)
                "latitude" -> old.copy(latitude = value?.toDouble())
                "longitude" -> old.copy(longitude = value?.toDouble())
                "altitude" -> old.copy(altitude = value?.toDouble())
                "accuracy" -> old.copy(accuracy = value?.toFloat())
                "speed" -> old.copy(speed = value?.toFloat())
                "bearing" -> old.copy(bearing = value?.toFloat())
                "randomRadiusMeters" ->
                    old.copy(randomRadiusMeters = value?.toFloat())
                "locationUpdateIntervalMs" ->
                    old.copy(locationUpdateIntervalMs = value?.toInt())
                else -> old
            }
        }.getOrElse {
            mutableState.value = mutableState.value.copy(
                message = it.message ?: "Invalid value",
            )
            return
        }
        mutableState.value = mutableState.value.copy(
            draft = next,
            message = null,
        )
    }

    fun save() {
        val packageName = mutableState.value.selectedPackage ?: return
        val value = mutableState.value.draft
        val error = validate(value)
        if (error != null) {
            mutableState.value = mutableState.value.copy(message = error)
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true)
            runCatching {
                val payload =
                    withContext(Dispatchers.IO) {
                        repository.ensureBaseline(
                            packageName,
                        )
                        repository.save(
                            packageName,
                            value,
                        )
                        repository.hookPayload(
                            value,
                        )
                    }
                withContext(Dispatchers.IO) {
                    val configResult =
                        environment.hookGateway.writeConfig(
                            group = "yparam",
                            key = "app." + packageName,
                            value = payload,
                        )
                    val scopeResult =
                        if (configResult is Outcome.Success) {
                            environment.hookGateway.reload(setOf(packageName))
                        } else {
                            configResult
                        }
                    configResult to scopeResult
                }
            }.onSuccess {
                    (configResult, scopeResult) ->
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    hookStatus =
                        runCatching {
                            withContext(Dispatchers.IO) {
                            environment.hookGateway.status()
                        }
                        }.getOrDefault(CapabilityStatus.Error),
                    message =
                        if (
                            configResult is
                                Outcome.Success &&
                            scopeResult is
                                Outcome.Success
                        ) {
                            "saved_target_restart"
                        } else {
                            "saved_hook_reload_unavailable"
                        },
                )
                reloadApps()
            }.onFailure {
                environment.logger.error(
                    "YSuite/YParam",
                    "Save failed",
                    it,
                )
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    message = it.message ?: it.javaClass.simpleName,
                )
            }
        }
    }

    fun reset() {
        val packageName =
            mutableState.value.selectedPackage
                ?: return
        viewModelScope.launch {
            val previous = withContext(Dispatchers.IO) {
                repository.read(packageName)
            }
            mutableState.value =
                mutableState.value.copy(
                    saving = true,
                    message = null,
                )

            runCatching {
                withContext(Dispatchers.IO) {
                    repository.reset(packageName)
                }

                val configResult =
                    environment.hookGateway
                        .writeConfig(
                            group = "yparam",
                            key =
                                "app." +
                                    packageName,
                            value = null,
                        )

                if (
                    configResult is
                    Outcome.Failure
                ) {
                    withContext(Dispatchers.IO) {
                        repository.save(
                            packageName,
                            previous,
                        )
                    }
                    return@runCatching (
                        configResult to
                            null
                        )
                }

                val scopeResult =
                    environment.hookGateway
                        .reload(
                            setOf(packageName),
                        )
                configResult to scopeResult
            }.onSuccess {
                    (configResult, scopeResult) ->
                select(packageName)
                mutableState.value =
                    mutableState.value.copy(
                        saving = false,
                        hookStatus =
                            runCatching {
                                environment
                                    .hookGateway
                                    .status()
                            }.getOrDefault(
                                CapabilityStatus.Error,
                            ),
                        message =
                            when {
                                configResult is
                                    Outcome.Failure ->
                                    "reset_hook_write_failed"
                                scopeResult is
                                    Outcome.Success ->
                                    "reset_target_restart"
                                else ->
                                    "reset_hook_reload_unavailable"
                            },
                    )
                reloadApps()
            }.onFailure { error ->
                withContext(Dispatchers.IO) {
                    repository.save(
                        packageName,
                        previous,
                    )
                }
                select(packageName)
                mutableState.value =
                    mutableState.value.copy(
                        saving = false,
                        message =
                            error.message
                                ?: error.javaClass
                                    .simpleName,
                    )
                reloadApps()
            }
        }
    }

    fun retryApps() = reloadApps()

    private fun reloadApps() {
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) { repository.apps() }
                mutableState.value = mutableState.value.copy(
                    apps = apps, appLoadError = null,
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

    private fun validate(value: YParamOverrides): String? {
        value.densityDpi?.let {
            if (it !in 72..1000) return "validation_density"
        }
        value.widthPixels?.let {
            if (it < 100) return "validation_width"
        }
        value.heightPixels?.let {
            if (it < 100) return "validation_height"
        }
        value.latitude?.let {
            if (it !in -90.0..90.0) return "validation_latitude"
        }
        value.longitude?.let {
            if (it !in -180.0..180.0) return "validation_longitude"
        }
        if (
            value.locationMode != null &&
            (value.latitude == null || value.longitude == null)
        ) {
            return "validation_location"
        }
        return null
    }

    class Factory(
        private val environment: YParamEnvironment,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            YParamViewModel(environment) as T
    }
}
