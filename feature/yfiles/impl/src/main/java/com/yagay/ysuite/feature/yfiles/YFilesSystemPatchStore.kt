package com.yagay.ysuite.feature.yfiles

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.yagay.ysuite.common.Outcome
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

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
) {
    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )

    init {
        YFilesPatchService.ensureRegistered()
    }

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
        YFilesPatchService.writeConfig(
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
        Outcome<Unit> {
        val result =
            YFilesPatchService.requestScope(
                recommendedTargets(),
            )
        if (result is Outcome.Success) {
            return sync()
        }
        return result
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
    }
}

private object YFilesPatchService :
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
                    "LSPosed service is not connected",
                retryable = true,
            )
        return runCatching {
            current.getRemotePreferences(
                YFilesSystemPatchStore.PREFS,
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
                            "Unable to write YFiles patch configuration",
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
                            ?: "Unable to write YFiles patch configuration",
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
                                                "YFiles scope request was denied"
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
                                    ?: "YFiles scope request failed",
                            cause = error,
                            retryable = true,
                        ),
                    )
                }
            }
        }
    }
}
