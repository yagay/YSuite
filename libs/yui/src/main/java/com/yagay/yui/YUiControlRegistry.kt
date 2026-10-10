package com.yagay.yui

/** Shared catalog for visual configuration, not a second feature-settings framework.
 * One option changes every consumer of the corresponding YUI component.
 * Sensitive functional switches (Root, hooks, NFC, download behavior) are excluded.
 */
enum class YControlGroup { THEME, TYPOGRAPHY, LAYOUT, BUTTONS, LISTS, CARDS, INPUTS, SWITCHES, DIALOGS, NAVIGATION, OVERLAYS }

enum class YControlKind { CHOICE, BOOLEAN, RANGE, TEXT }

data class YControlDefinition(
    val key: YSettingKey,
    val group: YControlGroup,
    val kind: YControlKind,
    val minimum: Int = 0,
    val maximum: Int = 0,
    val step: Int = 1,
    val choices: List<String> = emptyList(),
) {
    init {
        if (kind == YControlKind.RANGE) {
            require(minimum <= maximum && step > 0)
            require(minimum == key.minimum && maximum == key.maximum)
        }
        if (kind == YControlKind.CHOICE) require(choices.isNotEmpty())
        if (kind == YControlKind.BOOLEAN) require(key.default in setOf("true", "false"))
        require(key.validate(key.default) == key.default)
    }
}

object YUiControlRegistry {
    /** Numeric limits are defined once in YSettingKey, including persistence validation. */
    private fun range(key: YSettingKey, group: YControlGroup): YControlDefinition =
        YControlDefinition(key, group, YControlKind.RANGE,
            minimum = requireNotNull(key.minimum), maximum = requireNotNull(key.maximum))

    val definitions: List<YControlDefinition> = listOf(
        YControlDefinition(YSettingKey.THEME, YControlGroup.THEME, YControlKind.CHOICE, choices = listOf("system","light","dark")),
        YControlDefinition(YSettingKey.ACCENT, YControlGroup.THEME, YControlKind.CHOICE, choices = listOf("default","blue","teal","green","purple","orange")),
        YControlDefinition(YSettingKey.DYNAMIC_COLOR, YControlGroup.THEME, YControlKind.BOOLEAN),
        range(YSettingKey.FONT_PERCENT, YControlGroup.TYPOGRAPHY),
        YControlDefinition(YSettingKey.DENSITY, YControlGroup.LAYOUT, YControlKind.CHOICE, choices = listOf("compact","standard","comfortable")),
        range(YSettingKey.SCREEN_PADDING, YControlGroup.LAYOUT),
        range(YSettingKey.PAGE_VERTICAL_PADDING, YControlGroup.LAYOUT),
        range(YSettingKey.SECTION_SPACING, YControlGroup.LAYOUT),
        range(YSettingKey.CONTROL_GAP, YControlGroup.LAYOUT),
        range(YSettingKey.BUTTON_HEIGHT, YControlGroup.BUTTONS),
        range(YSettingKey.BUTTON_RADIUS, YControlGroup.BUTTONS),
        range(YSettingKey.BUTTON_PADDING, YControlGroup.BUTTONS),
        range(YSettingKey.BUTTON_VERTICAL_PADDING, YControlGroup.BUTTONS),
        range(YSettingKey.ICON_TOUCH_TARGET, YControlGroup.BUTTONS),
        range(YSettingKey.ICON_VISUAL_SIZE, YControlGroup.BUTTONS),
        range(YSettingKey.ROW_HEIGHT, YControlGroup.LISTS),
        range(YSettingKey.LIST_ICON_SIZE, YControlGroup.LISTS),
        range(YSettingKey.ROW_HORIZONTAL_PADDING, YControlGroup.LISTS),
        range(YSettingKey.ROW_VERTICAL_PADDING, YControlGroup.LISTS),
        range(YSettingKey.CARD_RADIUS, YControlGroup.CARDS),
        range(YSettingKey.CARD_PADDING, YControlGroup.CARDS),
        range(YSettingKey.FIELD_RADIUS, YControlGroup.INPUTS),
        range(YSettingKey.SWITCH_SLOT_WIDTH, YControlGroup.SWITCHES),
        range(YSettingKey.DIALOG_RADIUS, YControlGroup.DIALOGS),
        range(YSettingKey.NAV_RADIUS, YControlGroup.NAVIGATION),
        range(YSettingKey.TOOLBAR_HEIGHT, YControlGroup.NAVIGATION),
        range(YSettingKey.FLOAT_ICON_ALPHA, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_ICON_SIZE, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_EDGE_VISIBLE, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_BORDER_WIDTH, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_TRAIL_ALPHA, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_TRAIL_WIDTH, YControlGroup.OVERLAYS),
        range(YSettingKey.FLOAT_MENU_COUNT, YControlGroup.OVERLAYS),
        YControlDefinition(YSettingKey.FLOAT_BORDER_COLOR, YControlGroup.OVERLAYS,
            YControlKind.CHOICE, choices = listOf("blue", "green", "cyan", "purple", "orange", "red", "white")),
        YControlDefinition(YSettingKey.FLOAT_ICON_STYLE, YControlGroup.OVERLAYS,
            YControlKind.CHOICE, choices = listOf("blue", "dark", "light", "custom", "slideshow")),
        YControlDefinition(YSettingKey.FLOAT_TRAIL_STYLE, YControlGroup.OVERLAYS,
            YControlKind.CHOICE, choices = listOf("round", "square", "enhanced")),
        YControlDefinition(YSettingKey.FLOAT_TRAIL_GRADIENT, YControlGroup.OVERLAYS,
            YControlKind.BOOLEAN),
        YControlDefinition(YSettingKey.FLOAT_BORDER_VISIBLE, YControlGroup.OVERLAYS,
            YControlKind.BOOLEAN),
        YControlDefinition(YSettingKey.FLOAT_TRAIL_VISIBLE, YControlGroup.OVERLAYS,
            YControlKind.BOOLEAN),
        YControlDefinition(YSettingKey.FLOAT_TRAIL_COLORS, YControlGroup.OVERLAYS,
            YControlKind.TEXT),
    )
    val groups: List<YControlGroup> = YControlGroup.entries
    fun forGroup(group: YControlGroup): List<YControlDefinition> = definitions.filter { it.group == group }
}
