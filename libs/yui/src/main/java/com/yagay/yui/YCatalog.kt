package com.yagay.yui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource

/** Living catalog for reviewing YUI without launching any feature module. */
@Composable
fun YComponentCatalogScreen() {
    var text by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(true) }
    var checkboxChecked by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableStateOf(3) }
    val title = stringResource(R.string.yui_catalog_title)
    val subtitle = stringResource(R.string.yui_catalog_subtitle)
    val primary = stringResource(R.string.yui_catalog_primary)
    val secondary = stringResource(R.string.yui_catalog_secondary)
    val success = stringResource(R.string.yui_catalog_success)
    val warning = stringResource(R.string.yui_catalog_warning)
    val error = stringResource(R.string.yui_catalog_error)
    val optionA = stringResource(R.string.yui_catalog_option_a)
    val optionB = stringResource(R.string.yui_catalog_option_b)

    YDashboardScaffold(title = title, subtitle = subtitle) { padding ->
        YPageList(padding = padding) {
            item { YSectionHeader(stringResource(R.string.yui_catalog_actions)) }
            item {
                YActionGroup(
                    listOf(
                        YActionSpec(primary, style = YActionStyle.PRIMARY, onClick = {}),
                        YActionSpec(secondary, style = YActionStyle.SECONDARY, onClick = {}),
                        YActionSpec(error, style = YActionStyle.DANGER, onClick = {}),
                    ),
                )
            }
            item { YSectionHeader(stringResource(R.string.yui_catalog_states)) }
            item { YNotice(success, tone = YNoticeTone.POSITIVE) }
            item { YNotice(warning, tone = YNoticeTone.WARNING) }
            item { YNotice(error, tone = YNoticeTone.ERROR) }
            item { YProgressItem(primary, progress = 0.62f, subtitle = secondary) }
            item { YSectionHeader(stringResource(R.string.yui_catalog_forms)) }
            item {
                YSwitchItem(
                    title = primary,
                    subtitle = secondary,
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
            item {
                YListItem(
                    title = optionA,
                    subtitle = secondary,
                    selected = selected == 0,
                    onClick = { selected = 0 },
                )
            }
            item {
                YTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.yui_catalog_field),
                )
            }
            item {
                YSegmentedControl(
                    value = selected,
                    options = listOf(YChoiceSpec(0, optionA), YChoiceSpec(1, optionB)),
                    onSelected = { selected = it },
                )
            }
            item {
                YCheckboxItem(
                    title = optionB,
                    subtitle = secondary,
                    checked = checkboxChecked,
                    onCheckedChange = { checkboxChecked = it },
                )
            }
            item {
                YRadioGroup(
                    value = selected,
                    options = listOf(YChoiceSpec(0, optionA), YChoiceSpec(1, optionB)),
                    onSelected = { selected = it },
                )
            }
            item {
                YIntSliderField(
                    title = stringResource(R.string.yui_catalog_field),
                    value = sliderValue,
                    range = 1..5,
                    onValueChange = { sliderValue = it },
                )
            }
            item {
                YTabBar(
                    tabs = listOf(YTabSpec("first", optionA), YTabSpec("second", optionB)),
                    selectedKey = if (selected == 0) "first" else "second",
                    onSelected = { selected = if (it.key == "first") 0 else 1 },
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun YCatalogLightPreview() {
    YTheme(dynamicColor = false, darkTheme = false) { YComponentCatalogScreen() }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun YCatalogDarkWidePreview() {
    YTheme(dynamicColor = false, darkTheme = true) { YComponentCatalogScreen() }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable
private fun YCatalogLargeFontPreview() {
    YTheme(dynamicColor = false, darkTheme = false) { YComponentCatalogScreen() }
}
