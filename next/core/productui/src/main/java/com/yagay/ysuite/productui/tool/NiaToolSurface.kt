package com.yagay.ysuite.productui.tool

import com.yagay.ysuite.productui.ProductSurfaceKind

import com.yagay.ysuite.productui.ProductLayoutTokens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.YSuiteBoundedProductPage

/**
 * Focused utility layout using Now in Android's bounded-content convention.
 */
@Composable
fun NiaToolSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteBoundedProductPage(
        surfaceKind = ProductSurfaceKind.Tool,
        title = title,
        maxContentWidth = ProductLayoutTokens.ToolContentMaxWidth,
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
