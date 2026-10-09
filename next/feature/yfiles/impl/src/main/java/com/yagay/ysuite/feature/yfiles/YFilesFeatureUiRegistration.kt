package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YFilesFeatureUiRegistration private constructor(
    private val environmentProvider: () -> YFilesEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteFeatureUiRegistration {
    constructor(
        environment: YFilesEnvironment,
        logger: YSuiteLogger,
    ) : this(
        environmentProvider = { environment },
        logger = logger,
    )

    companion object {
        fun lazy(
            logger: YSuiteLogger,
            environmentProvider: () -> YFilesEnvironment,
        ): YFilesFeatureUiRegistration =
            YFilesFeatureUiRegistration(
                environmentProvider = environmentProvider,
                logger = logger,
            )
    }

    override val contract = YFilesFeatureContract
    override val productSurface = ProductSurfaceKind.FileManager

    @Composable
    override fun label(): String =
        stringResource(R.string.yfiles_title)

    @Composable
    override fun Content() {
        val environment =
            remember {
                environmentProvider()
            }
        YFilesFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
