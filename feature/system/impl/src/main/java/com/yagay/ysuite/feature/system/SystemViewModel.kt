package com.yagay.ysuite.feature.system

import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.LogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.permissions.api.PermissionCatalog
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.permissions.api.PermissionResult
import com.yagay.ysuite.platform.api.CapabilitySnapshot
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.presentation.YSuiteViewModel
import kotlinx.coroutines.launch

data class SystemUiState(
    val refreshing: Boolean = false,
    val capabilities: CapabilitySnapshot? = null,
    val permissions: List<PermissionRequirement> = emptyList(),
    val permissionResult: PermissionResult =
        PermissionResult(emptyMap()),
    val diagnostics: List<DiagnosticFinding> = emptyList(),
    val logs: List<LogRecord> = emptyList(),
)

class SystemViewModel(
    private val capabilityMonitor: PlatformCapabilityMonitor,
    private val diagnosticCenter: DiagnosticCenter,
    private val logStore: LogStore,
    private val logger: YSuiteLogger,
    private val permissionChecker: PermissionChecker,
    private val permissionCatalog: PermissionCatalog,
) : YSuiteViewModel<SystemUiState, Nothing>(
    initialState = SystemUiState(),
) {
    init {
        viewModelScope.launch {
            logStore.records.collect { records ->
                updateState {
                    it.copy(logs = records.takeLast(MAX_VISIBLE_LOGS))
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (state.value.refreshing) return

        updateState { it.copy(refreshing = true) }
        logger.debug(TAG, "System center refresh started")

        viewModelScope.launch {
            val permissions = permissionCatalog.requirements()
            val permissionResult =
                permissionChecker.snapshot(permissions)
            val capabilities = capabilityMonitor.probe()
            val diagnostics = diagnosticCenter.runAll()

            updateState {
                it.copy(
                    refreshing = false,
                    capabilities = capabilities,
                    permissions = permissions,
                    permissionResult = permissionResult,
                    diagnostics = diagnostics.findings,
                )
            }

            logger.debug(TAG, "System center refresh completed")
        }
    }

    fun applyPermissionResult(result: PermissionResult) {
        updateState { it.copy(permissionResult = result) }
        refresh()
    }

    fun clearLogs() {
        logStore.clear()
    }

    companion object {
        private const val TAG = "YSuite/System"
        private const val MAX_VISIBLE_LOGS = 50
    }
}
