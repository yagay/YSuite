package com.yagay.ysuite.feature.ydownload

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.HookGateway
import org.json.JSONObject

data class YDownloadSystemPatchSettings(
    val enabled: Boolean = true,
    val allowMetered: Boolean = true,
    val allowRoaming: Boolean = true,
    val requireCharging: Boolean = false,
    val requireDeviceIdle: Boolean = false,
    val forceCompletionNotification: Boolean = false,
)

class YDownloadSystemPatchStore(
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

    fun load(): YDownloadSystemPatchSettings =
        YDownloadSystemPatchSettings(
            enabled =
                prefs.getBoolean(
                    "enabled",
                    true,
                ),
            allowMetered =
                prefs.getBoolean(
                    "allow_metered",
                    true,
                ),
            allowRoaming =
                prefs.getBoolean(
                    "allow_roaming",
                    true,
                ),
            requireCharging =
                prefs.getBoolean(
                    "require_charging",
                    false,
                ),
            requireDeviceIdle =
                prefs.getBoolean(
                    "require_device_idle",
                    false,
                ),
            forceCompletionNotification =
                prefs.getBoolean(
                    "force_completion_notification",
                    false,
                ),
        )

    suspend fun save(
        settings: YDownloadSystemPatchSettings,
    ): Outcome<Unit> {
        prefs.edit()
            .putBoolean(
                "enabled",
                settings.enabled,
            )
            .putBoolean(
                "allow_metered",
                settings.allowMetered,
            )
            .putBoolean(
                "allow_roaming",
                settings.allowRoaming,
            )
            .putBoolean(
                "require_charging",
                settings.requireCharging,
            )
            .putBoolean(
                "require_device_idle",
                settings.requireDeviceIdle,
            )
            .putBoolean(
                "force_completion_notification",
                settings.forceCompletionNotification,
            )
            .apply()
        return sync(settings)
    }

    suspend fun sync(
        settings:
            YDownloadSystemPatchSettings =
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
                        "allowMetered",
                        settings.allowMetered,
                    )
                    .put(
                        "allowRoaming",
                        settings.allowRoaming,
                    )
                    .put(
                        "requireCharging",
                        settings.requireCharging,
                    )
                    .put(
                        "requireDeviceIdle",
                        settings.requireDeviceIdle,
                    )
                    .put(
                        "forceCompletionNotification",
                        settings.forceCompletionNotification,
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
        val targets = linkedSetOf<String>()

        runCatching {
            pm.queryBroadcastReceivers(
                Intent(
                    DownloadManager
                        .ACTION_DOWNLOAD_COMPLETE,
                ),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrDefault(
            emptyList(),
        ).forEach {
            it.activityInfo
                ?.packageName
                ?.takeIf(String::isNotBlank)
                ?.let(targets::add)
        }

        runCatching {
            pm.queryIntentActivities(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://example.com",
                    ),
                ).addCategory(
                    Intent.CATEGORY_BROWSABLE,
                ),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrDefault(
            emptyList(),
        ).forEach {
            it.activityInfo
                ?.packageName
                ?.takeIf(String::isNotBlank)
                ?.let(targets::add)
        }

        listOf(
            "com.google.android.apps.nbu.files",
            "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer",
            "com.mi.android.fileexplorer",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.discord",
            "com.reddit.frontpage",
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

        targets.removeAll(
            setOf(
                appContext.packageName,
                "android",
                "com.android.systemui",
                "com.android.providers.downloads",
                "com.google.android.gms",
                "com.android.vending",
            ),
        )
        return targets
    }

    companion object {
        const val PREFS = "ydownload_patch"
        const val CONFIG_KEY = "config"
    }
}
