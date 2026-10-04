package com.yagay.ysuite.settings

import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: AppThemeMode)

    suspend fun setLanguageTag(languageTag: String?)
}
