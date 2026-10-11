package com.yagay.yui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Canonical intrinsic size policy for text buttons, action buttons and chips.
 *
 * The YUI height preference is a MINIMUM; available width, word wrapping,
 * explicit newlines, custom text scaling and vertical content padding decide
 * the real measured height. Keep the Material minimum touch target outside
 * the button's visual geometry.
 */
@Composable
internal fun Modifier.yActionButtonSize(): Modifier {
    val minimumVisualHeight = LocalYAppearance.current.buttonGeometry().visualHeightDp.dp
    return minimumInteractiveComponentSize().heightIn(min = minimumVisualHeight)
}
