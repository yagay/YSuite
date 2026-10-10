package com.yagay.yui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material 3 compatibility facade for existing product layouts.
 *
 * Only YUI defines normal-screen control geometry. Specialized file, browser, download
 * and diagnostics layouts can keep their structure and behavior while importing these
 * controls instead of maintaining local padding/shape/target-size implementations.
 * Default colors, animations and state handling remain from upstream AndroidX Material3.
 */
@Composable
fun YUiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalYAppearance.current.buttonHeightDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding ?: PaddingValues(
            horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp,
            vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun YUiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalYAppearance.current.buttonHeightDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding ?: PaddingValues(
            horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp,
            vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun YUiTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = LocalYAppearance.current.buttonHeightDp.dp),
        enabled = enabled,
        shape = shape ?: RoundedCornerShape(LocalYAppearance.current.buttonRadiusDp.dp),
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding ?: PaddingValues(
            horizontal = LocalYAppearance.current.buttonPaddingHorizontalDp.dp,
            vertical = LocalYAppearance.current.buttonVerticalPaddingDp.dp,
        ),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun YUiIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(
            minWidth = LocalYAppearance.current.iconTouchTargetDp.dp,
            minHeight = LocalYAppearance.current.iconTouchTargetDp.dp,
        ),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun YUiCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}
