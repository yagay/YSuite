package com.yagay.yui

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The shared Material 3 FAB facade used by product workspaces.
 * Button size and corner radius are resolved from the same live YUI appearance
 * as normal buttons rather than fixed per-product Material defaults.
 */
@Composable
fun YUiFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appearance = LocalYAppearance.current
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = appearance.buttonHeightDp.dp, minHeight = appearance.buttonHeightDp.dp),
        shape = RoundedCornerShape(appearance.buttonRadiusDp.dp),
        content = content,
    )
}

@Composable
fun YUiSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appearance = LocalYAppearance.current
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = appearance.iconTouchTargetDp.dp, minHeight = appearance.iconTouchTargetDp.dp),
        shape = RoundedCornerShape(appearance.buttonRadiusDp.dp),
        content = content,
    )
}
