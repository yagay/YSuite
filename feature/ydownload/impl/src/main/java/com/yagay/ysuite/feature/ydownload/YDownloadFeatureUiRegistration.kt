package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydownload.api.YDownloadFeatureContract
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YDownloadFeatureUiRegistration(
    private val environment: YDownloadEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteFeatureUiRegistration {
    override val contract = YDownloadFeatureContract
    override val productSurface =
        ProductSurfaceKind.DownloadManager

    @Composable
    override fun label(): String =
        stringResource(R.string.ydownload_title)

    @Composable
    override fun Content() {
        YDownloadFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
