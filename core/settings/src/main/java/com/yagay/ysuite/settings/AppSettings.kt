package com.yagay.ysuite.settings

enum class AppThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val dynamicColorEnabled: Boolean = true,
    val languageTag: String? = null,
)
