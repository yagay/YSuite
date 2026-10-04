package com.yagay.ysuite.feature.yfiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfiles.api.YFilesFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

object YFilesStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration,
    YSuiteStandaloneAwareRegistration {
    override val contract = YFilesFeatureContract
    override val productSurface = ProductSurfaceKind.FileManager

    private var rootGateway: RootGateway? = null

    override fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    ) {
        rootGateway = dependencies.rootGateway
    }

    private val logger: YSuiteLogger =
        CompositeYSuiteLogger(
            listOf(
                InMemoryLogStore(),
            ),
        )

    @Composable
    override fun label(): String =
        stringResource(R.string.yfiles_title)

    @Composable
    override fun Content() {
        val context =
            LocalContext.current.applicationContext
        val gateway = rootGateway
        val environment =
            remember(context, gateway) {
                if (gateway == null) {
                    YFilesEnvironmentFactory.createWithoutRoot(context)
                } else {
                    YFilesEnvironmentFactory.create(
                        context = context,
                        rootGateway = gateway,
                    )
                }
            }

        YFilesFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
