package com.yagay.ysuite.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

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
