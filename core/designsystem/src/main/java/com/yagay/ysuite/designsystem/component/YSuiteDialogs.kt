package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

data class YSuiteFormField(
    val id: String,
    val label: String,
    val value: String,
)

@Composable
fun YSuiteConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            YSuitePrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteSecondaryButton(
                text = dismissText,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
fun YSuiteTextInputDialog(
    title: String,
    label: String,
    value: String,
    confirmText: String,
    dismissText: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = {
            YSuitePrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteSecondaryButton(
                text = dismissText,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
fun YSuiteTextEditorDialog(
    title: String,
    value: String,
    confirmText: String,
    dismissText: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                minLines = 8,
                maxLines = 18,
            )
        },
        confirmButton = {
            YSuitePrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteSecondaryButton(
                text = dismissText,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
fun YSuiteTextFormDialog(
    title: String,
    fields: List<YSuiteFormField>,
    confirmText: String,
    dismissText: String,
    onValueChange: (String, String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                fields.forEach { field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = {
                            onValueChange(field.id, it)
                        },
                        singleLine = true,
                        label = { Text(field.label) },
                    )
                }
            }
        },
        confirmButton = {
            YSuitePrimaryButton(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteSecondaryButton(
                text = dismissText,
                onClick = onDismiss,
            )
        },
    )
}
