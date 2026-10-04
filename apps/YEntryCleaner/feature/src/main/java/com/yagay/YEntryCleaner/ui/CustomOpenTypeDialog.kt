package com.yagay.YEntryCleaner.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.YEntryCleaner.R
import com.yagay.YEntryCleaner.domain.CustomOpenDefinition
import com.yagay.YEntryCleaner.domain.OpenPreset
import com.yagay.YEntryCleaner.domain.OpenTypeConfig
import com.yagay.yui.YActionSpec
import com.yagay.yui.YActionStyle
import com.yagay.yui.YFormDialog
import com.yagay.yui.YListItem
import com.yagay.yui.YNotice
import com.yagay.yui.YNoticeTone
import com.yagay.yui.YTextField

@StringRes
private fun customOpenErrorRes(code: String?): Int = when (code) {
    "custom_open_invalid_title" -> R.string.custom_open_invalid_title
    "custom_open_invalid_mime" -> R.string.custom_open_invalid_mime
    "custom_open_invalid_extension" -> R.string.custom_open_invalid_extension
    "custom_open_missing_matcher" -> R.string.custom_open_missing_matcher
    else -> R.string.custom_open_generic_error
}

@Composable
internal fun CustomOpenTypeDialog(
    config: OpenTypeConfig,
    onSave: (OpenPreset, CustomOpenDefinition?) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<OpenPreset?>(null) }
    val preset = editing
    if (preset != null) {
        CustomOpenTypeEditor(
            preset = preset,
            initial = config.customDefinitions[preset],
            onSave = { definition -> onSave(preset, definition); editing = null },
            onDelete = if (preset in config.customDefinitions) ({ onSave(preset, null); editing = null }) else null,
            onDismiss = { editing = null }
        )
        return
    }

    YFormDialog(
        title = stringResource(R.string.custom_open_title),
        onDismissRequest = onDismiss,
        actions = listOf(
            YActionSpec(
                label = stringResource(R.string.common_done),
                style = YActionStyle.PRIMARY,
                onClick = onDismiss,
            ),
        ),
    ) {
        YNotice(stringResource(R.string.custom_open_help))
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(OpenPreset.CUSTOM_SLOTS, key = { it.name }) { slot ->
                val definition = config.customDefinitions[slot]
                val summary = if (definition == null) {
                    stringResource(R.string.custom_open_not_configured)
                } else {
                    buildString {
                        if (definition.mimeTypes.isNotEmpty()) {
                            append(stringResource(R.string.custom_open_mime_summary, definition.mimeTypes.joinToString()))
                        }
                        if (definition.mimeTypes.isNotEmpty() && definition.extensions.isNotEmpty()) {
                            append(stringResource(R.string.yentry_list_separator))
                        }
                        if (definition.extensions.isNotEmpty()) {
                            append(
                                stringResource(
                                    R.string.custom_open_extension_summary,
                                    definition.extensions.joinToString { ".$it" }
                                )
                            )
                        }
                    }
                }
                YListItem(
                    title = definition?.title ?: stringResource(slot.titleRes()),
                    subtitle = summary,
                    detail = stringResource(
                        if (definition == null) R.string.custom_open_not_configured else R.string.common_enable
                    ),
                    onClick = { editing = slot },
                )
            }
        }
    }
}

@Composable
private fun CustomOpenTypeEditor(
    preset: OpenPreset,
    initial: CustomOpenDefinition?,
    onSave: (CustomOpenDefinition) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var mimeText by remember(initial) { mutableStateOf(initial?.mimeTypes?.sorted()?.joinToString("\n").orEmpty()) }
    var extensionText by remember(initial) { mutableStateOf(initial?.extensions?.sorted()?.joinToString("\n").orEmpty()) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    fun parseSet(text: String): Set<String> = text
        .split(',', ';', '\n', '\r', '\t', ' ')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    val presetTitle = stringResource(preset.titleRes())
    val actions = buildList {
        onDelete?.let { delete ->
            add(
                YActionSpec(
                    label = stringResource(R.string.common_delete),
                    style = YActionStyle.DANGER,
                    onClick = delete,
                )
            )
        }
        add(YActionSpec(label = stringResource(R.string.common_cancel), onClick = onDismiss))
        add(
            YActionSpec(
                label = stringResource(R.string.common_save),
                style = YActionStyle.PRIMARY,
                onClick = {
                    runCatching {
                        CustomOpenDefinition(title, parseSet(mimeText), parseSet(extensionText)).validated()
                    }.onSuccess(onSave).onFailure { errorRes = customOpenErrorRes(it.message) }
                },
            )
        )
    }

    YFormDialog(
        title = if (initial == null) {
            stringResource(R.string.custom_open_add_title, presetTitle)
        } else {
            stringResource(R.string.custom_open_edit_title, initial.title)
        },
        onDismissRequest = onDismiss,
        actions = actions,
    ) {
        YNotice(stringResource(R.string.custom_open_help))
        YTextField(
            value = title,
            onValueChange = { title = it; errorRes = null },
            label = stringResource(R.string.custom_open_name),
        )
        YTextField(
            value = mimeText,
            onValueChange = { mimeText = it; errorRes = null },
            label = stringResource(R.string.custom_open_mime_label),
            supportingText = stringResource(R.string.custom_open_mime_example),
            singleLine = false,
            modifier = Modifier.heightIn(min = 96.dp),
        )
        YTextField(
            value = extensionText,
            onValueChange = { extensionText = it; errorRes = null },
            label = stringResource(R.string.custom_open_extension_label),
            supportingText = stringResource(R.string.custom_open_extension_example),
            singleLine = false,
            modifier = Modifier.heightIn(min = 96.dp),
        )
        errorRes?.let {
            YNotice(
                text = stringResource(it),
                tone = YNoticeTone.ERROR,
            )
        }
    }
}
