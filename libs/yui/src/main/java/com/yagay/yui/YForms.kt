package com.yagay.yui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardOptions
import com.yagay.yui.YUiAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.yagay.yui.YUiOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun YIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    YUiIconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
fun YTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    YUiOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        label = { Text(label) },
        supportingText = when {
            !errorText.isNullOrBlank() -> ({ Text(errorText) })
            !supportingText.isNullOrBlank() -> ({ Text(supportingText) })
            else -> null
        },
        isError = !errorText.isNullOrBlank(),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun YNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) = YTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    supportingText = supportingText,
    errorText = errorText,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
)

@Composable
fun YPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) = YTextField(
    value = value,
    onValueChange = onValueChange,
    label = label,
    modifier = modifier,
    supportingText = supportingText,
    errorText = errorText,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    visualTransformation = PasswordVisualTransformation(),
)

@Immutable
data class YChoiceSpec<T>(val value: T, val label: String, val supportingText: String? = null)

@Composable
fun <T> YRadioGroup(
    value: T,
    options: List<YChoiceSpec<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        options.forEach { option ->
            YListItem(
                title = option.label,
                subtitle = option.supportingText,
                selected = option.value == value,
                enabled = enabled,
                onClick = { if (enabled) onSelected(option.value) },
                trailing = {
                    RadioButton(
                        selected = option.value == value,
                        onClick = { if (enabled) onSelected(option.value) },
                        enabled = enabled,
                    )
                },
            )
        }
    }
}

@Composable
fun YSliderField(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    valueLabel: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!valueLabel.isNullOrBlank()) {
                Text(valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> YSegmentedControl(
    value: T,
    options: List<YChoiceSpec<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.value == value,
                onClick = { onSelected(option.value) },
                enabled = enabled,
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
fun <T> YDropdownField(
    label: String,
    value: T,
    options: List<YChoiceSpec<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.label.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label)
                            if (!option.supportingText.isNullOrBlank()) {
                                Text(
                                    option.supportingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                )
            }
        }
    }
}

@Composable
fun YPickerItem(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = YNavigationItem(
    title = title,
    subtitle = value,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
)

@Composable
fun YConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dangerous: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            YDialogConfirmButton(confirmLabel, onConfirm, dangerous = dangerous)
        },
        dismissButton = { YDialogDismissButton(dismissLabel, onDismiss) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(
                start = YDimens.ScreenHorizontal,
                end = YDimens.ScreenHorizontal,
                bottom = YDimens.ScreenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(YDimens.ControlGap),
        ) { content() }
    }
}

@Immutable
data class YMenuAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun YOverflowMenu(
    expanded: Boolean,
    actions: List<YMenuAction>,
    onDismissRequest: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                enabled = action.enabled,
                onClick = {
                    onDismissRequest()
                    action.onClick()
                },
            )
        }
    }
}

@Composable
fun YMessageHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier)
}
