package com.yagay.YSuite

import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yagay.yui.YAppearanceStore
import com.yagay.yui.YDialogConfirmButton
import com.yagay.yui.YDialogDismissButton
import com.yagay.yui.YHorizontalActions
import com.yagay.yui.YPageList
import com.yagay.yui.YPrimaryButton
import com.yagay.yui.YSecondaryButton
import com.yagay.yui.YSection
import com.yagay.yui.YSettingKey
import com.yagay.yui.YSettingsScaffold
import com.yagay.yui.YSwitchItem
import com.yagay.yui.YUiRadioButton
import com.yagay.yui.YUiSlider
import com.yagay.yui.YUiAlertDialog
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/** Central appearance settings; feature-owned functional settings stay in their own modules. */
@Composable
internal fun SuiteSettingsScreen(
    modules: List<Pair<String, String>>,
    moduleId: String?,
    onBack: () -> Unit,
    onSelectModule: (String) -> Unit,
    onOpenManagement: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context.applicationContext) { YAppearanceStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val unsubscribe = store.listen { revision++ }
        onDispose(unsubscribe)
    }
    // Read revision so SharedPreferences updates refresh the complete screen immediately.
    val appearance = remember(revision, moduleId) { store.appearance(moduleId) }
    val scopeName = modules.firstOrNull { it.first == moduleId }?.second
    var confirmReset by remember(moduleId) { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val outcome = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(store.exportJson()) }
                } ?: error("Unable to open document")
            }
            Toast.makeText(
                context,
                context.getString(if (outcome.isSuccess) R.string.settings_export_ok else R.string.settings_export_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val outcome = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        // Stop before allocating an unbounded document.
                        val content = CharArray(262_145)
                        val read = reader.read(content)
                        if (read == content.size) error("Settings document too large")
                        String(content, 0, read.coerceAtLeast(0))
                    }
                } ?: error("Unable to open document")
                store.importJson(json)
            }
            Toast.makeText(
                context,
                context.getString(if (outcome.isSuccess) R.string.settings_import_ok else R.string.settings_import_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    YSettingsScaffold(
        title = scopeName ?: stringResource(R.string.settings_title),
        subtitle = if (moduleId == null) stringResource(R.string.settings_global) else stringResource(R.string.settings_module_override),
    ) { padding ->
        YPageList(padding = padding) {
            item {
                YSecondaryButton(text = stringResource(R.string.settings_back), onClick = onBack)
            }
            item {
                YSection(
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                ) {
                    SettingsChoices(
                        choices = listOf(
                            "system" to stringResource(R.string.settings_theme_system),
                            "light" to stringResource(R.string.settings_theme_light),
                            "dark" to stringResource(R.string.settings_theme_dark),
                        ),
                        selected = appearance.theme,
                        onSelect = { store.set(YSettingKey.THEME, it, moduleId) },
                    )
                    YSwitchItem(
                        title = stringResource(R.string.settings_dynamic_title),
                        subtitle = stringResource(R.string.settings_dynamic_subtitle),
                        checked = appearance.dynamicColor,
                        onCheckedChange = { store.set(YSettingKey.DYNAMIC_COLOR, it.toString(), moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.THEME, revision)
                    InheritSetting(moduleId, store, YSettingKey.DYNAMIC_COLOR, revision)
                }
            }
            item {
                YSection(
                    title = stringResource(R.string.settings_density_title),
                    subtitle = stringResource(R.string.settings_density_subtitle),
                ) {
                    SettingsChoices(
                        choices = listOf(
                            "compact" to stringResource(R.string.settings_density_compact),
                            "standard" to stringResource(R.string.settings_density_standard),
                            "comfortable" to stringResource(R.string.settings_density_comfortable),
                        ),
                        selected = appearance.density,
                        onSelect = { store.set(YSettingKey.DENSITY, it, moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.DENSITY, revision)
                }
            }
            item {
                YSection(
                    title = stringResource(R.string.settings_sizing_title),
                    subtitle = stringResource(R.string.settings_sizing_subtitle),
                ) {
                    SettingsSlider(
                        label = stringResource(R.string.settings_button_radius),
                        value = appearance.buttonRadiusDp,
                        range = 0..32,
                        step = 2,
                        onChange = { store.set(YSettingKey.BUTTON_RADIUS, it.toString(), moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.BUTTON_RADIUS, revision)
                    SettingsSlider(
                        label = stringResource(R.string.settings_button_padding),
                        value = appearance.buttonPaddingHorizontalDp,
                        range = 8..32,
                        step = 2,
                        onChange = { store.set(YSettingKey.BUTTON_PADDING, it.toString(), moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.BUTTON_PADDING, revision)
                    SettingsSlider(
                        label = stringResource(R.string.settings_control_gap),
                        value = appearance.controlGapDp,
                        range = 4..24,
                        step = 1,
                        onChange = { store.set(YSettingKey.CONTROL_GAP, it.toString(), moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.CONTROL_GAP, revision)
                    SettingsSlider(
                        label = stringResource(R.string.settings_font_scale),
                        value = appearance.fontPercent,
                        range = 85..130,
                        step = 5,
                        suffix = "%",
                        onChange = { store.set(YSettingKey.FONT_PERCENT, it.toString(), moduleId) },
                    )
                    InheritSetting(moduleId, store, YSettingKey.FONT_PERCENT, revision)
                }
            }
            if (moduleId == null) {
                item {
                    YSection(
                        title = stringResource(R.string.settings_home_title),
                        subtitle = stringResource(R.string.settings_home_subtitle),
                    ) {
                        YSwitchItem(
                            title = stringResource(R.string.settings_swipe_pin),
                            checked = appearance.homeSwipePin,
                            onCheckedChange = { store.set(YSettingKey.HOME_SWIPE_PIN, it.toString()) },
                        )
                        YSwitchItem(
                            title = stringResource(R.string.settings_home_status),
                            checked = appearance.homeStatusVisible,
                            onCheckedChange = { store.set(YSettingKey.HOME_STATUS, it.toString()) },
                        )
                    }
                }
                item {
                    YSection(
                        title = stringResource(R.string.settings_modules),
                        subtitle = stringResource(R.string.settings_modules_help),
                    ) {
                        modules.forEach { (id, name) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickable { onSelectModule(id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    YSection(
                        title = stringResource(R.string.settings_advanced_title),
                        subtitle = stringResource(R.string.settings_advanced_desc),
                    ) {
                        YSecondaryButton(
                            text = stringResource(R.string.home_manage),
                            onClick = onOpenManagement,
                        )
                    }
                }
                item {
                    YSection(
                        title = stringResource(R.string.settings_backup_title),
                        subtitle = stringResource(R.string.settings_backup_desc),
                    ) {
                        YHorizontalActions {
                            YSecondaryButton(
                                text = stringResource(R.string.settings_export),
                                onClick = { exportLauncher.launch("YSuite-appearance.json") },
                            )
                            YSecondaryButton(
                                text = stringResource(R.string.settings_import),
                                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            )
                        }
                    }
                }
            }
            item {
                YSection(title = stringResource(R.string.settings_reset_title)) {
                    YSecondaryButton(
                        text = stringResource(if (moduleId == null) R.string.settings_reset_global else R.string.settings_reset_module),
                        onClick = { confirmReset = true },
                    )
                    Text(
                        stringResource(R.string.settings_scope_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmReset) {
        YUiAlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_confirm)) },
            confirmButton = {
                YDialogConfirmButton(stringResource(R.string.settings_reset_action), onClick = {
                    store.reset(moduleId)
                    confirmReset = false
                }, dangerous = true)
            },
            dismissButton = {
                YDialogDismissButton(stringResource(R.string.settings_cancel), onClick = { confirmReset = false })
            },
        )
    }
}

@Composable
private fun SettingsChoices(
    choices: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        choices.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { onSelect(value) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                YUiRadioButton(selected = value == selected, onClick = { onSelect(value) })
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String = " dp",
    onChange: (Int) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value.toString() + suffix, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        YUiSlider(
            value = value.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw.toInt() - range.first + step / 2) / step * step + range.first
                onChange(snapped.coerceIn(range))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

@Composable
private fun InheritSetting(
    moduleId: String?,
    store: YAppearanceStore,
    key: YSettingKey,
    revision: Int,
) {
    if (moduleId == null) return
    // 'revision' intentionally participates in composition after an external preference change.
    val overridden = remember(store, moduleId, key, revision) { store.isOverridden(key, moduleId) }
    if (overridden) {
        YSecondaryButton(
            text = stringResource(R.string.settings_inherit_global),
            onClick = { store.inherit(key, moduleId) },
        )
    }
}
