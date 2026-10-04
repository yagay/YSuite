package com.yagay.ysuite.feature.template

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuitePanel
import com.yagay.ysuite.designsystem.component.YSuiteToggleRow
import com.yagay.ysuite.ui.YSuiteSettingsScreen

@Composable
fun TemplateFeatureScreen(
    viewModel: TemplateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    YSuiteSettingsScreen(
        title = stringResource(R.string.template_title),
        subtitle = stringResource(R.string.template_summary),
    ) { _ ->
        YSuitePanel(title = stringResource(R.string.template_section)) {
            YSuiteToggleRow(
                title = stringResource(R.string.template_toggle),
                subtitle = stringResource(R.string.template_toggle_summary),
                checked = state.enabled,
                onCheckedChange = viewModel::setEnabled,
            )
        }
    }
}
