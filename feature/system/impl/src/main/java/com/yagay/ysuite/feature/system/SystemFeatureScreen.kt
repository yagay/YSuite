package com.yagay.ysuite.feature.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteFilterBar
import com.yagay.ysuite.designsystem.component.YSuiteFilterOption
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSearchField
import com.yagay.ysuite.designsystem.component.YSuiteSecondaryButton
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.diagnostics.DiagnosticCenter
import com.yagay.ysuite.diagnostics.DiagnosticFinding
import com.yagay.ysuite.diagnostics.DiagnosticStatus
import com.yagay.ysuite.logging.api.LogCollector
import com.yagay.ysuite.logging.api.LogLevel
import com.yagay.ysuite.logging.api.LogRecord
import com.yagay.ysuite.logging.api.LogSource
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
import com.yagay.ysuite.platform.api.PlatformServices
import com.yagay.ysuite.productui.dashboard.NiaDashboardSurface
import com.yagay.ysuite.productui.logs.LogcatReaderWorkspace
import com.yagay.ysuite.ui.YSuiteBackNavigationButton
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton
import com.yagay.ysuite.ui.rememberYSuitePermissionRequester

@Composable
fun SystemFeatureScreen(
    capabilityMonitor:
        PlatformCapabilityMonitor,
    platformServices: PlatformServices,
    diagnosticCenter: DiagnosticCenter,
    logStore: LogStore,
    logCollector: LogCollector,
    logger: YSuiteLogger,
    permissionChecker: PermissionChecker,
    permissionCatalog: PermissionCatalog,
) {
    val model: SystemViewModel =
        viewModel(
            factory =
                SystemViewModelFactory(
                    capabilityMonitor =
                        capabilityMonitor,
                    platformServices =
                        platformServices,
                    diagnosticCenter =
                        diagnosticCenter,
                    logStore = logStore,
                    logCollector =
                        logCollector,
                    logger = logger,
                    permissionChecker =
                        permissionChecker,
                    permissionCatalog =
                        permissionCatalog,
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()
    val requester =
        rememberYSuitePermissionRequester(
            onResult =
                model::applyPermissionResult,
        )

    YSuiteFeatureBackHandler(
        enabled = state.page == SystemPage.Logs,
    ) {
        model.backToOverview()
    }

    when (state.page) {
        SystemPage.Overview ->
            OverviewSurface(
                state = state,
                onRefresh = model::refresh,
                onRequestPermissions =
                    requester::launch,
                onRequestShizuku =
                    model::requestShizukuPermission,
                onShowLogs =
                    model::showLogs,
            )
        SystemPage.Logs ->
            LogsSurface(
                state = state,
                onBack =
                    model::backToOverview,
                onSearch =
                    model::setLogQuery,
                onLevel =
                    model::setLogLevel,
                onRefresh =
                    model::refreshLogcat,
                onClear =
                    model::clearLogs,
            )
    }
}

@Composable
private fun OverviewSurface(
    state: SystemUiState,
    onRefresh: () -> Unit,
    onRequestPermissions:
        (List<PermissionRequirement>) -> Unit,
    onRequestShizuku: () -> Unit,
    onShowLogs: () -> Unit,
) {
    NiaDashboardSurface(
        title =
            stringResource(
                R.string.system_title,
            ),
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
    ) { _ ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState(),
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    YSuiteSpacing.Large,
                ),
        ) {
            YSuiteSection(
                title =
                    stringResource(
                        R.string.system_platform,
                    ),
            ) {
                CapabilityRow(
                    label =
                        stringResource(
                            R.string.system_normal,
                        ),
                    status =
                        state.capabilities?.get(
                            CapabilityKind.Normal,
                        ),
                )
                CapabilityRow(
                    label =
                        stringResource(
                            R.string.system_shizuku,
                        ),
                    status =
                        state.capabilities?.get(
                            CapabilityKind.Shizuku,
                        ),
                )
                if (
                    state.capabilities?.get(
                        CapabilityKind.Shizuku,
                    ) ==
                    CapabilityStatus
                        .PermissionRequired
                ) {
                    YSuiteSecondaryButton(
                        text =
                            stringResource(
                                R.string
                                    .system_request_shizuku,
                            ),
                        onClick =
                            onRequestShizuku,
                    )
                }
                CapabilityRow(
                    label =
                        stringResource(
                            R.string.system_root,
                        ),
                    status =
                        state.capabilities?.get(
                            CapabilityKind.Root,
                        ),
                )
                CapabilityRow(
                    label =
                        stringResource(
                            R.string.system_hooks,
                        ),
                    status =
                        state.capabilities?.get(
                            CapabilityKind.Hooks,
                        ),
                )
                YSuiteSecondaryButton(
                    text =
                        if (state.refreshing) {
                            stringResource(
                                R.string
                                    .system_refreshing,
                            )
                        } else {
                            stringResource(
                                R.string
                                    .system_refresh,
                            )
                        },
                    onClick = onRefresh,
                )
            }

            PermissionSection(
                requirements =
                    state.permissions,
                result =
                    state.permissionResult,
                onRequest =
                    onRequestPermissions,
            )

            DiagnosticSection(
                findings =
                    state.diagnostics,
            )

            YSuiteSection(
                title =
                    stringResource(
                        R.string.system_logs,
                    ),
            ) {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string
                                .system_app_log_count,
                            state.appLogs.size,
                        ),
                    subtitle =
                        stringResource(
                            R.string
                                .system_logs_summary,
                        ),
                )
                YSuiteSecondaryButton(
                    text =
                        stringResource(
                            R.string
                                .system_open_logs,
                        ),
                    onClick = onShowLogs,
                )
            }
        }
    }
}

