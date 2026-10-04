package com.yagay.ysuite.feature.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteActionButton
import com.yagay.ysuite.designsystem.component.YSuiteDataRow
import com.yagay.ysuite.designsystem.component.YSuiteItemKind
import com.yagay.ysuite.designsystem.component.YSuiteMetricTile
import com.yagay.ysuite.designsystem.component.YSuitePanel
import com.yagay.ysuite.designsystem.component.YSuiteStatusPill
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.diagnostics.DiagnosticStatus
import com.yagay.ysuite.logging.api.LogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.permissions.api.PermissionCatalog
import com.yagay.ysuite.permissions.api.PermissionChecker
import com.yagay.ysuite.permissions.api.PermissionRequirement
import com.yagay.ysuite.permissions.api.PermissionResult
import com.yagay.ysuite.permissions.api.PermissionStatus
import com.yagay.ysuite.platform.api.CapabilityKind
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.PlatformCapabilityMonitor
import com.yagay.ysuite.ui.YSuiteDashboardScreen
import com.yagay.ysuite.ui.YSuiteWidthClass
import com.yagay.ysuite.ui.rememberYSuitePermissionRequester

@Composable
fun SystemFeatureScreen(
    capabilityMonitor: PlatformCapabilityMonitor,
    diagnosticCenter: DiagnosticCenter,
    logStore: LogStore,
    logger: YSuiteLogger,
    permissionChecker: PermissionChecker,
    permissionCatalog: PermissionCatalog,
) {
    val model: SystemViewModel = viewModel(
        factory = SystemViewModelFactory(
            capabilityMonitor = capabilityMonitor,
            diagnosticCenter = diagnosticCenter,
            logStore = logStore,
            logger = logger,
            permissionChecker = permissionChecker,
            permissionCatalog = permissionCatalog,
        ),
    )
    val state by model.state.collectAsStateWithLifecycle()
    val requester = rememberYSuitePermissionRequester(
        onResult = model::applyPermissionResult,
    )

    YSuiteDashboardScreen(
        title = stringResource(R.string.system_title),
        subtitle = stringResource(R.string.system_summary),
    ) { widthClass ->
        val rootStatus =
            state.capabilities?.get(CapabilityKind.Root)
                ?: CapabilityStatus.Unavailable
        val hookStatus =
            state.capabilities?.get(CapabilityKind.Hooks)
                ?: CapabilityStatus.Unavailable

        if (widthClass == YSuiteWidthClass.Compact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteMetricTile(
                    label = stringResource(R.string.system_root),
                    value = capabilityStatusText(rootStatus),
                    tone = capabilityStatusTone(rootStatus),
                    modifier = Modifier.fillMaxWidth(),
                )
                YSuiteMetricTile(
                    label = stringResource(R.string.system_hooks),
                    value = capabilityStatusText(hookStatus),
                    tone = capabilityStatusTone(hookStatus),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
            ) {
                YSuiteMetricTile(
                    label = stringResource(R.string.system_root),
                    value = capabilityStatusText(rootStatus),
                    tone = capabilityStatusTone(rootStatus),
                    modifier = Modifier.weight(1f),
                )
                YSuiteMetricTile(
                    label = stringResource(R.string.system_hooks),
                    value = capabilityStatusText(hookStatus),
                    tone = capabilityStatusTone(hookStatus),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        YSuiteActionButton(
            text =
                if (state.refreshing) {
                    stringResource(R.string.system_refreshing)
                } else {
                    stringResource(R.string.system_refresh)
                },
            onClick = model::refresh,
        )

        PermissionPanel(
            requirements = state.permissions,
            result = state.permissionResult,
            onRequest = requester::launch,
        )

        DiagnosticPanel(
            findings = state.diagnostics,
        )

        LogPanel(
            records = state.logs,
            onClear = model::clearLogs,
        )
    }
}

@Composable
private fun PermissionPanel(
    requirements: List<PermissionRequirement>,
    result: PermissionResult,
    onRequest: (List<PermissionRequirement>) -> Unit,
) {
    YSuitePanel(
        title = stringResource(R.string.system_permissions),
    ) {
        if (requirements.isEmpty()) {
            YSuiteDataRow(
                title = stringResource(R.string.system_permissions_empty),
                kind = YSuiteItemKind.Permission,
            )
        } else {
            requirements.forEach { requirement ->
                val status =
                    result.statuses[requirement.permission]
                        ?: PermissionStatus.Denied
                YSuiteDataRow(
                    title = requirement.permission.substringAfterLast('.'),
                    kind = YSuiteItemKind.Permission,
                    trailing = {
                        YSuiteStatusPill(
                            text = permissionStatusText(status),
                            tone = permissionStatusTone(status),
                        )
                    },
                )
            }

            val denied = requirements.filter {
                result.statuses[it.permission] != PermissionStatus.Granted
            }
            if (denied.isNotEmpty()) {
                YSuiteActionButton(
                    text = stringResource(
                        R.string.system_request_permissions,
                    ),
                    onClick = { onRequest(denied) },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(
    findings: List<DiagnosticFinding>,
) {
    YSuitePanel(
        title = stringResource(R.string.system_diagnostics),
    ) {
        if (findings.isEmpty()) {
            YSuiteDataRow(
                title = stringResource(R.string.system_diagnostics_empty),
                kind = YSuiteItemKind.Info,
            )
        } else {
            findings.forEach { finding ->
                YSuiteDataRow(
                    title = finding.id,
                    subtitle = finding.owner,
                    kind = YSuiteItemKind.Info,
                    trailing = {
                        YSuiteStatusPill(
                            text = diagnosticStatusText(finding.status),
                            tone = diagnosticStatusTone(finding.status),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LogPanel(
    records: List<com.yagay.ysuite.logging.api.LogRecord>,
    onClear: () -> Unit,
) {
    YSuitePanel(
        title = stringResource(R.string.system_logs),
    ) {
        if (records.isEmpty()) {
            YSuiteDataRow(
                title = stringResource(R.string.system_logs_empty),
                kind = YSuiteItemKind.Log,
            )
        } else {
            records.asReversed().take(20).forEach { record ->
                YSuiteDataRow(
                    title = record.level.name + " · " + record.tag,
                    subtitle = record.message,
                    kind = YSuiteItemKind.Log,
                )
            }
            YSuiteActionButton(
                text = stringResource(R.string.system_clear_logs),
                onClick = onClear,
            )
        }
    }
}

@Composable
private fun capabilityStatusText(
    status: CapabilityStatus,
): String =
    when (status) {
        CapabilityStatus.Available ->
            stringResource(R.string.system_status_available)
        CapabilityStatus.Unavailable ->
            stringResource(R.string.system_status_unavailable)
        CapabilityStatus.PermissionRequired ->
            stringResource(R.string.system_status_permission)
        CapabilityStatus.Error ->
            stringResource(R.string.system_status_error)
    }

private fun capabilityStatusTone(
    status: CapabilityStatus,
): YSuiteStatusTone =
    when (status) {
        CapabilityStatus.Available ->
            YSuiteStatusTone.Positive
        CapabilityStatus.PermissionRequired ->
            YSuiteStatusTone.Warning
        CapabilityStatus.Unavailable ->
            YSuiteStatusTone.Neutral
        CapabilityStatus.Error ->
            YSuiteStatusTone.Error
    }

@Composable
private fun permissionStatusText(
    status: PermissionStatus,
): String =
    when (status) {
        PermissionStatus.Granted ->
            stringResource(R.string.system_status_granted)
        PermissionStatus.Denied ->
            stringResource(R.string.system_status_denied)
    }

private fun permissionStatusTone(
    status: PermissionStatus,
): YSuiteStatusTone =
    when (status) {
        PermissionStatus.Granted -> YSuiteStatusTone.Positive
        PermissionStatus.Denied -> YSuiteStatusTone.Warning
    }

@Composable
private fun diagnosticStatusText(
    status: DiagnosticStatus,
): String =
    when (status) {
        DiagnosticStatus.Pass ->
            stringResource(R.string.system_status_pass)
        DiagnosticStatus.Warning ->
            stringResource(R.string.system_status_warning)
        DiagnosticStatus.Failure ->
            stringResource(R.string.system_status_error)
        DiagnosticStatus.Unknown ->
            stringResource(R.string.system_status_unknown)
    }

private fun diagnosticStatusTone(
    status: DiagnosticStatus,
): YSuiteStatusTone =
    when (status) {
        DiagnosticStatus.Pass -> YSuiteStatusTone.Positive
        DiagnosticStatus.Warning -> YSuiteStatusTone.Warning
        DiagnosticStatus.Failure -> YSuiteStatusTone.Error
        DiagnosticStatus.Unknown -> YSuiteStatusTone.Neutral
    }

private class SystemViewModelFactory(
    private val capabilityMonitor: PlatformCapabilityMonitor,
    private val diagnosticCenter: DiagnosticCenter,
    private val logStore: LogStore,
    private val logger: YSuiteLogger,
    private val permissionChecker: PermissionChecker,
    private val permissionCatalog: PermissionCatalog,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        SystemViewModel(
            capabilityMonitor = capabilityMonitor,
            diagnosticCenter = diagnosticCenter,
            logStore = logStore,
            logger = logger,
            permissionChecker = permissionChecker,
            permissionCatalog = permissionCatalog,
        ) as T
}
