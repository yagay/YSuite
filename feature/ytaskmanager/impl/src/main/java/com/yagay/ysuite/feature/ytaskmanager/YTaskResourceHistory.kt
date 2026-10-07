package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSparkline
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

@Composable
internal fun YTaskResourceHistory(
    state: YTaskManagerUiState,
) {
    val cpu =
        remember {
            mutableStateListOf<Float>()
        }
    val ram =
        remember {
            mutableStateListOf<Float>()
        }
    val swap =
        remember {
            mutableStateListOf<Float>()
        }
    val gpu =
        remember {
            mutableStateListOf<Float>()
        }

    val system = state.snapshot.system
    val gpuSnapshot = state.snapshot.gpu
    val ramPercent =
        percent(
            system.ramUsedBytes,
            system.ramTotalBytes,
        )
    val swapPercent =
        percent(
            system.swapUsedBytes,
            system.swapTotalBytes,
        )
    val gpuPercent =
        gpuSnapshot.usagePercent ?: 0f

    LaunchedEffect(
        system.cpuPercent,
        system.ramUsedBytes,
        system.swapUsedBytes,
        gpuSnapshot.usagePercent,
    ) {
        cpu.append(system.cpuPercent)
        ram.append(ramPercent)
        swap.append(swapPercent)
        gpu.append(gpuPercent)
    }

    YSuiteSection(
        title = stringResource(R.string.ytask_history),
        modifier =
            Modifier.padding(
                horizontal =
                    YSuiteSpacing.Medium,
            ),
    ) {
        HistoryRow(
            title = stringResource(R.string.ytask_cpu),
            values = cpu,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_ram),
            values = ram,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_swap),
            values = swap,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_gpu),
            values = gpu,
        )
    }
}

@Composable
private fun HistoryRow(
    title: String,
    values: List<Float>,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        YSuiteListItem(
            title = title,
        )
        YSuiteSparkline(
            values = values,
        )
    }
}

private fun MutableList<Float>.append(
    value: Float,
) {
    add(
        value.coerceIn(
            0f,
            100f,
        ),
    )
    while (size > 60) {
        removeAt(0)
    }
}

private fun percent(
    used: Long,
    total: Long,
): Float =
    if (total > 0L) {
        (
            used.toDouble() /
                total.toDouble() *
                100.0
            ).toFloat()
            .coerceIn(0f, 100f)
    } else {
        0f
    }
