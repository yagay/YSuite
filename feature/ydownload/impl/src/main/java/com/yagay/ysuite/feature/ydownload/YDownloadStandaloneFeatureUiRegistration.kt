package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ydownload.api.YDownloadFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.platform.api.HookGateway
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

object YDownloadStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration,
    YSuiteStandaloneAwareRegistration {
    override val contract = YDownloadFeatureContract
    override val productSurface =
        ProductSurfaceKind.DownloadManager

    private var hookGateway: HookGateway? = null

    override fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    ) {
        hookGateway = dependencies.hookGateway
    }

    private val logger: YSuiteLogger =
        CompositeYSuiteLogger(
            listOf(InMemoryLogStore()),
        )

    @Composable
    override fun label(): String =
        stringResource(R.string.ydownload_title)

    @Composable
    override fun Content() {
        val context =
            LocalContext.current.applicationContext
        val hooks =
            checkNotNull(hookGateway) {
                "Standalone dependencies not bound"
            }
        val environment =
            remember(context, hooks) {
                YDownloadEnvironmentFactory.create(
                    context = context,
                    logger = logger,
                    hookGateway = hooks,
                )
            }

        YDownloadFeatureScreen(
            environment = environment,
            logger = logger,
        )
    }
}
