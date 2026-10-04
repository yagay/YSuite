package com.yagay.ysuite.feature.template

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.template.api.TemplateFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

object TemplateFeatureUiRegistration : YSuiteFeatureUiRegistration {
    override val contract = TemplateFeatureContract
    override val productSurface = ProductSurfaceKind.Tool

    @Composable
    override fun label(): String =
        stringResource(R.string.template_title)

    @Composable
    override fun Content() {
        TemplateFeatureScreen()
    }
}
