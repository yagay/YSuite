package com.yagay.yui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Single Material 3 control implementation for every Compose feature.
 *
 * The visible button uses a 40dp design token; Material 3 still applies its
 * accessible touch target. Do not recreate local ButtonDefaults or paddings.
 */
enum class YButtonVariant { FILLED, TONAL, OUTLINED, TEXT, DANGER }

@Composable
fun YMaterialButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: YButtonVariant = YButtonVariant.FILLED,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonModifier = modifier.heightIn(min = YDimens.ButtonVisualHeight)
    val padding = PaddingValues(
        horizontal = if (variant == YButtonVariant.TEXT) YDimens.ButtonTextPaddingHorizontal
            else YDimens.ButtonPaddingHorizontal,
        vertical = YDimens.ButtonPaddingVertical,
    )
    val shape = MaterialTheme.shapes.small
    when (variant) {
        YButtonVariant.FILLED -> Button(
            onClick = onClick, modifier = buttonModifier, enabled = enabled,
            shape = shape, contentPadding = padding, content = content,
        )
        YButtonVariant.TONAL -> FilledTonalButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled,
            shape = shape, contentPadding = padding, content = content,
        )
        YButtonVariant.OUTLINED -> OutlinedButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled,
            shape = shape, contentPadding = padding, content = content,
        )
        YButtonVariant.TEXT -> TextButton(
            onClick = onClick, modifier = buttonModifier, enabled = enabled,
            shape = shape, contentPadding = padding, content = content,
        )
        YButtonVariant.DANGER -> Button(
            onClick = onClick, modifier = buttonModifier, enabled = enabled,
            shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            content = content,
        )
    }
}

@Composable
fun YMaterialFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}
