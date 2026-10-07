package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskPage
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcess
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessKind
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskProcessSort
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.productui.featurelayout.YTaskManagerWorkspace
import com.yagay.ysuite.ui.YSuiteHostNavigationButton
import kotlin.math.roundToInt

@Composable
fun YTaskManagerFeatureScreen(
    environment: YTaskManagerEnvironment,
) {
    val model: YTaskManagerViewModel =
        viewModel(factory = YTaskManagerViewModel.Factory(environment))
    val state by model.state.collectAsStateWithLifecycle()

    YTaskManagerWorkspace(
        title = stringResource(R.string.ytask_title),
        navigationIcon = { YSuiteHostNavigationButton() },
        summary = {
            Row(
                modifier = Modifier.padding(YSuiteSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteStatusBadge(
                    text =
                        if (state.rootStatus == CapabilityStatus.Available) {
                            stringResource(R.string.ytask_root_ready)
                        } else {
                            stringResource(R.string.ytask_root_required)
                        },
                    tone =
                        if (state.rootStatus == CapabilityStatus.Available) {
                            YSuiteStatusTone.Positive
                        } else {
                            YSuiteStatusTone.Error
                        },
                )
                YSuiteStatusBadge(
                    text =
                        stringResource(
                            R.string.ytask_process_count,
                            state.snapshot.processes.size,
                        ),
                    tone = YSuiteStatusTone.Neutral,
                )
            }
        },
        filters = {
            Column(
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                YSuiteFilterBar(
                    options =
                        listOf(
                            YSuiteFilterOption(
                                YTaskPage.Processes.name,
                                stringResource(R.string.ytask_processes),
                            ),
                            YSuiteFilterOption(
                                YTaskPage.Resources.name,
                                stringResource(R.string.ytask_resources),
                            ),
                            YSuiteFilterOption(
                                YTaskPage.Network.name,
                                stringResource(R.string.ytask_network),
                            ),
                        ),
                    selectedId = state.page.name,
                    onSelected = {
                        runCatching { YTaskPage.valueOf(it) }
                            .getOrNull()?.let(model::setPage)
                    },
                )
                if (state.page == YTaskPage.Processes) {
                    YSuiteSearchField(
                        value = state.query,
                        onValueChange = model::setQuery,
                        label = stringResource(R.string.ytask_search),
                    )
                    YSuiteFilterBar(
                        options =
                            listOf(
                                YSuiteFilterOption(
                                    YTaskProcessSort.Memory.name,
                                    stringResource(R.string.ytask_sort_memory),
                                ),
                                YSuiteFilterOption(
                                    YTaskProcessSort.Name.name,
                                    stringResource(R.string.ytask_sort_name),
                                ),
                                YSuiteFilterOption(
                                    YTaskProcessSort.Pid.name,
                                    stringResource(R.string.ytask_sort_pid),
                                ),
                                YSuiteFilterOption(
                                    YTaskProcessSort.Download.name,
                                    stringResource(R.string.ytask_sort_download),
                                ),
                                YSuiteFilterOption(
                                    YTaskProcessSort.Upload.name,
                                    stringResource(R.string.ytask_sort_upload),
                                ),
                            ),
                        selectedId = state.sort.name,
                        onSelected = {
                            runCatching { YTaskProcessSort.valueOf(it) }
                                .getOrNull()?.let(model::setSort)
                        },
                    )
                }
            }
        },
        resourcesPane = {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.ytask_resources),
                        subtitle = state.snapshot.system.soc.ifBlank {
                            state.snapshot.system.architecture
                        },
                    )
                }
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.ytask_cpu),
                        subtitle =
                            stringResource(
                                R.string.ytask_percent,
                                state.snapshot.system.cpuPercent.roundToInt(),
                            ),
                    )
                }
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.ytask_ram),
                        subtitle =
                            stringResource(
                                R.string.ytask_memory_pair,
                                formatBytes(state.snapshot.system.ramUsedBytes),
                                formatBytes(state.snapshot.system.ramTotalBytes),
                            ),
                    )
                }
                item {
                    YSuiteListItem(
                        title = stringResource(R.string.ytask_network),
                        subtitle = state.snapshot.networkBackend,
                    )
                }
            }
        },
        detailPane =
            model.selectedProcess()?.let { process ->
                { ProcessDetail(process, model) }
            },
    ) {
        when {
            state.rootStatus != CapabilityStatus.Available ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        YSuiteListItem(
                            title = stringResource(R.string.ytask_root_required),
                            subtitle = stringResource(R.string.ytask_root_required_desc),
                        )
                    }
                }
            state.page == YTaskPage.Resources ->
                ResourceContent(state)
            state.page == YTaskPage.Network ->
                NetworkContent(state)
            else ->
                ProcessContent(state, model)
        }
    }
}

