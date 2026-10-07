package com.yagay.ysuite.feature.system

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.logging.api.LogCollector
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogQuery
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.LogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.logging.api.filtered
import com.yagay.ysuite.permissions.api.PermissionCatalog
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.permissions.api.PermissionResult
import com.yagay.ysuite.platform.api.CapabilitySnapshot
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.platform.api.PlatformServices
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SystemPage {
    Overview,
    Logs,
}

data class SystemUiState(
    val page: SystemPage = SystemPage.Overview,
    val refreshing: Boolean = false,
    val logcatRefreshing: Boolean = false,
    val capabilities: CapabilitySnapshot? = null,
    val permissions: List<PermissionRequirement> =
        emptyList(),
    val permissionResult: PermissionResult =
        PermissionResult(emptyMap()),
    val diagnostics: List<DiagnosticFinding> =
        emptyList(),
    val appLogs: List<LogRecord> = emptyList(),
    val logcatLogs: List<LogRecord> = emptyList(),
    val logQuery: String = "",
    val logLevel: LogLevel? = null,
) {
    val visibleLogs: List<LogRecord>
        get() =
            (appLogs + logcatLogs)
                .sortedBy(LogRecord::timestampMillis)
                .filtered(
                    LogQuery(
                        text = logQuery,
                        levels =
                            logLevel
                                ?.let(::setOf)
                                .orEmpty(),
                        limit = MAX_VISIBLE_LOGS,
                    ),
                )

    companion object {
        private const val MAX_VISIBLE_LOGS = 1_000
    }
}

private data class SystemRefreshSnapshot(
    val capabilities: CapabilitySnapshot,
    val permissions: List<PermissionRequirement>,
    val permissionResult: PermissionResult,
    val diagnostics: List<DiagnosticFinding>,
)

class SystemViewModel(
    private val capabilityMonitor:
        PlatformCapabilityMonitor,
    private val platformServices:
        PlatformServices,
    private val diagnosticCenter: DiagnosticCenter,
    private val logStore: LogStore,
    private val logCollector: LogCollector,
    private val logger: YSuiteLogger,
    private val permissionChecker:
        PermissionChecker,
    private val permissionCatalog:
        PermissionCatalog,
) : YSuiteViewModel<SystemUiState, Nothing>(
    initialState = SystemUiState(),
) {
    init {
        viewModelScope.launch {
            logStore.records.collect { records ->
                updateState {
                    it.copy(
                        appLogs =
                            records.takeLast(
                                MAX_APP_LOGS,
                            ),
                    )
                }
            }
        }
        refreshInternal(
            includeDiagnostics = false,
        )
    }

    fun refresh() {
        refreshInternal(
            includeDiagnostics = true,
        )
    }

    private fun refreshInternal(
        includeDiagnostics: Boolean,
    ) {
        if (state.value.refreshing) return

        updateState {
            it.copy(refreshing = true)
        }
        logger.debug(
            TAG,
            "System center refresh started",
        )

        viewModelScope.launch {
            try {
                val snapshot =
                    withContext(Dispatchers.IO) {
                        val permissions =
                            permissionCatalog
                                .requirements()
                        val permissionResult =
                            permissionChecker
                                .snapshot(permissions)
                        val capabilities =
                            capabilityMonitor.probe()
                        val diagnostics =
                            if (includeDiagnostics) {
                                diagnosticCenter
                                    .runAll()
                                    .findings
                            } else {
                                emptyList()
                            }
                        SystemRefreshSnapshot(
                            capabilities =
                                capabilities,
                            permissions =
                                permissions,
                            permissionResult =
                                permissionResult,
                            diagnostics =
                                diagnostics,
                        )
                    }

                updateState {
                    it.copy(
                        capabilities =
                            snapshot.capabilities,
                        permissions =
                            snapshot.permissions,
                        permissionResult =
                            snapshot.permissionResult,
                        diagnostics =
                            if (
                                includeDiagnostics
                            ) {
                                snapshot.diagnostics
                            } else {
                                it.diagnostics
                            },
                    )
                }

                logger.debug(
                    TAG,
                    "System center refresh completed",
                )
            } catch (error: Throwable) {
                logger.error(
                    TAG,
                    "System center refresh failed",
                    error,
                )
            } finally {
                updateState {
                    it.copy(
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun applyPermissionResult(
        result: PermissionResult,
    ) {
        updateState {
            it.copy(
                permissionResult = result,
            )
        }
        refresh()
    }

    fun requestShizukuPermission() {
        val requested =
            platformServices.shizuku
                .requestPermission()
        logger.debug(
            TAG,
            if (requested) {
                "Shizuku permission requested"
            } else {
                "Shizuku permission request unavailable"
            },
        )
        refresh()
    }

    fun showLogs() {
        updateState {
            it.copy(page = SystemPage.Logs)
        }
        refreshLogcat()
    }

    fun backToOverview() {
        updateState {
            it.copy(
                page = SystemPage.Overview,
            )
        }
    }

    fun setLogQuery(value: String) {
        updateState {
            it.copy(logQuery = value)
        }
    }

    fun setLogLevel(
        value: LogLevel?,
    ) {
        updateState {
            it.copy(logLevel = value)
        }
    }

    fun refreshLogcat() {
        if (state.value.logcatRefreshing) {
            return
        }
        updateState {
            it.copy(
                logcatRefreshing = true,
            )
        }
        viewModelScope.launch {
            try {
                val records =
                    logCollector.collect(
                        MAX_LOGCAT_LINES,
                    )
                updateState {
                    it.copy(
                        logcatLogs = records,
                    )
                }
            } catch (error: Throwable) {
                logger.error(
                    TAG,
                    "Logcat collection failed",
                    error,
                )
            } finally {
                updateState {
                    it.copy(
                        logcatRefreshing = false,
                    )
                }
            }
        }
    }

    fun clearLogs() {
        logStore.clear()
        updateState {
            it.copy(
                logcatLogs = emptyList(),
            )
        }
    }

    companion object {
        private const val TAG =
            "YSuite/System"
        private const val MAX_APP_LOGS = 500
        private const val MAX_LOGCAT_LINES = 500
    }
}
