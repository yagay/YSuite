package com.yagay.yui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A compact YUI switch, not a scaled Material switch that still occupies a tall layout cell.
 *
 * The visual track is 38x22dp; the independent touch surface is 48x40dp.
 * Settings rows remain at least 48dp high so the whole option is easy to tap.
 */
@Composable
fun YCompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val track = if (checked) colors.primary else colors.surfaceVariant
    val outline = if (checked) Color.Transparent else colors.outline
    val thumb = if (checked) colors.onPrimary else colors.onSurfaceVariant
    val opacity = if (enabled) 1f else 0.38f
    val switchShape = CircleShape

    Box(
        modifier = modifier
            .size(width = YDimens.TouchTarget, height = YDimens.SwitchSlotHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = YDimens.SwitchTrackWidth, height = YDimens.SwitchTrackHeight)
                .background(track.copy(alpha = opacity), switchShape)
                .border(1.dp, outline.copy(alpha = opacity), switchShape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = if (checked) {
                        YDimens.SwitchTrackWidth - YDimens.SwitchThumbSize - 2.dp
                    } else {
                        2.dp
                    })
                    .size(YDimens.SwitchThumbSize)
                    .background(thumb.copy(alpha = opacity), CircleShape),
            )
        }
    }
}
