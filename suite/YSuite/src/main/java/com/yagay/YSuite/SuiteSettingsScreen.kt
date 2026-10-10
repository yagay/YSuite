package com.yagay.YSuite

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YAppearanceStore
import com.yagay.yui.YCard
import com.yagay.yui.YControlDefinition
import com.yagay.yui.YControlGroup
import com.yagay.yui.YControlKind
import com.yagay.yui.YDialogConfirmButton
import com.yagay.yui.YDialogDismissButton
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YListItem
import com.yagay.yui.YPageList
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YSettingKey
import com.yagay.yui.YSettingsScaffold
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YUiAlertDialog
import com.yagay.yui.YUiNavigationDrawerItem
import com.yagay.yui.YUiOutlinedTextField
import com.yagay.yui.YUiRadioButton
import com.yagay.yui.YUiSlider
import com.yagay.yui.YUiControlRegistry
import com.yagay.yui.YTheme
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.roundToInt

/**
 * Appearance only. Each shared control family has one canonical parameter group.
 * Functional settings (NFC, Root/LSPosed, notifications, download tasks...) stay in modules.
 */
@Composable
internal fun SuiteSettingsScreen(
    modules: List<Pair<String, String>>,
    moduleId: String?,
    onBack: () -> Unit,
    onSelectModule: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { YAppearanceStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val dispose = store.listen { revision++ }
        onDispose(dispose)
    }
    var group by rememberSaveable(moduleId) { mutableStateOf<YControlGroup?>(null) }
    var resetConfirm by remember(moduleId) { mutableStateOf(false) }
    var sampleDialog by remember { mutableStateOf(false) }
    var exampleText by remember { mutableStateOf("") }
    var previewSwitch by remember { mutableStateOf(true) }
    var previewSelected by remember { mutableStateOf(false) }
    val scopeName = modules.firstOrNull { it.first == moduleId }?.second
    val title = scopeName ?: stringResource(R.string.settings_title)
    val back: () -> Unit = { if (group != null) group = null else onBack() }

    BackHandler(enabled = group != null) { group = null }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val outcome = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(store.exportJson()) }
                } ?: error("Unable to open output file")
            }
            Toast.makeText(context, context.getString(
                if (outcome.isSuccess) R.string.settings_export_ok else R.string.settings_export_failed,
            ), Toast.LENGTH_LONG).show()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val outcome = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(4_096)
                        val value = StringBuilder()
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            value.append(buffer, 0, count)
                            require(value.length <= 262_144) { "Settings document too large" }
                        }
                        value.toString()
                    }
                } ?: error("Unable to open input file")
                store.importJson(json)
            }
            Toast.makeText(context, context.getString(
                if (outcome.isSuccess) R.string.settings_import_ok else R.string.settings_import_failed,
            ), Toast.LENGTH_LONG).show()
        }
    }

    YSettingsScaffold(
        title = if (group == null) title else stringResource(groupTitle(group!!)),
        subtitle = if (moduleId == null)
            stringResource(R.string.settings_global)
        else stringResource(R.string.settings_module_override),
    ) { padding ->
        YPageList(padding = padding) {
            item(key = "back") {
                YSecondaryButton(stringResource(R.string.settings_back), onClick = back)
            }
            if (group == null) {
                item(key = "intro") {
                    Text(
                        stringResource(R.string.appearance_catalog_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(YUiControlRegistry.groups.size, key = { YUiControlRegistry.groups[it].name }) { index ->
                    val family = YUiControlRegistry.groups[index]
                    YListItem(
                        title = stringResource(groupTitle(family)),
                        subtitle = stringResource(groupSubtitle(family)),
                        detail = stringResource(R.string.appearance_catalog_count, YUiControlRegistry.forGroup(family).size),
                        onClick = { group = family },
                        trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    )
                }
                if (moduleId == null) {
                    item(key = "modules") {
                        YSection(
                            title = stringResource(R.string.settings_modules),
                            subtitle = stringResource(R.string.appearance_modules_only),
                        ) {
                            modules.forEach { (id, name) ->
                                YListItem(
                                    title = name,
                                    subtitle = stringResource(R.string.appearance_module_override_hint),
                                    onClick = { onSelectModule(id) },
                                    trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                )
                            }
                        }
                    }
                    item(key = "backup") {
                        YSection(title = stringResource(R.string.settings_backup_title),
                            subtitle = stringResource(R.string.settings_backup_desc)) {
                            YHorizontalActions {
                                YSecondaryButton(
                                    text = stringResource(R.string.settings_export),
                                    onClick = { exportLauncher.launch("YSuite-appearance.json") },
                                )
                                YSecondaryButton(
                                    text = stringResource(R.string.settings_import),
                                    onClick = { importLauncher.launch(arrayOf("application/json","text/plain")) },
                                )
                            }
                        }
                    }
                }
                item(key = "reset") {
                    YSection(title = stringResource(R.string.settings_reset_title)) {
                        YSecondaryButton(
                            text = stringResource(if (moduleId == null)
                                R.string.settings_reset_global else R.string.settings_reset_module),
                            onClick = { resetConfirm = true },
                        )
                        Text(
                            stringResource(R.string.appearance_scope_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val family = group!!
                item(key = "family-intro") {
                    Text(
                        stringResource(groupSubtitle(family)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val defs = YUiControlRegistry.forGroup(family)
                items(defs.size, key = { defs[it].key.key }) { index ->
                    val definition = defs[index]
                    // Revision refreshes individual values on local/remote preference changes.
                    val value = store.value(definition.key, moduleId)
                    YSection(title = stringResource(settingTitle(definition.key))) {
                        AppearanceEditor(
                            definition = definition,
                            value = value,
                            onSet = { store.set(definition.key, it, moduleId); revision++ },
                        )
                        if (moduleId != null && store.isOverridden(definition.key, moduleId)) {
                            YSecondaryButton(
                                text = stringResource(R.string.settings_inherit_global),
                                onClick = { store.inherit(definition.key, moduleId); revision++ },
                            )
                        }
                    }
                }
                item(key = "preview") {
                    YSection(
                        title = stringResource(R.string.appearance_preview_title),
                        subtitle = stringResource(R.string.appearance_preview_desc),
                    ) {
                        YTheme(appearanceOverride = store.appearance(moduleId)) {
                        when (family) {
                            YControlGroup.BUTTONS, YControlGroup.THEME, YControlGroup.TYPOGRAPHY -> {
                                YHorizontalActions {
                                    YPrimaryButton(stringResource(R.string.appearance_sample_primary), onClick = {})
                                    YSecondaryButton(stringResource(R.string.appearance_sample_secondary), onClick = {})
                                }
                            }
                            YControlGroup.LISTS, YControlGroup.LAYOUT -> {
                                YListItem(
                                    title = stringResource(R.string.appearance_sample_row),
                                    subtitle = stringResource(R.string.appearance_preview_desc),
                                    selected = previewSelected,
                                    onClick = { previewSelected = !previewSelected },
                                )
                            }
                            YControlGroup.CARDS -> {
                                YCard {
                                    Text(stringResource(R.string.appearance_sample_card), style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.appearance_preview_desc))
                                }
                            }
                            YControlGroup.INPUTS -> {
                                YUiOutlinedTextField(
                                    value = exampleText,
                                    onValueChange = { exampleText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.appearance_sample_input)) },
                                )
                            }
                            YControlGroup.SWITCHES -> {
                                YSwitchItem(
                                    title = stringResource(R.string.appearance_sample_switch),
                                    checked = previewSwitch,
                                    onCheckedChange = { previewSwitch = it },
                                )
                            }
                            YControlGroup.DIALOGS -> {
                                YPrimaryButton(stringResource(R.string.appearance_sample_dialog), onClick = { sampleDialog = true })
                            }
                            YControlGroup.NAVIGATION -> {
                                YUiNavigationDrawerItem(
                                    label = { Text(stringResource(R.string.appearance_sample_navigation)) },
                                    selected = previewSelected,
                                    onClick = { previewSelected = !previewSelected },
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
    if (resetConfirm) {
        YUiAlertDialog(
            onDismissRequest = { resetConfirm = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                YDialogConfirmButton(stringResource(R.string.settings_reset_action), onClick = {
                    store.reset(moduleId)
                    revision++
                    resetConfirm = false
                }, dangerous = true)
            },
            dismissButton = { YDialogDismissButton(stringResource(R.string.settings_cancel)) { resetConfirm = false } },
        )
    }
    if (sampleDialog) {
        YUiAlertDialog(
            onDismissRequest = { sampleDialog = false },
            title = { Text(stringResource(R.string.appearance_sample_dialog)) },
            text = { Text(stringResource(R.string.appearance_preview_desc)) },
            confirmButton = {
                YDialogConfirmButton(stringResource(R.string.settings_cancel), onClick = { sampleDialog = false })
            },
        )
    }
}

@Composable
private fun AppearanceEditor(definition: YControlDefinition, value: String, onSet: (String) -> Unit) {
    when (definition.kind) {
        YControlKind.BOOLEAN -> YSwitchItem(
            title = stringResource(settingTitle(definition.key)),
            checked = value == "true",
            onCheckedChange = { onSet(it.toString()) },
        )
        YControlKind.CHOICE -> Column {
            definition.choices.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { onSet(option) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YUiRadioButton(selected = option == value, onClick = { onSet(option) })
                    Text(stringResource(choiceTitle(definition.key, option)))
                }
            }
        }
        YControlKind.RANGE -> {
            val numeric = value.toIntOrNull() ?: definition.key.default.toInt()
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(settingTitle(definition.key)), modifier = Modifier.weight(1f))
                    Text(numeric.toString() + if (definition.key == YSettingKey.FONT_PERCENT) "%" else " dp",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                YUiSlider(
                    value = numeric.toFloat(),
                    onValueChange = { raw ->
                        val count = ((raw - definition.minimum) / definition.step).roundToInt()
                        onSet((definition.minimum + count * definition.step)
                            .coerceIn(definition.minimum, definition.maximum).toString())
                    },
                    valueRange = definition.minimum.toFloat()..definition.maximum.toFloat(),
                )
            }
        }
    }
}

private fun groupTitle(group: YControlGroup): Int = when (group) {
    YControlGroup.THEME -> R.string.appearance_group_theme
    YControlGroup.TYPOGRAPHY -> R.string.appearance_group_typography
    YControlGroup.LAYOUT -> R.string.appearance_group_layout
    YControlGroup.BUTTONS -> R.string.appearance_group_buttons
    YControlGroup.LISTS -> R.string.appearance_group_lists
    YControlGroup.CARDS -> R.string.appearance_group_cards
    YControlGroup.INPUTS -> R.string.appearance_group_inputs
    YControlGroup.SWITCHES -> R.string.appearance_group_switches
    YControlGroup.DIALOGS -> R.string.appearance_group_dialogs
    YControlGroup.NAVIGATION -> R.string.appearance_group_navigation
}

private fun groupSubtitle(group: YControlGroup): Int = when (group) {
    YControlGroup.THEME -> R.string.appearance_group_theme_desc
    YControlGroup.TYPOGRAPHY -> R.string.appearance_group_typography_desc
    YControlGroup.LAYOUT -> R.string.appearance_group_layout_desc
    YControlGroup.BUTTONS -> R.string.appearance_group_buttons_desc
    YControlGroup.LISTS -> R.string.appearance_group_lists_desc
    YControlGroup.CARDS -> R.string.appearance_group_cards_desc
    YControlGroup.INPUTS -> R.string.appearance_group_inputs_desc
    YControlGroup.SWITCHES -> R.string.appearance_group_switches_desc
    YControlGroup.DIALOGS -> R.string.appearance_group_dialogs_desc
    YControlGroup.NAVIGATION -> R.string.appearance_group_navigation_desc
}

private fun settingTitle(key: YSettingKey): Int = when (key) {
    YSettingKey.THEME -> R.string.settings_theme_title
    YSettingKey.DYNAMIC_COLOR -> R.string.settings_dynamic_title
    YSettingKey.DENSITY -> R.string.settings_density_title
    YSettingKey.BUTTON_RADIUS -> R.string.settings_button_radius
    YSettingKey.BUTTON_PADDING -> R.string.settings_button_padding
    YSettingKey.CONTROL_GAP -> R.string.settings_control_gap
    YSettingKey.FONT_PERCENT -> R.string.settings_font_scale
    YSettingKey.ACCENT -> R.string.appearance_accent
    YSettingKey.BUTTON_HEIGHT -> R.string.appearance_button_height
    YSettingKey.BUTTON_VERTICAL_PADDING -> R.string.appearance_button_vertical_padding
    YSettingKey.ROW_HEIGHT -> R.string.appearance_row_height
    YSettingKey.ROW_HORIZONTAL_PADDING -> R.string.appearance_row_horizontal_padding
    YSettingKey.ROW_VERTICAL_PADDING -> R.string.appearance_row_vertical_padding
    YSettingKey.CARD_RADIUS -> R.string.appearance_card_radius
    YSettingKey.CARD_PADDING -> R.string.appearance_card_padding
    YSettingKey.FIELD_RADIUS -> R.string.appearance_field_radius
    YSettingKey.DIALOG_RADIUS -> R.string.appearance_dialog_radius
    YSettingKey.SWITCH_SLOT_WIDTH -> R.string.appearance_switch_slot_width
    YSettingKey.ICON_TOUCH_TARGET -> R.string.appearance_icon_touch_target
    YSettingKey.NAV_RADIUS -> R.string.appearance_nav_radius
    YSettingKey.SCREEN_PADDING -> R.string.appearance_screen_padding
    YSettingKey.SECTION_SPACING -> R.string.appearance_section_spacing
    YSettingKey.HOME_SWIPE_PIN -> R.string.settings_swipe_pin
    YSettingKey.HOME_STATUS -> R.string.settings_home_status
}

private fun choiceTitle(key: YSettingKey, choice: String): Int = when (key) {
    YSettingKey.THEME -> when (choice) {
        "light" -> R.string.settings_theme_light
        "dark" -> R.string.settings_theme_dark
        else -> R.string.settings_theme_system
    }
    YSettingKey.DENSITY -> when (choice) {
        "compact" -> R.string.settings_density_compact
        "comfortable" -> R.string.settings_density_comfortable
        else -> R.string.settings_density_standard
    }
    YSettingKey.ACCENT -> when (choice) {
        "blue" -> R.string.appearance_accent_blue
        "teal" -> R.string.appearance_accent_teal
        "green" -> R.string.appearance_accent_green
        "purple" -> R.string.appearance_accent_purple
        "orange" -> R.string.appearance_accent_orange
        else -> R.string.appearance_accent_default
    }
    else -> R.string.appearance_accent_default
}