@Composable
private fun ProcessContent(
    state: YTaskManagerUiState,
    model: YTaskManagerViewModel,
) {
    val visible = model.visibleProcesses()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            YSuiteFilterBar(
                options =
                    listOf(
                        YSuiteFilterOption(
                            YTaskProcessKind.UserApp.name,
                            stringResource(
                                if (state.showUser) {
                                    R.string.ytask_user_on
                                } else {
                                    R.string.ytask_user_off
                                },
                            ),
                        ),
                        YSuiteFilterOption(
                            YTaskProcessKind.SystemApp.name,
                            stringResource(
                                if (state.showSystem) {
                                    R.string.ytask_system_on
                                } else {
                                    R.string.ytask_system_off
                                },
                            ),
                        ),
                        YSuiteFilterOption(
                            YTaskProcessKind.Linux.name,
                            stringResource(
                                if (state.showLinux) {
                                    R.string.ytask_linux_on
                                } else {
                                    R.string.ytask_linux_off
                                },
                            ),
                        ),
                    ),
                selectedId = null,
                onSelected = {
                    runCatching { YTaskProcessKind.valueOf(it) }
                        .getOrNull()?.let(model::setKind)
                },
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            )
        }
        if (visible.isEmpty()) {
            item {
                YSuiteListItem(
                    title =
                        if (state.loading) {
                            stringResource(R.string.ytask_loading)
                        } else {
                            stringResource(R.string.ytask_empty)
                        },
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        items(visible, key = { it.pid }) { process ->
            YSuiteListItem(
                title = process.displayName,
                subtitle =
                    stringResource(
                        R.string.ytask_process_summary,
                        process.pid,
                        formatBytes(process.rssKb * 1024L),
                        formatRate(process.rxBytesPerSecond),
                        formatRate(process.txBytesPerSecond),
                    ),
                modifier =
                    Modifier
                        .clickable { model.select(process) }
                        .padding(
                            horizontal = YSuiteSpacing.Medium,
                            vertical = YSuiteSpacing.Small,
                        ),
            )
        }
        state.error?.let { error ->
            item {
                YSuiteStatusBadge(
                    text = error,
                    tone = YSuiteStatusTone.Error,
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
    }
}

@Composable
private fun ResourceContent(state: YTaskManagerUiState) {
    val s = state.snapshot.system
    val g = state.snapshot.gpu
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        item {
            YSuiteSection(
                title = stringResource(R.string.ytask_cpu),
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(
                    title = stringResource(R.string.ytask_cpu_usage),
                    subtitle = stringResource(
                        R.string.ytask_percent,
                        s.cpuPercent.roundToInt(),
                    ),
                )
                YSuiteListItem(
                    title = stringResource(R.string.ytask_cpu_info),
                    subtitle = stringResource(
                        R.string.ytask_cpu_info_value,
                        s.soc.ifBlank { s.architecture },
                        s.coreCount,
                        s.load1,
                    ),
                )
                s.cpuTemperatureC?.let {
                    YSuiteListItem(
                        title = stringResource(R.string.ytask_temperature),
                        subtitle = stringResource(
                            R.string.ytask_temperature_value,
                            it,
                        ),
                    )
                }
            }
        }
        item {
            YSuiteSection(
                title = stringResource(R.string.ytask_memory),
                modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(
                    title = stringResource(R.string.ytask_ram),
                    subtitle = stringResource(
                        R.string.ytask_memory_pair,
                        formatBytes(s.ramUsedBytes),
                        formatBytes(s.ramTotalBytes),
                    ),
                )
                YSuiteListItem(
                    title = stringResource(R.string.ytask_swap),
                    subtitle = stringResource(
                        R.string.ytask_memory_pair,
                        formatBytes(s.swapUsedBytes),
                        formatBytes(s.swapTotalBytes),
                    ),
                )
            }
        }
        item {
            YSuiteSection(
                title = stringResource(R.string.ytask_gpu),
                modifier = Modifier.padding(horizontal = YSuiteSpacing.Medium),
            ) {
                YSuiteListItem(
                    title = stringResource(R.string.ytask_gpu_usage),
                    subtitle =
                        g.usagePercent?.let {
                            stringResource(
                                R.string.ytask_percent,
                                it.roundToInt(),
                            )
                        } ?: stringResource(R.string.ytask_unavailable),
                )
                YSuiteListItem(
                    title = stringResource(R.string.ytask_gpu_frequency),
                    subtitle =
                        g.currentHz?.let(::formatFrequency)
                            ?: stringResource(R.string.ytask_unavailable),
                )
            }
        }
    }
}

@Composable
private fun NetworkContent(state: YTaskManagerUiState) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            YSuiteListItem(
                title = stringResource(R.string.ytask_network_backend),
                subtitle = state.snapshot.networkBackend,
                modifier = Modifier.padding(YSuiteSpacing.Medium),
            )
        }
        if (state.snapshot.network.isEmpty()) {
            item {
                YSuiteListItem(
                    title = stringResource(R.string.ytask_network_empty),
                    modifier = Modifier.padding(YSuiteSpacing.Medium),
                )
            }
        }
        items(state.snapshot.network, key = { it.uid }) { row ->
            YSuiteListItem(
                title = row.label,
                subtitle =
                    stringResource(
                        R.string.ytask_network_summary,
                        formatRate(row.rxBytesPerSecond),
                        formatRate(row.txBytesPerSecond),
                    ),
                modifier = Modifier.padding(
                    horizontal = YSuiteSpacing.Medium,
                    vertical = YSuiteSpacing.Small,
                ),
            )
        }
    }
}