@Composable
private fun LogsSurface(
    state: SystemUiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onLevel: (LogLevel?) -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    LogcatReaderWorkspace(
        title =
            stringResource(
                R.string.system_logs,
            ),
        navigationIcon = {
            YSuiteBackNavigationButton(
                onClick = onBack,
            )
        },
        search = {
            YSuiteSearchField(
                value = state.logQuery,
                onValueChange = onSearch,
                label =
                    stringResource(
                        R.string
                            .system_log_search,
                    ),
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                    ),
            )
        },
        filters = {
            Column(
                modifier =
                    Modifier.padding(
                        horizontal =
                            YSuiteSpacing.Medium,
                        vertical =
                            YSuiteSpacing.Small,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        YSuiteSpacing.Small,
                    ),
            ) {
                YSuiteFilterBar(
                    options =
                        logFilterOptions(),
                    selectedId =
                        state.logLevel
                            ?.name
                            ?: "All",
                    onSelected = { id ->
                        onLevel(
                            LogLevel.entries
                                .firstOrNull {
                                    it.name == id
                                },
                        )
                    },
                )
                YSuiteSecondaryButton(
                    text =
                        if (
                            state.logcatRefreshing
                        ) {
                            stringResource(
                                R.string
                                    .system_logcat_refreshing,
                            )
                        } else {
                            stringResource(
                                R.string
                                    .system_refresh_logcat,
                            )
                        },
                    onClick = onRefresh,
                )
                YSuiteSecondaryButton(
                    text =
                        stringResource(
                            R.string
                                .system_clear_logs,
                        ),
                    onClick = onClear,
                )
            }
        },
        content = {
            if (state.visibleLogs.isEmpty()) {
                Column(
                    modifier =
                        Modifier.padding(
                            YSuiteSpacing.Medium,
                        ),
                ) {
                    YSuiteListItem(
                        title =
                            stringResource(
                                R.string
                                    .system_logs_empty,
                            ),
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier.fillMaxSize(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            YSuiteSpacing.Small,
                        ),
                ) {
                    items(
                        items =
                            state.visibleLogs
                                .asReversed(),
                        key = {
                            it.timestampMillis
                                .toString() +
                                it.tag +
                                it.message
                        },
                    ) { record ->
                        LogRow(record)
                    }
                }
            }
        },
    )
}

@Composable
private fun logFilterOptions():
    List<YSuiteFilterOption> =
    listOf(
        YSuiteFilterOption(
            id = "All",
            label =
                stringResource(
                    R.string
                        .system_log_level_all,
                ),
        ),
        YSuiteFilterOption(
            id = LogLevel.Debug.name,
            label =
                stringResource(
                    R.string
                        .system_log_level_debug,
                ),
        ),
        YSuiteFilterOption(
            id = LogLevel.Info.name,
            label =
                stringResource(
                    R.string
                        .system_log_level_info,
                ),
        ),
        YSuiteFilterOption(
            id = LogLevel.Warning.name,
            label =
                stringResource(
                    R.string
                        .system_log_level_warning,
                ),
        ),
        YSuiteFilterOption(
            id = LogLevel.Error.name,
            label =
                stringResource(
                    R.string
                        .system_log_level_error,
                ),
        ),
    )

