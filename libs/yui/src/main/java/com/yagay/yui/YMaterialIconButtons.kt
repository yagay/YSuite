package com.yagay.yui
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Three additional upstream Material3 icon button variants with one YUI geometry. */
@Composable fun YUiFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val a = LocalYAppearance.current
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = a.iconTouchTargetDp.dp, minHeight = a.iconTouchTargetDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(a.buttonRadiusDp.dp),
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable fun YUiFilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val a = LocalYAppearance.current
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = a.iconTouchTargetDp.dp, minHeight = a.iconTouchTargetDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(a.buttonRadiusDp.dp),
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable fun YUiOutlinedIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: IconButtonColors = IconButtonDefaults.outlinedIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val a = LocalYAppearance.current
    OutlinedIconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = a.iconTouchTargetDp.dp, minHeight = a.iconTouchTargetDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(a.buttonRadiusDp.dp),
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
