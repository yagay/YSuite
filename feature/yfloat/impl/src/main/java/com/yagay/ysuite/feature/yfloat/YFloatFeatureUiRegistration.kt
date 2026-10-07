package com.yagay.ysuite.feature.yfloat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfloat.api.YFloatFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
class YFloatFeatureUiRegistration(private val environment: YFloatEnvironment) : YSuiteFeatureUiRegistration {
    override val contract = YFloatFeatureContract
    override val productSurface = ProductSurfaceKind.Settings
    @Composable override fun label(): String = stringResource(R.string.yfloat_title)
    @Composable override fun Content() { YFloatFeatureScreen(environment) }
}
