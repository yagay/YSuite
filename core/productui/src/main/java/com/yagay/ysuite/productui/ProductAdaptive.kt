package com.yagay.ysuite.productui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

private val LocalProductAdaptiveInfo =
    staticCompositionLocalOf<ProductAdaptiveInfo?> { null }

private fun productWidthClass(widthDp: androidx.compose.ui.unit.Dp): ProductWidthClass =
    when {
        widthDp < 600.dp -> ProductWidthClass.Compact
        widthDp < 840.dp -> ProductWidthClass.Medium
        else -> ProductWidthClass.Expanded
    }

/**
 * Computes the product width class exactly once for one YSuite window and provides it to every
 * nested product workspace. Feature layouts therefore do not reclassify themselves after host
 * navigation panes or drawers change their local constraints.
 */
@Composable
fun ProductAdaptiveRoot(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val adaptive =
            ProductAdaptiveInfo(
                widthClass = productWidthClass(maxWidth),
            )
        CompositionLocalProvider(
            LocalProductAdaptiveInfo provides adaptive,
        ) {
            content(adaptive)
        }
    }
}

/**
 * Reuses the host window classification when present. Standalone product previews still work
 * because they fall back to a local root calculation.
 */
@Composable
fun ProductAdaptiveBox(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    val inherited = LocalProductAdaptiveInfo.current
    if (inherited != null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
        ) {
            content(inherited)
        }
    } else {
        ProductAdaptiveRoot(
            modifier = modifier,
            content = content,
        )
    }
}
