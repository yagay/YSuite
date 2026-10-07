package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.HookGateway
import org.json.JSONObject

data class YFilesSystemPatchSettings(
    val enabled: Boolean = true,
    val initialUri: String = "",
    val localOnly: Boolean = false,
    val allowMultiple: Boolean = false,
    val defaultSort: String = SORT_SYSTEM,
) {
    companion object {
        const val SORT_SYSTEM = "system"
        const val SORT_NAME = "name"
        const val SORT_DATE = "date"
        const val SORT_SIZE = "size"
        const val SORT_TYPE = "type"

        val validSorts =
            listOf(
                SORT_SYSTEM,
                SORT_NAME,
                SORT_DATE,
                SORT_SIZE,
                SORT_TYPE,
            )
    }
}

class YFilesSystemPatchStore(
    context: Context,
    private val hooks: HookGateway,
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )

    fun load(): YFilesSystemPatchSettings =
        YFilesSystemPatchSettings(
            enabled =
                prefs.getBoolean(
                    "enabled",
                    true,
                ),
            initialUri =
                prefs.getString(
                    "initial_uri",
                    "",
                ).orEmpty(),
            localOnly =
                prefs.getBoolean(
                    "local_only",
                    false,
                ),
            allowMultiple =
                prefs.getBoolean(
                    "allow_multiple",
                    false,
                ),
            defaultSort =
                prefs.getString(
                    "default_sort",
                    YFilesSystemPatchSettings
                        .SORT_SYSTEM,
                )?.takeIf {
                    it in
                        YFilesSystemPatchSettings
                            .validSorts
                } ?: YFilesSystemPatchSettings
                    .SORT_SYSTEM,
        )

    suspend fun save(
        settings: YFilesSystemPatchSettings,
    ): Outcome<Unit> {
        val normalized =
            settings.copy(
                initialUri =
                    settings.initialUri.trim(),
                defaultSort =
                    settings.defaultSort
                        .takeIf {
                            it in
                                YFilesSystemPatchSettings
                                    .validSorts
                        } ?: YFilesSystemPatchSettings
                        .SORT_SYSTEM,
            )
        prefs.edit()
            .putBoolean(
                "enabled",
                normalized.enabled,
            )
            .putString(
                "initial_uri",
                normalized.initialUri,
            )
            .putBoolean(
                "local_only",
                normalized.localOnly,
            )
            .putBoolean(
                "allow_multiple",
                normalized.allowMultiple,
            )
            .putString(
                "default_sort",
                normalized.defaultSort,
            )
            .apply()
        return sync(normalized)
    }

    suspend fun sync(
        settings:
            YFilesSystemPatchSettings =
            load(),
    ): Outcome<Unit> =
        hooks.writeConfig(
            group = PREFS,
            key = CONFIG_KEY,
            value =
                JSONObject()
                    .put(
                        "enabled",
                        settings.enabled,
                    )
                    .put(
                        "initialUri",
                        settings.initialUri,
                    )
                    .put(
                        "localOnly",
                        settings.localOnly,
                    )
                    .put(
                        "allowMultiple",
                        settings.allowMultiple,
                    )
                    .put(
                        "defaultSort",
                        settings.defaultSort,
                    )
                    .toString(),
        )

    suspend fun requestRecommendedScope():
        Outcome<Unit> =
        when (
            val result =
                hooks.reload(
                    recommendedTargets(),
                )
        ) {
            is Outcome.Success -> sync()
            is Outcome.Failure -> result
        }

    fun recommendedTargets(): Set<String> {
        val pm = appContext.packageManager
        val targets =
            linkedSetOf(
                "com.android.documentsui",
                "com.google.android.documentsui",
            )

        listOf(
            Intent(
                Intent.ACTION_OPEN_DOCUMENT,
            ).apply {
                type = "*/*"
                addCategory(
                    Intent.CATEGORY_OPENABLE,
                )
            },
            Intent(
                Intent.ACTION_CREATE_DOCUMENT,
            ).apply {
                type = "*/*"
                addCategory(
                    Intent.CATEGORY_OPENABLE,
                )
            },
            Intent(
                Intent.ACTION_OPEN_DOCUMENT_TREE,
            ),
            Intent(
                Intent.ACTION_GET_CONTENT,
            ).apply {
                type = "*/*"
                addCategory(
                    Intent.CATEGORY_OPENABLE,
                )
            },
        ).forEach { intent ->
            runCatching {
                pm.queryIntentActivities(
                    intent,
                    PackageManager
                        .MATCH_DEFAULT_ONLY,
                )
            }.getOrDefault(
                emptyList(),
            ).forEach {
                it.activityInfo
                    ?.packageName
                    ?.takeIf(String::isNotBlank)
                    ?.let(targets::add)
            }
        }

        listOf(
            "com.android.settings",
            "com.android.chrome",
            "com.google.android.apps.chrome",
            "com.google.android.gm",
            "org.mozilla.firefox",
            "org.telegram.messenger",
            "com.whatsapp",
        ).forEach { packageName ->
            if (
                runCatching {
                    pm.getApplicationInfo(
                        packageName,
                        0,
                    )
                }.isSuccess
            ) {
                targets += packageName
            }
        }
        targets.remove(appContext.packageName)
        return targets
    }

    companion object {
        const val PREFS = "yfiles_patch"
        const val CONFIG_KEY = "config"
    }
}
