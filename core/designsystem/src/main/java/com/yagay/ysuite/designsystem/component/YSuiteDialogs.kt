package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            YSuitePrimaryAction(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteActionButton(
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
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(label) },
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            YSuitePrimaryAction(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteActionButton(
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
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                minLines = 8,
                maxLines = 18,
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            YSuitePrimaryAction(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteActionButton(
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
    extraContent: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small),
            ) {
                fields.forEach { field ->
                    TextField(
                        value = field.value,
                        onValueChange = {
                            onValueChange(field.id, it)
                        },
                        singleLine = true,
                        label = { Text(field.label) },
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                extraContent?.invoke()
            }
        },
        confirmButton = {
            YSuitePrimaryAction(
                text = confirmText,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            YSuiteActionButton(
                text = dismissText,
                onClick = onDismiss,
            )
        },
    )
}
