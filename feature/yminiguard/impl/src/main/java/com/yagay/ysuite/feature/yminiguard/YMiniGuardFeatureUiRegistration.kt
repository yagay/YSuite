package com.yagay.ysuite.feature.yminiguard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YMiniGuardFeatureUiRegistration(
    private val environment: YMiniGuardEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract = YMiniGuardFeatureContract
    override val productSurface = ProductSurfaceKind.EntityManager
    @Composable override fun label(): String =
        stringResource(R.string.yminiguard_title)
    @Composable override fun Content() {
        YMiniGuardFeatureScreen(environment)
    }
}
