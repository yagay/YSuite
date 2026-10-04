package com.yagay.ysuite.designsystem.component

import androidx.compose.material3.AlertDialog
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
            YSuitePrimaryButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            YSuiteSecondaryButton(text = dismissText, onClick = onDismiss)
        },
    )
}
