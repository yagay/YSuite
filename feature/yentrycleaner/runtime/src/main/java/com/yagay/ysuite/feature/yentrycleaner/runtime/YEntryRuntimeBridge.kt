package com.yagay.ysuite.feature.yentrycleaner.runtime

object YEntryRuntimeBridge {
    const val GROUP = "yentrycleaner"
    const val KEY_MODE = "mode"
    const val KEY_HIDDEN_RULES = "hidden_rules"
    const val KEY_SHOWN_RULES = "shown_rules"
    const val KEY_OPEN_CUSTOM_DEFINITIONS = "open_custom_definitions"
    const val KEY_PRIORITIES = "priorities"
    const val KEY_DISABLED_COMPONENTS = "disabled_components"
    const val KEY_DIAGNOSTIC = "diagnostic"
    const val KEY_MANAGER_APP_ID = "manager_app_id"
    const val KEY_COMPONENT_DISCOVERY_PROTOCOL =
        "component_discovery_protocol"
    const val COMPONENT_DISCOVERY_PROTOCOL = 2

    fun ruleKey(
        surface: String,
        qualifier: String,
        packageName: String,
        className: String,
    ): String =
        listOf(
            surface,
            qualifier.ifBlank { "*" },
            packageName,
            className,
        ).joinToString("|")

    fun componentKey(
        user: Int,
        packageName: String,
        className: String,
    ): String =
        listOf(
            user.toString(),
            packageName,
            className,
        ).joinToString("|")
}
