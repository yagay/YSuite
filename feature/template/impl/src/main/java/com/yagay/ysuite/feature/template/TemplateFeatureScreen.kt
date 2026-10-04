package com.yagay.ysuite.feature.template

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.productui.tool.NiaToolSurface
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun TemplateFeatureScreen(
    viewModel: TemplateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    NiaToolSurface(
        title = stringResource(R.string.template_title),
        navigationIcon = { YSuiteHostNavigationButton() },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            YSuiteSection(
                title = stringResource(R.string.template_section),
            ) {
                YSuiteSwitchItem(
                    title = stringResource(R.string.template_toggle),
                    subtitle = stringResource(R.string.template_toggle_summary),
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }
        }
    }
}
