package com.yagay.ysuite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.resources.R

@Composable
fun YSuiteArchitectureOverview(padding: PaddingValues = PaddingValues()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(YSuiteSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Large),
    ) {
        YSuiteSection(title = stringResource(R.string.architecture_ready)) {
            YSuiteListItem(
                title = stringResource(R.string.design_system),
                subtitle = stringResource(R.string.design_system_summary),
            )
            YSuiteListItem(
                title = stringResource(R.string.localization),
                subtitle = stringResource(R.string.localization_summary),
            )
            YSuiteListItem(
                title = stringResource(R.string.feature_boundary),
                subtitle = stringResource(R.string.feature_boundary_summary),
            )
        }
    }
}
