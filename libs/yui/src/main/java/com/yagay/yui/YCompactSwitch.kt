package com.yagay.yui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Canonical YUI switch: the Android View renderer and this Compose renderer
 * share the exact track, outline, thumb and scaling math through YSwitchPainter.
 */
@Composable
fun YStandardSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val appearance = LocalYAppearance.current
    val geometry = appearance.switchGeometry()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .padding(start = appearance.switchLabelGapDp.dp)
            .defaultMinSize(
                minWidth = geometry.touchWidthDp.dp,
                minHeight = geometry.touchHeightDp.dp,
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(geometry.canvasWidthDp.dp, geometry.canvasHeightDp.dp),
        ) {
            val switchColors = YSwitchPaintColors(
                checkedTrack = colors.primary.toArgb(),
                uncheckedTrack = colors.surfaceContainerHighest.toArgb(),
                checkedThumb = colors.onPrimary.toArgb(),
                uncheckedThumb = colors.onSurfaceVariant.toArgb(),
                outline = colors.outline.toArgb(),
            )
            drawIntoCanvas { native ->
                YSwitchPainter.draw(
                    canvas = native.nativeCanvas,
                    widthPx = size.width,
                    heightPx = size.height,
                    density = density,
                    geometry = geometry,
                    checked = checked,
                    enabled = enabled,
                    colors = switchColors,
                )
            }
        }
    }
}

@Deprecated("Use YStandardSwitch.")
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = YStandardSwitch(checked, onCheckedChange, modifier, enabled)
