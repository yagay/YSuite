package com.yagay.YEntryCleaner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.ComponentCandidate
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YFormDialog
import com.yagay.yui.YListItem
import com.yagay.yui.YTextField

@Composable
internal fun ComponentTitleDialog(
    item: ComponentCandidate,
    currentTitle: String?,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(item.rule.id) { mutableStateOf(currentTitle.orEmpty()) }
    LaunchedEffect(currentTitle, item.rule.id) { text = currentTitle.orEmpty() }
    val trimmed = text.trim()
    val normalizedCurrent = currentTitle.orEmpty()
    val changed = trimmed != normalizedCurrent

    YFormDialog(
        title = stringResource(R.string.component_title_dialog_title),
        onDismissRequest = onDismiss,
        actions = listOf(
            YActionSpec(
                label = stringResource(R.string.common_cancel),
                onClick = onDismiss,
            ),
            YActionSpec(
                label = stringResource(R.string.common_save),
                enabled = changed,
                style = YActionStyle.PRIMARY,
                onClick = {
                    onSave(trimmed.ifEmpty { null })
                    onDismiss()
                },
            ),
        ),
    ) {
        YListItem(
            title = stringResource(R.string.component_title_original, item.activityLabel),
            subtitle = stringResource(R.string.component_title_help, stringResource(item.rule.kind.titleRes())),
            detail = item.rule.className,
        )
        YTextField(
            value = text,
            onValueChange = { if (it.length <= 64 && it.none(Char::isISOControl)) text = it },
            label = stringResource(R.string.component_title_field),
            supportingText = stringResource(R.string.component_title_support, text.length),
        )
    }
}
