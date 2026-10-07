package com.yagay.ysuite.feature.ytaskmanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ytaskmanager.api.YTaskManagerFeatureContract
import com.yagay.ysuite.logging.api.CompositeYSuiteLogger
import com.yagay.ysuite.logging.api.InMemoryLogStore
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneAwareRegistration
import com.yagay.ysuite.ui.YSuiteStandaloneDependencies

object YTaskManagerStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration,
    YSuiteStandaloneAwareRegistration {
    override val contract = YTaskManagerFeatureContract
    override val productSurface = ProductSurfaceKind.TaskManager

    private var dependencies: YSuiteStandaloneDependencies? = null
    private val logger =
        CompositeYSuiteLogger(listOf(InMemoryLogStore()))

    override fun bindStandaloneDependencies(
        dependencies: YSuiteStandaloneDependencies,
    ) {
        this.dependencies = dependencies
    }

    @Composable
    override fun label(): String =
        stringResource(R.string.ytask_title)

    @Composable
    override fun Content() {
        val context = LocalContext.current.applicationContext
        val deps = checkNotNull(dependencies)
        val environment = remember(context, deps) {
            YTaskManagerEnvironmentFactory.create(
                context = context,
                rootGateway = deps.rootGateway,
                hookGateway = deps.hookGateway,
                logger = logger,
            )
        }
        YTaskManagerFeatureScreen(environment)
    }
}
