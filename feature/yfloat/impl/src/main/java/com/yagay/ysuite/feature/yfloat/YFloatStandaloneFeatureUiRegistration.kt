package com.yagay.ysuite.feature.yfloat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yfloat.api.YFloatFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

object YFloatStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration,
    YSuiteStandaloneAwareRegistration {
    override val contract = YFloatFeatureContract
    override val productSurface = ProductSurfaceKind.Settings

    private var dependencies: YSuiteStandaloneDependencies? = null
    private val logger = CompositeYSuiteLogger(listOf(InMemoryLogStore()))

    override fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    ) {
        this.dependencies = dependencies
    }

    @Composable
    override fun label(): String =
        stringResource(R.string.yfloat_title)

    @Composable
    override fun Content() {
        val context = LocalContext.current.applicationContext
        val deps = checkNotNull(dependencies)
        val environment =
            remember(context, deps) {
                YFloatEnvironmentFactory.create(
                    context = context,
                    rootGateway = deps.rootGateway,
                    hookGateway = deps.hookGateway,
                    logger = logger,
                )
            }
        YFloatFeatureScreen(environment)
    }
}
