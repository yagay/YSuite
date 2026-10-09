package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydownload.api.YDownloadFeatureContract
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YDownloadFeatureUiRegistration private constructor(
    private val environmentProvider: () -> YDownloadEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteFeatureUiRegistration {
    constructor(
        environment: YDownloadEnvironment,
        logger: YSuiteLogger,
    ) : this(
        environmentProvider = { environment },
        logger = logger,
    )

    companion object {
        fun lazy(
            logger: YSuiteLogger,
            environmentProvider: () -> YDownloadEnvironment,
        ): YDownloadFeatureUiRegistration =
            YDownloadFeatureUiRegistration(
                environmentProvider = environmentProvider,
                logger = logger,
            )
    }

    override val contract = YDownloadFeatureContract
    override val productSurface =
        ProductSurfaceKind.DownloadManager

    @Composable
    override fun label(): String =
        stringResource(R.string.ydownload_title)

    @Composable
    override fun Content() {
        val environment =
            remember {
                environmentProvider()
            }
        YDownloadFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
