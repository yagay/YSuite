package com.yagay.ysuite.feature.ydiag

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydiag.api.YDiagFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YDiagFeatureUiRegistration(
    private val environment: YDiagEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract = YDiagFeatureContract
    override val productSurface = ProductSurfaceKind.LogViewer

    @Composable
    override fun label(): String =
        stringResource(R.string.ydiag_title)

    @Composable
    override fun Content() {
        YDiagFeatureScreen(environment)
    }
}
