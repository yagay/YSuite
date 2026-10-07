package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
    val history = state.resourceHistory

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
            values = history.cpu,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_ram),
            values = history.ram,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_swap),
            values = history.swap,
        )
        HistoryRow(
            title = stringResource(R.string.ytask_gpu),
            values = history.gpu,
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
