package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
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
        title = "History",
        modifier =
            Modifier.padding(
                horizontal =
                    YSuiteSpacing.Medium,
            ),
    ) {
        HistoryRow(
            title = "CPU",
            values = cpu,
        )
        HistoryRow(
            title = "RAM",
            values = ram,
        )
        HistoryRow(
            title = "SWAP",
            values = swap,
        )
        HistoryRow(
            title = "GPU",
            values = gpu,
        )
    }
}

@Composable
private fun HistoryRow(
    title: String,
    values: List<Float>,
) {
    val lineColor =
        MaterialTheme.colorScheme.primary
    val guideColor =
        MaterialTheme.colorScheme
            .outlineVariant

    Column(
        modifier =
            Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography
                    .labelLarge,
        )
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
        ) {
            drawLine(
                color = guideColor,
                start =
                    Offset(
                        0f,
                        size.height / 2f,
                    ),
                end =
                    Offset(
                        size.width,
                        size.height / 2f,
                    ),
                strokeWidth = 1f,
            )
            if (values.size < 2) {
                return@Canvas
            }
            val maxIndex =
                (values.size - 1)
                    .coerceAtLeast(1)
            var previous =
                point(
                    index = 0,
                    maxIndex = maxIndex,
                    value = values.first(),
                )
            values.drop(1)
                .forEachIndexed {
                    index,
                    value,
                    ->
                    val next =
                        point(
                            index = index + 1,
                            maxIndex = maxIndex,
                            value = value,
                        )
                    drawLine(
                        color = lineColor,
                        start = previous,
                        end = next,
                        strokeWidth = 3f,
                    )
                    previous = next
                }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope
    .point(
        index: Int,
        maxIndex: Int,
        value: Float,
    ): Offset =
    Offset(
        x =
            size.width *
                index.toFloat() /
                maxIndex.toFloat(),
        y =
            size.height *
                (
                    1f -
                        value.coerceIn(
                            0f,
                            100f,
                        ) / 100f
                    ),
    )

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
