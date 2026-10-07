package com.yagay.ysuite.feature.ydownload

import android.content.Context
import com.yagay.ysuite.feature.ydownload.api.YDownloadBackend
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.yDownloadSettingsDataStore
    by preferencesDataStore(name = "ydownload_settings")

data class YDownloadSettings(
    val defaultBackend: YDownloadBackend =
        YDownloadBackend.System,
    val defaultTreeUri: String? = null,
    val maxConcurrentDownloads: Int = 3,
    val defaultThreadCount: Int = 4,
    val globalSpeedLimitBytesPerSecond: Long = 0L,
    val wifiOnly: Boolean = false,
    val autoResumeNetwork: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val defaultUserAgent: String = MOBILE_USER_AGENT,
) {
    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/121.0 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/121.0 Safari/537.36"
    }
}

class YDownloadSettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val defaultBackend =
            stringPreferencesKey("default_backend")
        val defaultTreeUri =
            stringPreferencesKey("default_tree_uri")
        val maxConcurrent =
            intPreferencesKey("max_concurrent")
        val defaultThreadCount =
            intPreferencesKey("default_thread_count")
        val speedLimit =
            longPreferencesKey("speed_limit")
        val wifiOnly =
            booleanPreferencesKey("wifi_only")
        val autoResumeNetwork =
            booleanPreferencesKey("auto_resume_network")
        val notifications =
            booleanPreferencesKey("notifications")
        val userAgent =
            stringPreferencesKey("user_agent")
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )

    val settings =
        context.yDownloadSettingsDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences ->
                YDownloadSettings(
                    defaultBackend =
                        runCatching {
                            YDownloadBackend.valueOf(
                                preferences[
                                    Keys.defaultBackend
                                ] ?: YDownloadBackend
                                    .System.name,
                            )
                        }.getOrDefault(
                            YDownloadBackend.System,
                        ),
                    defaultTreeUri =
                        preferences[Keys.defaultTreeUri]
                            ?.takeIf(String::isNotBlank),
                    maxConcurrentDownloads =
                        (preferences[Keys.maxConcurrent] ?: 3)
                            .coerceIn(1, 10),
                    defaultThreadCount =
                        (preferences[Keys.defaultThreadCount] ?: 4)
                            .coerceIn(1, 16),
                    globalSpeedLimitBytesPerSecond =
                        (preferences[Keys.speedLimit] ?: 0L)
                            .coerceAtLeast(0L),
                    wifiOnly =
                        preferences[Keys.wifiOnly] ?: false,
                    autoResumeNetwork =
                        preferences[Keys.autoResumeNetwork] ?: true,
                    notificationsEnabled =
                        preferences[Keys.notifications] ?: true,
                    defaultUserAgent =
                        preferences[Keys.userAgent]
                            ?: YDownloadSettings
                                .MOBILE_USER_AGENT,
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = YDownloadSettings(),
            )

    suspend fun setDefaultBackend(
        value: YDownloadBackend,
    ) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.defaultBackend] =
                value.name
        }
    }

    suspend fun setDefaultTreeUri(uri: String?) {
        context.yDownloadSettingsDataStore.edit {
            if (uri.isNullOrBlank()) {
                it.remove(Keys.defaultTreeUri)
            } else {
                it[Keys.defaultTreeUri] = uri
            }
        }
    }

    suspend fun setMaxConcurrentDownloads(value: Int) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.maxConcurrent] = value.coerceIn(1, 10)
        }
    }

    suspend fun setDefaultThreadCount(value: Int) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.defaultThreadCount] = value.coerceIn(1, 16)
        }
    }

    suspend fun setGlobalSpeedLimit(value: Long) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.speedLimit] = value.coerceAtLeast(0L)
        }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.wifiOnly] = value
        }
    }

    suspend fun setAutoResumeNetwork(value: Boolean) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.autoResumeNetwork] = value
        }
    }

    suspend fun setNotificationsEnabled(value: Boolean) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.notifications] = value
        }
    }

    suspend fun setDefaultUserAgent(value: String) {
        context.yDownloadSettingsDataStore.edit {
            it[Keys.userAgent] =
                value.ifBlank {
                    YDownloadSettings.MOBILE_USER_AGENT
                }
        }
    }
}