@Composable
private fun LogRow(
    record: LogRecord,
) {
    val source =
        when (record.source) {
            LogSource.App ->
                stringResource(
                    R.string
                        .system_log_source_app,
                )
            LogSource.Logcat ->
                stringResource(
                    R.string
                        .system_log_source_logcat,
                )
        }
    YSuiteListItem(
        title =
            record.level.name +
                " · " +
                record.tag,
        subtitle =
            source +
                " · " +
                record.message,
        modifier =
            Modifier.padding(
                horizontal =
                    YSuiteSpacing.Medium,
            ),
    )
}

@Composable
private fun CapabilityRow(
    label: String,
    status: CapabilityStatus?,
) {
    val resolved =
        status
            ?: CapabilityStatus.Unavailable
    YSuiteListItem(
        title = label,
        trailing = {
            YSuiteStatusBadge(
                text =
                    capabilityStatusText(
                        resolved,
                    ),
                tone =
                    capabilityStatusTone(
                        resolved,
                    ),
            )
        },
    )
}

@Composable
private fun PermissionSection(
    requirements:
        List<PermissionRequirement>,
    result: PermissionResult,
    onRequest:
        (List<PermissionRequirement>) -> Unit,
) {
    YSuiteSection(
        title =
            stringResource(
                R.string.system_permissions,
            ),
    ) {
        if (requirements.isEmpty()) {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .system_permissions_empty,
                    ),
            )
        } else {
            requirements.forEach {
                requirement ->
                val status =
                    result.statuses[
                        requirement.permission
                    ] ?: PermissionStatus.Denied
                YSuiteListItem(
                    title =
                        requirement.permission
                            .substringAfterLast(
                                '.',
                            ),
                    trailing = {
                        YSuiteStatusBadge(
                            text =
                                permissionStatusText(
                                    status,
                                ),
                            tone =
                                permissionStatusTone(
                                    status,
                                ),
                        )
                    },
                )
            }

            val denied =
                requirements.filter {
                    result.statuses[
                        it.permission
                    ] !=
                        PermissionStatus.Granted
                }
            if (denied.isNotEmpty()) {
                YSuiteSecondaryButton(
                    text =
                        stringResource(
                            R.string
                                .system_request_permissions,
                        ),
                    onClick = {
                        onRequest(denied)
                    },
                )
            }
        }
    }
}

