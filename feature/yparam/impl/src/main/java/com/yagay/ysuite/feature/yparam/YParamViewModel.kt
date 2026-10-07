package com.yagay.ysuite.feature.yparam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yparam.api.YParamAppSummary
import com.yagay.ysuite.feature.yparam.api.YParamDefaults
import com.yagay.ysuite.feature.yparam.api.YParamOverrides
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.Dispatchers
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

    init {
        reloadApps()
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                hookStatus =
                    runCatching {
                        environment.hookGateway.status()
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
        if (packageName == null) {
            mutableState.value = mutableState.value.copy(
                selectedPackage = null,
                draft = YParamOverrides(),
                defaults = null,
                baseline = null,
                diagnostics = null,
                message = null,
            )
            return
        }
        val value = repository.read(packageName)
        val defaults = repository.defaults(packageName)
        val baseline =
            repository.baseline(packageName)
        val diagnostics =
            runCatching {
                repository.diagnostics(
                    packageName,
                )
            }.getOrNull()
        mutableState.value = mutableState.value.copy(
            selectedPackage = packageName,
            draft = value,
            defaults = defaults,
            baseline = baseline,
            diagnostics = diagnostics,
            message = null,
        )
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
                val configResult =
                    environment.hookGateway
                        .writeConfig(
                            group = "yparam",
                            key =
                                "app." +
                                    packageName,
                            value = payload,
                        )
                val scopeResult =
                    environment.hookGateway
                        .reload(
                            setOf(packageName),
                        )
                configResult to scopeResult
            }.onSuccess {
                    (configResult, scopeResult) ->
                mutableState.value = mutableState.value.copy(
                    saving = false,
                    hookStatus =
                        runCatching {
                            environment.hookGateway.status()
                        }.getOrDefault(CapabilityStatus.Error),
                    message =
                        if (
                            configResult is
                                Outcome.Success &&
                            scopeResult is
                                Outcome.Success
                        ) {
                            "saved"
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
        val packageName = mutableState.value.selectedPackage ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.reset(packageName)
                }
                environment.hookGateway
                    .writeConfig(
                        group = "yparam",
                        key =
                            "app." +
                                packageName,
                        value = null,
                    )
                environment.hookGateway
                    .reload(
                        setOf(packageName),
                    )
            }
            select(packageName)
            reloadApps()
        }
    }

    private fun reloadApps() {
        viewModelScope.launch {
            val apps =
                withContext(Dispatchers.IO) {
                    repository.apps()
                }
            mutableState.value = mutableState.value.copy(apps = apps)
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
