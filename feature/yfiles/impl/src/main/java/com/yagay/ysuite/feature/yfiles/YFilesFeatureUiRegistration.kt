package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YFilesFeatureUiRegistration(
    private val environment: YFilesEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteFeatureUiRegistration {
    override val contract = YFilesFeatureContract
    override val productSurface = ProductSurfaceKind.FileManager

    @Composable
    override fun label(): String =
        stringResource(R.string.yfiles_title)

    @Composable
    override fun Content() {
        YFilesFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