@Composable
private fun ProcessDetail(
    process: YTaskProcess,
    model: YTaskManagerViewModel,
) {
    Column(
        modifier = Modifier.padding(YSuiteSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Medium),
    ) {
        YSuiteSection(title = process.displayName) {
            YSuiteListItem(
                title = stringResource(R.string.ytask_pid),
                subtitle = process.pid.toString(),
            )
            YSuiteListItem(
                title = stringResource(R.string.ytask_command),
                subtitle = process.command,
            )
            YSuiteListItem(
                title = stringResource(R.string.ytask_memory),
                subtitle = formatBytes(process.rssKb * 1024L),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
        ) {
            YSuiteSecondaryButton(
                text = stringResource(R.string.ytask_kill),
                onClick = { model.kill(process) },
            )
            if (process.packageName != null) {
                YSuiteSecondaryButton(
                    text = stringResource(R.string.ytask_force_stop),
                    onClick = { model.forceStop(process) },
                )
            }
        }
    }
}

private fun formatBytes(value: Long): String {
    if (value <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var index = 0
    while (amount >= 1024.0 && index < units.lastIndex) {
        amount /= 1024.0
        index++
    }
    return if (index == 0) {
        value.toString() + " " + units[index]
    } else {
        "%.1f %s".format(amount, units[index])
    }
}

private fun formatRate(value: Long): String =
    formatBytes(value) + "/s"

private fun formatFrequency(value: Long): String =
    if (value >= 1_000_000L) {
        "%.0f MHz".format(value / 1_000_000.0)
    } else {
        value.toString() + " Hz"
    }
