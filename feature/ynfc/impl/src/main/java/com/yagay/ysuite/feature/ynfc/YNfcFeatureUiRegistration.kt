package com.yagay.ysuite.feature.ynfc
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ynfc.api.YNfcFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
class YNfcFeatureUiRegistration(private val environment: YNfcEnvironment) : YSuiteFeatureUiRegistration {
    override val contract = YNfcFeatureContract
    override val productSurface = ProductSurfaceKind.Tool
    @Composable override fun label(): String = stringResource(R.string.ynfc_title)
    @Composable override fun Content() { YNfcFeatureScreen(environment) }
}
