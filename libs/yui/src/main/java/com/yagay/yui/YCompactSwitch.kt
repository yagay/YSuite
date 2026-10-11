package com.yagay.yui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

/**
 * Canonical switch for all Compose features.
 * Default dimensions preserve the upstream Material3 control; custom dimensions
 * use one YUI renderer with independent track/thumb sizes and Switch semantics.
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
    val wrapper = modifier
        .padding(start = appearance.switchLabelGapDp.dp)
        .defaultMinSize(
            minWidth = geometry.touchWidthDp.dp,
            minHeight = geometry.touchHeightDp.dp,
        )
    if (geometry.useMaterialTrack) {
        Box(modifier = wrapper, contentAlignment = Alignment.Center) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.scale(geometry.scale),
            )
        }
    } else {
        val colors = MaterialTheme.colorScheme
        val position = animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            label = "YUISwitchThumbPosition",
        ).value
        val trackColor = if (checked) colors.primary else colors.surfaceContainerHighest
        val thumbColor = if (checked) colors.onPrimary else colors.onSurfaceVariant
        val outlineColor = if (checked) colors.primary else colors.outline
        val opacity = if (enabled) 1f else 0.38f
        Box(
            modifier = wrapper.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(
                geometry.canvasWidthDp.dp,
                geometry.canvasHeightDp.dp,
            )) {
                val trackWidth = geometry.trackWidthDp.dp.toPx()
                val trackHeight = geometry.trackHeightDp.dp.toPx()
                val thumbSize = geometry.thumbDiameterDp.dp.toPx()
                val trackLeft = (size.width - trackWidth) / 2f
                val trackTop = (size.height - trackHeight) / 2f
                val corner = CornerRadius(trackHeight / 2f)
                drawRoundRect(
                    color = trackColor.copy(alpha = opacity),
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidth, trackHeight),
                    cornerRadius = corner,
                )
                if (!checked) {
                    val stroke = 2.dp.toPx().coerceAtMost(trackHeight / 4f)
                    drawRoundRect(
                        color = outlineColor.copy(alpha = opacity),
                        topLeft = Offset(trackLeft + stroke / 2f, trackTop + stroke / 2f),
                        size = Size((trackWidth - stroke).coerceAtLeast(0f),
                            (trackHeight - stroke).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius((trackHeight - stroke) / 2f),
                        style = Stroke(stroke),
                    )
                }
                val thumbRadius = thumbSize / 2f
                val inset = (thumbRadius + 2.dp.toPx()).coerceAtMost(trackWidth / 2f)
                val centerLeft = trackLeft + inset
                val centerRight = trackLeft + trackWidth - inset
                val centerX = centerLeft + (centerRight - centerLeft) * position
                drawCircle(
                    color = thumbColor.copy(alpha = opacity),
                    radius = thumbRadius,
                    center = Offset(centerX, size.height / 2f),
                )
            }
        }
    }
}

/** Compatibility alias: uses exactly the same customizable YUI control. */
@Deprecated("Use YStandardSwitch.")
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = YStandardSwitch(checked, onCheckedChange, modifier, enabled)
