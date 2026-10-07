package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskManagerFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YTaskManagerFeatureUiRegistration(
    private val environment: YTaskManagerEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract = YTaskManagerFeatureContract
    override val productSurface = ProductSurfaceKind.TaskManager

    @Composable
    override fun label(): String =
        stringResource(R.string.ytask_title)

    @Composable
    override fun Content() {
        YTaskManagerFeatureScreen(environment)
    }
}
