package com.yagay.ysuite.feature.ypower

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ypower.api.YPowerFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YPowerFeatureUiRegistration(
    private val environment: YPowerEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract =
        YPowerFeatureContract
    override val productSurface =
        ProductSurfaceKind.EntityManager

    @Composable
    override fun label(): String =
        stringResource(R.string.ypower_title)

    @Composable
    override fun Content() {
        YPowerFeatureScreen(environment)
    }
}
