package com.yagay.ysuite.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.ySuiteSettingsDataStore by preferencesDataStore(name = "ysuite_settings")

class DataStoreAppSettingsRepository(
    private val context: Context,
) : AppSettingsRepository {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor =
            booleanPreferencesKey("dynamic_color")
        val languageTag = stringPreferencesKey("language_tag")
    }

    override val settings: Flow<AppSettings> =
        context.ySuiteSettingsDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                AppSettings(
                    themeMode = preferences[Keys.themeMode]
                        ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
                        ?: AppThemeMode.System,
                    dynamicColorEnabled =
                        preferences[Keys.dynamicColor] ?: true,
                    languageTag = preferences[Keys.languageTag]?.takeIf(String::isNotBlank),
                )
            }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        context.ySuiteSettingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode.name
        }
    }

    override suspend fun setDynamicColorEnabled(
        enabled: Boolean,
    ) {
        context.ySuiteSettingsDataStore.edit { preferences ->
            preferences[Keys.dynamicColor] = enabled
        }
    }

    override suspend fun setLanguageTag(languageTag: String?) {
        context.ySuiteSettingsDataStore.edit { preferences ->
            if (languageTag.isNullOrBlank()) {
                preferences.remove(Keys.languageTag)
            } else {
                preferences[Keys.languageTag] = languageTag
            }
        }
    }
}
