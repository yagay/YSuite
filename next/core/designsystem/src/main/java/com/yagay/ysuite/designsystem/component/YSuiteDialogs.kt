package com.yagay.ysuite.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yagay.yui.YConfirmDialog
import com.yagay.yui.YDialogConfirmButton
import com.yagay.yui.YDialogDismissButton
import com.yagay.yui.YTextField
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing

data class YSuiteFormField(val id: String, val label: String, val value: String)

@Composable
fun YSuiteConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    YConfirmDialog(
        title = title, message = message,
        confirmLabel = confirmText, dismissLabel = dismissText,
        onConfirm = onConfirm, onDismiss = onDismiss,
    )
}

@Composable
fun YSuiteTextInputDialog(
    title: String, label: String, value: String,
    confirmText: String, dismissText: String,
    onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { YTextField(value = value, onValueChange = onValueChange, label = label) },
        confirmButton = { YDialogConfirmButton(confirmText, onConfirm) },
        dismissButton = { YDialogDismissButton(dismissText, onDismiss) },
    )
}

@Composable
fun YSuiteTextEditorDialog(
    title: String, value: String, confirmText: String, dismissText: String,
    onValueChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            YTextField(
                value = value, onValueChange = onValueChange,
                label = title, singleLine = false, minLines = 8, maxLines = 18,
            )
        },
        confirmButton = { YDialogConfirmButton(confirmText, onConfirm) },
        dismissButton = { YDialogDismissButton(dismissText, onDismiss) },
    )
}

@Composable
fun YSuiteTextFormDialog(
    title: String, fields: List<YSuiteFormField>,
    confirmText: String, dismissText: String,
    onValueChange: (String, String) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(YSuiteSpacing.Small)) {
                fields.forEach { field ->
                    YTextField(
                        value = field.value,
                        onValueChange = { onValueChange(field.id, it) },
                        label = field.label,
                    )
                }
                extraContent?.invoke()
            }
        },
        confirmButton = { YDialogConfirmButton(confirmText, onConfirm) },
        dismissButton = { YDialogDismissButton(dismissText, onDismiss) },
    )
}
