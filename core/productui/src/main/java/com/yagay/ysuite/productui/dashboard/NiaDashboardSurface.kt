package com.yagay.ysuite.productui.dashboard

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.YSuiteBoundedProductPage

/**
 * Dashboard/content-shell pattern aligned with Android Now in Android.
 */
@Composable
fun NiaDashboardSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteBoundedProductPage(
        surfaceKind = ProductSurfaceKind.Dashboard,
        title = title,
        maxContentWidth = ProductLayoutTokens.DashboardContentMaxWidth,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { adaptive ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            content(adaptive)
        }
    }
}
