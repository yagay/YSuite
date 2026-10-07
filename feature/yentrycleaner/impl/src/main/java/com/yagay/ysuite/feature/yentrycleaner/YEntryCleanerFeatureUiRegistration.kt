package com.yagay.ysuite.feature.yentrycleaner

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.yentrycleaner.api.YEntryCleanerFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

class YEntryCleanerFeatureUiRegistration(
    private val environment:
        YEntryCleanerEnvironment,
) : YSuiteFeatureUiRegistration {
    override val contract =
        YEntryCleanerFeatureContract
    override val productSurface =
        ProductSurfaceKind.EntityManager

    @Composable
    override fun label(): String =
        stringResource(R.string.yentry_title)

    @Composable
    override fun Content() {
        YEntryCleanerFeatureScreen(
            environment,
        )
    }
}
