package com.yagay.ysuite.feature.yminiguard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yminiguard.api.YMiniGuardFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

object YMiniGuardStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration,
    YSuiteStandaloneAwareRegistration {
    override val contract = YMiniGuardFeatureContract
    override val productSurface = ProductSurfaceKind.EntityManager
    private var dependencies: YSuiteStandaloneDependencies? = null
    private val logger = CompositeYSuiteLogger(listOf(InMemoryLogStore()))
    override fun bindStandaloneDependencies(dependencies: YSuiteStandaloneDependencies) {
        this.dependencies = dependencies
    }
    @Composable override fun label(): String =
        stringResource(R.string.yminiguard_title)
    @Composable override fun Content() {
        val context = LocalContext.current.applicationContext
        val deps = checkNotNull(dependencies)
        val environment = remember(context, deps) {
            YMiniGuardEnvironmentFactory.create(
                context,
                deps.rootGateway,
                deps.hookGateway,
                logger,
            )
        }
        YMiniGuardFeatureScreen(environment)
    }
}
