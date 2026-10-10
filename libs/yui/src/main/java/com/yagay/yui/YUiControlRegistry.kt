package com.yagay.yui

/** Shared catalog for visual configuration, not a second feature-settings framework.
 * One option changes every consumer of the corresponding YUI component.
 * Sensitive functional switches (Root, hooks, NFC, download behavior) are excluded.
 */
enum class YControlGroup { THEME, TYPOGRAPHY, LAYOUT, BUTTONS, LISTS, CARDS, INPUTS, SWITCHES, DIALOGS, NAVIGATION }

enum class YControlKind { CHOICE, BOOLEAN, RANGE }

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
        if (kind == YControlKind.RANGE) require(minimum <= maximum && step > 0)
        if (kind == YControlKind.CHOICE) require(choices.isNotEmpty())
        if (kind == YControlKind.BOOLEAN) require(key.default in setOf("true", "false"))
        require(key.validate(key.default) == key.default)
    }
}

object YUiControlRegistry {
    val definitions: List<YControlDefinition> = listOf(
        YControlDefinition(YSettingKey.THEME, YControlGroup.THEME, YControlKind.CHOICE, choices = listOf("system","light","dark")),
        YControlDefinition(YSettingKey.ACCENT, YControlGroup.THEME, YControlKind.CHOICE, choices = listOf("default","blue","teal","green","purple","orange")),
        YControlDefinition(YSettingKey.DYNAMIC_COLOR, YControlGroup.THEME, YControlKind.BOOLEAN),
        YControlDefinition(YSettingKey.FONT_PERCENT, YControlGroup.TYPOGRAPHY, YControlKind.RANGE,85,130,5),
        YControlDefinition(YSettingKey.DENSITY, YControlGroup.LAYOUT, YControlKind.CHOICE, choices = listOf("compact","standard","comfortable")),
        YControlDefinition(YSettingKey.SCREEN_PADDING, YControlGroup.LAYOUT, YControlKind.RANGE,8,32,2),
        YControlDefinition(YSettingKey.SECTION_SPACING, YControlGroup.LAYOUT, YControlKind.RANGE,0,32,2),
        YControlDefinition(YSettingKey.CONTROL_GAP, YControlGroup.LAYOUT, YControlKind.RANGE,4,24,1),
        YControlDefinition(YSettingKey.BUTTON_HEIGHT, YControlGroup.BUTTONS, YControlKind.RANGE,48,72,2),
        YControlDefinition(YSettingKey.BUTTON_RADIUS, YControlGroup.BUTTONS, YControlKind.RANGE,0,32,2),
        YControlDefinition(YSettingKey.BUTTON_PADDING, YControlGroup.BUTTONS, YControlKind.RANGE,8,32,2),
        YControlDefinition(YSettingKey.BUTTON_VERTICAL_PADDING, YControlGroup.BUTTONS, YControlKind.RANGE,0,16,2),
        YControlDefinition(YSettingKey.ICON_TOUCH_TARGET, YControlGroup.BUTTONS, YControlKind.RANGE,48,72,2),
        YControlDefinition(YSettingKey.ROW_HEIGHT, YControlGroup.LISTS, YControlKind.RANGE,48,88,2),
        YControlDefinition(YSettingKey.ROW_HORIZONTAL_PADDING, YControlGroup.LISTS, YControlKind.RANGE,8,32,2),
        YControlDefinition(YSettingKey.ROW_VERTICAL_PADDING, YControlGroup.LISTS, YControlKind.RANGE,2,16,2),
        YControlDefinition(YSettingKey.CARD_RADIUS, YControlGroup.CARDS, YControlKind.RANGE,0,32,2),
        YControlDefinition(YSettingKey.CARD_PADDING, YControlGroup.CARDS, YControlKind.RANGE,8,32,2),
        YControlDefinition(YSettingKey.FIELD_RADIUS, YControlGroup.INPUTS, YControlKind.RANGE,0,32,2),
        YControlDefinition(YSettingKey.SWITCH_SLOT_WIDTH, YControlGroup.SWITCHES, YControlKind.RANGE,56,84,2),
        YControlDefinition(YSettingKey.DIALOG_RADIUS, YControlGroup.DIALOGS, YControlKind.RANGE,0,32,2),
        YControlDefinition(YSettingKey.NAV_RADIUS, YControlGroup.NAVIGATION, YControlKind.RANGE,0,32,2),
    )
    val groups: List<YControlGroup> = YControlGroup.entries
    fun forGroup(group: YControlGroup): List<YControlDefinition> = definitions.filter { it.group == group }
}
