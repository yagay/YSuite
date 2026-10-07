package com.yagay.ysuite.feature.yparam

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yparam.api.YParamFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YParamFeatureUiRegistration(
    private val environment: YParamEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract = YParamFeatureContract
    override val productSurface = ProductSurfaceKind.EntityManager

    @Composable
    override fun label(): String =
        stringResource(R.string.yparam_title)

    @Composable
    override fun Content() {
        YParamFeatureScreen(environment)
    }
}
