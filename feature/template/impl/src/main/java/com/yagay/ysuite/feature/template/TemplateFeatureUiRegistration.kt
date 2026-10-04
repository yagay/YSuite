package com.yagay.ysuite.feature.template

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.template.api.TemplateFeatureContract
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuitePageKind

object TemplateFeatureUiRegistration : YSuiteFeatureUiRegistration {
    override val contract = TemplateFeatureContract

    override val pageKind =
        YSuitePageKind.Settings

    @Composable
    override fun label(): String =
        stringResource(R.string.template_title)

    @Composable
    override fun Content() {
        TemplateFeatureScreen()
    }
}
