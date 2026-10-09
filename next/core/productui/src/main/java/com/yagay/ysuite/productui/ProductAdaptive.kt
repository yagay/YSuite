package com.yagay.ysuite.productui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.yagay.ysuite.designsystem.theme.YSuiteLayoutTokens

enum class ProductWidthClass {
    Compact,
    Medium,
    Expanded,
}

data class ProductAdaptiveInfo(
    val widthClass: ProductWidthClass,
    val windowWidthClass: ProductWidthClass = widthClass,
) {
    val isCompact: Boolean
        get() = widthClass == ProductWidthClass.Compact

    val isExpanded: Boolean
        get() = widthClass == ProductWidthClass.Expanded

    val isWindowExpanded: Boolean
        get() = windowWidthClass == ProductWidthClass.Expanded
}

private val LocalProductAdaptiveInfo =
    staticCompositionLocalOf<ProductAdaptiveInfo?> { null }

internal fun classifyProductWidth(width: Dp): ProductWidthClass =
    when {
        width < YSuiteLayoutTokens.CompactBreakpoint ->
            ProductWidthClass.Compact
        width < YSuiteLayoutTokens.ExpandedBreakpoint ->
            ProductWidthClass.Medium
        else ->
            ProductWidthClass.Expanded
    }

/**
 * Classifies the feature viewport once after host navigation has been laid out.
 * Nested workspaces inherit this as their window-level classification.
 */
@Composable
fun ProductAdaptiveRoot(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthClass = classifyProductWidth(maxWidth)
        val adaptive =
            ProductAdaptiveInfo(
                widthClass = widthClass,
                windowWidthClass = widthClass,
            )
        CompositionLocalProvider(
            LocalProductAdaptiveInfo provides adaptive,
        ) {
            content(adaptive)
        }
    }
}

/**
 * Reads the feature-window classification without reclassifying for nested constraints.
 */
@Composable
fun ProductAdaptiveBox(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    val inherited = LocalProductAdaptiveInfo.current
    if (inherited != null) {
        Box(modifier = modifier) {
            content(inherited)
        }
    } else {
        ProductAdaptiveRoot(
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * Reclassifies only the local content pane while preserving the feature-window width class.
 *
 * Use this after fixed source/category/detail panes have consumed width. This prevents a workspace
 * from treating a 600dp center pane as Expanded merely because the overall feature viewport was
 * wider than 840dp.
 */
@Composable
fun ProductPaneAdaptiveBox(
    modifier: Modifier = Modifier,
    content: @Composable (ProductAdaptiveInfo) -> Unit,
) {
    val inherited = LocalProductAdaptiveInfo.current
    BoxWithConstraints(modifier = modifier) {
        val localClass = classifyProductWidth(maxWidth)
        content(
            ProductAdaptiveInfo(
                widthClass = localClass,
                windowWidthClass =
                    inherited?.windowWidthClass
                        ?: localClass,
            ),
        )
    }
}
