package com.yagay.ysuite.productui.detail

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yagay.ysuite.productui.ProductAdaptiveInfo
import com.yagay.ysuite.productui.YSuiteBoundedProductPage

/**
 * Focused detail layout using the shared YSuite bounded page host.
 */
@Composable
fun NiaDetailSurface(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    YSuiteBoundedProductPage(
        title = title,
        maxContentWidth = 900.dp,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    ) { adaptive ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            content(adaptive)
        }
    }
}
