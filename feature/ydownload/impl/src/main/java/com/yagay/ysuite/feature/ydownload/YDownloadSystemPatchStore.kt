package com.yagay.ysuite.feature.ydownload

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.yagay.ysuite.common.Outcome
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

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
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )

    init {
        YDownloadPatchService.ensureRegistered()
    }

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
        YDownloadPatchService.writeConfig(
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
        Outcome<Unit> {
        val result =
            YDownloadPatchService.requestScope(
                recommendedTargets(),
            )
        return if (result is Outcome.Success) {
            sync()
        } else {
            result
        }
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
    }
}

private const val ERROR_HOOK_SERVICE_UNAVAILABLE =
    "hook_service_unavailable"
private const val ERROR_HOOK_CONFIG_WRITE_FAILED =
    "hook_config_write_failed"

private object YDownloadPatchService :
    XposedServiceHelper.OnServiceListener {
    @Volatile
    private var service: XposedService? = null

    @Volatile
    private var registered = false

    @Synchronized
    fun ensureRegistered() {
        if (registered) return
        registered = true
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(
        service: XposedService,
    ) {
        this.service = service
    }

    override fun onServiceDied(
        service: XposedService,
    ) {
        if (this.service === service) {
            this.service = null
        }
    }

    fun writeConfig(
        payload: String,
    ): Outcome<Unit> {
        ensureRegistered()
        val current =
            service ?: return Outcome.Failure(
                code = "hook_service_unavailable",
                message =
                    ERROR_HOOK_SERVICE_UNAVAILABLE,
                retryable = true,
            )
        return runCatching {
            current.getRemotePreferences(
                YDownloadSystemPatchStore.PREFS,
            ).edit()
                .putString(
                    "config",
                    payload,
                )
                .commit()
        }.fold(
            onSuccess = {
                if (it) {
                    Outcome.Success(Unit)
                } else {
                    Outcome.Failure(
                        code =
                            "hook_config_write_failed",
                        message =
                            ERROR_HOOK_CONFIG_WRITE_FAILED,
                        retryable = true,
                    )
                }
            },
            onFailure = {
                Outcome.Failure(
                    code =
                        "hook_config_write_failed",
                    message =
                        it.message
                            ?: ERROR_HOOK_CONFIG_WRITE_FAILED,
                    cause = it,
                    retryable = true,
                )
            },
        )
    }

    suspend fun requestScope(
        packages: Set<String>,
    ): Outcome<Unit> {
        ensureRegistered()
        val current =
            service ?: return Outcome.Failure(
                code = "hook_service_unavailable",
                message =
                    "LSPosed service is not connected",
                retryable = true,
            )
        val requested =
            packages
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        if (requested.isEmpty()) {
            return Outcome.Success(Unit)
        }

        val existing =
            runCatching {
                current.scope.toSet()
            }.getOrDefault(emptySet())
        val missing =
            requested.filterNot {
                it in existing
            }
        if (missing.isEmpty()) {
            return Outcome.Success(Unit)
        }

        return suspendCancellableCoroutine {
                continuation ->
            try {
                current.requestScope(
                    missing,
                    object :
                        XposedService
                            .OnScopeEventListener {
                        override fun onScopeRequestApproved(
                            approved: List<String>,
                        ) {
                            if (
                                continuation.isActive
                            ) {
                                continuation.resume(
                                    Outcome.Success(
                                        Unit,
                                    ),
                                )
                            }
                        }

                        override fun onScopeRequestFailed(
                            message: String,
                        ) {
                            if (
                                continuation.isActive
                            ) {
                                continuation.resume(
                                    Outcome.Failure(
                                        code =
                                            "hook_scope_denied",
                                        message =
                                            message.ifBlank {
                                                "YDownload scope request was denied"
                                            },
                                        retryable =
                                            true,
                                    ),
                                )
                            }
                        }
                    },
                )
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resume(
                        Outcome.Failure(
                            code =
                                "hook_scope_request_failed",
                            message =
                                error.message
                                    ?: "YDownload scope request failed",
                            cause = error,
                            retryable = true,
                        ),
                    )
                }
            }
        }
    }
}