@Composable
private fun DiagnosticSection(
    findings: List<DiagnosticFinding>,
) {
    YSuiteSection(
        title =
            stringResource(
                R.string.system_diagnostics,
            ),
    ) {
        if (findings.isEmpty()) {
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .system_diagnostics_empty,
                    ),
            )
        } else {
            val failures =
                findings.count {
                    it.status ==
                        DiagnosticStatus.Failure
                }
            val warnings =
                findings.count {
                    it.status ==
                        DiagnosticStatus.Warning
                }
            YSuiteListItem(
                title =
                    stringResource(
                        R.string
                            .system_diagnostic_summary,
                        findings.size,
                        failures,
                        warnings,
                    ),
            )
            findings.forEach { finding ->
                val recommendation =
                    diagnosticRecommendationText(
                        finding.recommendation,
                    )
                val subtitle =
                    buildString {
                        append(
                            diagnosticSummaryText(
                                finding,
                            ),
                        )
                        recommendation?.let {
                            append("\n")
                            append(it)
                        }
                    }
                YSuiteListItem(
                    title =
                        diagnosticTitle(
                            finding,
                        ),
                    subtitle = subtitle,
                    trailing = {
                        YSuiteStatusBadge(
                            text =
                                diagnosticStatusText(
                                    finding.status,
                                ),
                            tone =
                                diagnosticStatusTone(
                                    finding.status,
                                ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun diagnosticTitle(
    finding: DiagnosticFinding,
): String =
    when (finding.category) {
        "capability" ->
            when (
                finding.metadata["kind"]
                    ?.let {
                        runCatching {
                            CapabilityKind.valueOf(it)
                        }.getOrNull()
                    }
            ) {
                CapabilityKind.Normal ->
                    stringResource(
                        R.string.system_normal,
                    )
                CapabilityKind.Shizuku ->
                    stringResource(
                        R.string.system_shizuku,
                    )
                CapabilityKind.Root ->
                    stringResource(
                        R.string.system_root,
                    )
                CapabilityKind.Hooks ->
                    stringResource(
                        R.string.system_hooks,
                    )
                null ->
                    stringResource(
                        R.string.system_platform,
                    )
            }
        "permissions" ->
            stringResource(
                R.string.system_permissions,
            )
        "filesystem" ->
            stringResource(
                R.string
                    .system_diagnostic_provider_title,
                finding.metadata["providerId"]
                    .orEmpty(),
            )
        else ->
            finding.id
    }

@Composable
private fun diagnosticSummaryText(
    finding: DiagnosticFinding,
): String =
    when (finding.category) {
        "capability" ->
            diagnosticStatusText(
                finding.status,
            )
        "permissions" ->
            stringResource(
                R.string
                    .system_diagnostic_permissions_summary,
                finding.metadata["declaredCount"]
                    ?.toIntOrNull()
                    ?: 0,
                finding.metadata["deniedCount"]
                    ?.toIntOrNull()
                    ?: 0,
            )
        "filesystem" ->
            stringResource(
                R.string
                    .system_diagnostic_provider_summary,
                finding.metadata["capabilityCount"]
                    ?.toIntOrNull()
                    ?: 0,
            )
        else ->
            finding.summary
    }

@Composable
private fun diagnosticRecommendationText(
    key: String?,
): String? =
    when (key) {
        "review_permissions" ->
            stringResource(
                R.string
                    .system_diagnostic_recommend_review_permissions,
            )
        "grant_shizuku" ->
            stringResource(
                R.string
                    .system_diagnostic_recommend_grant_shizuku,
            )
        "start_shizuku" ->
            stringResource(
                R.string
                    .system_diagnostic_recommend_start_shizuku,
            )
        "grant_root" ->
            stringResource(
                R.string
                    .system_diagnostic_recommend_grant_root,
            )
        "enable_hooks" ->
            stringResource(
                R.string
                    .system_diagnostic_recommend_enable_hooks,
            )
        null, "" ->
            null
        else ->
            key
    }

@Composable
private fun capabilityStatusText(
    status: CapabilityStatus,
): String =
    when (status) {
        CapabilityStatus.Available ->
            stringResource(
                R.string
                    .system_status_available,
            )
        CapabilityStatus.Unavailable ->
            stringResource(
                R.string
                    .system_status_unavailable,
            )
        CapabilityStatus.PermissionRequired ->
            stringResource(
                R.string
                    .system_status_permission,
            )
        CapabilityStatus.Error ->
            stringResource(
                R.string
                    .system_status_error,
            )
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
            stringResource(
                R.string
                    .system_status_granted,
            )
        PermissionStatus.Denied ->
            stringResource(
                R.string
                    .system_status_denied,
            )
    }

private fun permissionStatusTone(
    status: PermissionStatus,
): YSuiteStatusTone =
    when (status) {
        PermissionStatus.Granted ->
            YSuiteStatusTone.Positive
        PermissionStatus.Denied ->
            YSuiteStatusTone.Warning
    }

@Composable
private fun diagnosticStatusText(
    status: DiagnosticStatus,
): String =
    when (status) {
        DiagnosticStatus.Pass ->
            stringResource(
                R.string.system_status_pass,
            )
        DiagnosticStatus.Warning ->
            stringResource(
                R.string
                    .system_status_warning,
            )
        DiagnosticStatus.Failure ->
            stringResource(
                R.string
                    .system_status_error,
            )
        DiagnosticStatus.Unknown ->
            stringResource(
                R.string
                    .system_status_unknown,
            )
    }

private fun diagnosticStatusTone(
    status: DiagnosticStatus,
): YSuiteStatusTone =
    when (status) {
        DiagnosticStatus.Pass ->
            YSuiteStatusTone.Positive
        DiagnosticStatus.Warning ->
            YSuiteStatusTone.Warning
        DiagnosticStatus.Failure ->
            YSuiteStatusTone.Error
        DiagnosticStatus.Unknown ->
            YSuiteStatusTone.Neutral
    }

private class SystemViewModelFactory(
    private val capabilityMonitor:
        PlatformCapabilityMonitor,
    private val platformServices:
        PlatformServices,
    private val diagnosticCenter:
        DiagnosticCenter,
    private val logStore: LogStore,
    private val logCollector: LogCollector,
    private val logger: YSuiteLogger,
    private val permissionChecker:
        PermissionChecker,
    private val permissionCatalog:
        PermissionCatalog,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
    ): T =
        SystemViewModel(
            capabilityMonitor =
                capabilityMonitor,
            platformServices =
                platformServices,
            diagnosticCenter =
                diagnosticCenter,
            logStore = logStore,
            logCollector = logCollector,
            logger = logger,
            permissionChecker =
                permissionChecker,
            permissionCatalog =
                permissionCatalog,
        ) as T
}
