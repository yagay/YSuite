package com.yagay.ysuite.feature.ynotify

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.ynotify.api.YNotifyFeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

object YNotifyStandaloneFeatureUiRegistration :
    YSuiteFeatureUiRegistration {
    override val contract =
        YNotifyFeatureContract
    override val productSurface =
        ProductSurfaceKind.LogViewer

    @Composable
    override fun label(): String =
        stringResource(R.string.ynotify_title)

    @Composable
    override fun Content() {
        YNotifyFeatureScreen()
    }
}
