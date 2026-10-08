package com.yagay.ysuite.feature.ypower

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ypower.api.YPowerAppSummary
import com.yagay.ysuite.feature.ypower.api.YPowerApplyResult
import com.yagay.ysuite.feature.ypower.api.YPowerFinding
import com.yagay.ysuite.feature.ypower.api.YPowerProfile
import com.yagay.ysuite.platform.api.CapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YPowerAppFilter {
    All,
    Enabled,
    Recommended,
    User,
    System,
}

data class YPowerUiState(
    val apps: List<YPowerAppSummary> = emptyList(),
    val appLoadError: String? = null,
    val query: String = "",
    val filter: YPowerAppFilter = YPowerAppFilter.All,
    val selectedPackage: String? = null,
    val draft: YPowerProfile? = null,
    val rootStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val hookStatus: CapabilityStatus =
        CapabilityStatus.Unavailable,
    val applying: Boolean = false,
    val diagnosing: Boolean = false,
    val diagnosticSessionActive: Boolean = false,
    val diagnosticSessionId: String = "",
    val diagnosticLevel:
        YPowerDiagnosticLevel =
        YPowerDiagnosticLevel.Standard,
    val reportUri: String? = null,
    val applyResult: YPowerApplyResult? = null,
    val findings: List<YPowerFinding> = emptyList(),
    val statusToken: String? = null,
)

class YPowerViewModel(
    private val environment: YPowerEnvironment,
) : ViewModel() {
    private val repository =
        YPowerRepository(
            environment.applicationContext,
            environment.rootGateway,
            environment.hookGateway,
            environment.logger,
        )
    private val mutableState =
        MutableStateFlow(YPowerUiState())
    val state: StateFlow<YPowerUiState> =
        mutableState.asStateFlow()

    init {
        reloadApps()
        refreshRuntime()
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(query = value)
    }

    fun setFilter(value: YPowerAppFilter) {
        mutableState.value =
            mutableState.value.copy(filter = value)
    }

    fun visibleApps(): List<YPowerAppSummary> {
        val state = mutableState.value
        val needle = state.query.trim()
        return state.apps.filter { app ->
            val filterMatch =
                when (state.filter) {
                    YPowerAppFilter.All -> true
                    YPowerAppFilter.Enabled ->
                        app.enabled
                    YPowerAppFilter.Recommended ->
                        app.recommended
                    YPowerAppFilter.User ->
                        !app.system
                    YPowerAppFilter.System ->
                        app.system
                }
            val searchMatch =
                needle.isBlank() ||
                    app.label.contains(
                        needle,
                        ignoreCase = true,
                    ) ||
                    app.packageName.contains(
                        needle,
                        ignoreCase = true,
                    )
            filterMatch && searchMatch
        }
    }

    fun select(packageName: String?) {
        mutableState.value =
            if (packageName == null) {
                mutableState.value.copy(
                    selectedPackage = null,
                    draft = null,
                    applyResult = null,
                    findings = emptyList(),
                    statusToken = null,
                    reportUri = null,
                )
            } else {
                val session =
                    repository
                        .diagnosticSessionState(
                            packageName,
                        )
                mutableState.value.copy(
                    selectedPackage = packageName,
                    draft = repository.load(packageName),
                    diagnosticSessionActive =
                        session.active,
                    diagnosticSessionId =
                        session.sessionId,
                    diagnosticLevel =
                        session.level,
                    applyResult = null,
                    findings = emptyList(),
                    statusToken = null,
                )
            }
    }

    fun update(
        transform: (YPowerProfile) -> YPowerProfile,
    ) {
        val draft = mutableState.value.draft ?: return
        mutableState.value =
            mutableState.value.copy(
                draft = transform(draft),
                statusToken = null,
            )
    }

    fun applyRecommended() {
        update {
            it.copy(
                enabled = true,
                dozeWhitelist = true,
                backgroundOps = true,
                standbyActive = true,
                backgroundData = true,
                tracePackageScan = true,
                traceFiles = true,
                traceCommands = true,
                traceProperties = true,
                tracePermissions = true,
                traceDebugger = true,
                traceExceptions = true,
                traceSecurityApis = true,
                traceNative = true,
            )
        }
    }

    fun saveAndApply() {
        val profile = mutableState.value.draft ?: return
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(applying = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.save(profile)
                    repository.apply(profile)
                }
            }.onSuccess { result ->
                mutableState.value =
                    mutableState.value.copy(
                        applying = false,
                        applyResult = result,
                        statusToken =
                            if (result.success) {
                                "applied"
                            } else {
                                "apply_failed"
                            },
                    )
                reloadApps()
                refreshRuntime()
            }.onFailure {
                environment.logger.error(
                    "YSuite/YPower",
                    "Apply failed",
                    it,
                )
                mutableState.value =
                    mutableState.value.copy(
                        applying = false,
                        statusToken = "apply_failed",
                    )
            }
        }
    }

    fun startDiagnosticSession(
        level: YPowerDiagnosticLevel,
    ) {
        val packageName =
            mutableState.value
                .selectedPackage
                ?: return
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    diagnosing = true,
                    findings = emptyList(),
                    reportUri = null,
                )
            runCatching {
                withContext(
                    Dispatchers.IO,
                ) {
                    repository
                        .startDiagnosticSession(
                            packageName,
                            level,
                        )
                }
            }.onSuccess {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        diagnosticSessionActive =
                            true,
                        diagnosticSessionId =
                            it.sessionId,
                        diagnosticLevel =
                            it.level,
                    )
            }.onFailure {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        statusToken =
                            "diagnostic_failed",
                    )
            }
        }
    }

    fun launchDiagnosticTarget() {
        val packageName =
            mutableState.value
                .selectedPackage
                ?: return
        if (
            !repository
                .launchDiagnosticTarget(
                    packageName,
                )
        ) {
            mutableState.value =
                mutableState.value.copy(
                    statusToken =
                        "launch_failed",
                )
        }
    }

    fun finishDiagnosticSession() {
        val packageName =
            mutableState.value
                .selectedPackage
                ?: return
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    diagnosing = true,
                )
            runCatching {
                withContext(
                    Dispatchers.IO,
                ) {
                    repository
                        .finishDiagnosticSession(
                            packageName,
                        )
                }
            }.onSuccess {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        diagnosticSessionActive =
                            false,
                        diagnosticSessionId = "",
                        findings =
                            it.findings,
                        reportUri =
                            it.reportUri,
                    )
            }.onFailure {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        statusToken =
                            "diagnostic_failed",
                    )
            }
        }
    }

    fun diagnose() {
        val packageName =
            mutableState.value.selectedPackage ?: return
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    diagnosing = true,
                    findings = emptyList(),
                )
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.diagnose(packageName)
                }
            }.onSuccess {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        findings = it,
                    )
            }.onFailure {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosing = false,
                        statusToken = "diagnostic_failed",
                    )
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

    private fun refreshRuntime() {
        viewModelScope.launch {
            mutableState.value =
                mutableState.value.copy(
                    rootStatus =
                        runCatching {
                            repository.rootStatus()
                        }.getOrDefault(
                            CapabilityStatus.Error,
                        ),
                    hookStatus =
                        runCatching {
                            repository.hookStatus()
                        }.getOrDefault(
                            CapabilityStatus.Error,
                        ),
                )
        }
    }

    class Factory(
        private val environment: YPowerEnvironment,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YPowerViewModel(environment) as T
    }
}
