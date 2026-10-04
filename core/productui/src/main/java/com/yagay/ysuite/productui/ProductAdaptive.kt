package com.yagay.ysuite.productui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class ProductWidthClass {
    Compact,
    Medium,
    Expanded,
}

data class ProductAdaptiveInfo(
    val widthClass: ProductWidthClass,
) {
    val isCompact: Boolean
        get() = widthClass == ProductWidthClass.Compact

    val isExpanded: Boolean
        get() = widthClass == ProductWidthClass.Expanded
}

@Composable
fun ProductAdaptiveBox(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthClass = when {
            maxWidth < 600.dp -> ProductWidthClass.Compact
            maxWidth < 840.dp -> ProductWidthClass.Medium
            else -> ProductWidthClass.Expanded
        }
        content(ProductAdaptiveInfo(widthClass))
    }
}
