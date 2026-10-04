package com.yagay.ysuite.settings

enum class AppThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val languageTag: String? = null,
)
