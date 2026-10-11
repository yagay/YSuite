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
    val appearance = LocalYAppearance.current.buttonGeometry()
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = appearance.iconTouchDp.dp, minHeight = appearance.iconTouchDp.dp),
        shape = RoundedCornerShape(appearance.cornerRadiusDp.dp),
        content = content,
    )
}

@Composable
fun YUiSmallFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appearance = LocalYAppearance.current.buttonGeometry()
    SmallFloatingActionButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = appearance.iconTouchDp.dp, minHeight = appearance.iconTouchDp.dp),
        shape = RoundedCornerShape(appearance.cornerRadiusDp.dp),
        content = content,
    )
}
