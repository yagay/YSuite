package com.yagay.ysuite.feature.yfiles.provider.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.platform.api.CapabilityStatus
import com.yagay.ysuite.platform.api.RootGateway
import com.yagay.ysuite.platform.api.RootRequest
import com.yagay.ysuite.platform.api.RootResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuShellGateway(
    private val context: Context,
) : RootGateway {
    override suspend fun status():
        CapabilityStatus =
        withContext(Dispatchers.IO) {
            runCatching {
                val clazz =
                    Class.forName(
                        "rikka.shizuku.Shizuku",
                    )
                val ping =
                    clazz.getMethod(
                        "pingBinder",
                    ).invoke(null) as Boolean
                if (!ping) {
                    CapabilityStatus.Unavailable
                } else {
                    val permission =
                        clazz.getMethod(
                            "checkSelfPermission",
                        ).invoke(null) as Int
                    if (
                        permission ==
                        PackageManager
                            .PERMISSION_GRANTED
                    ) {
                        CapabilityStatus.Available
                    } else {
                        CapabilityStatus
                            .PermissionRequired
                    }
                }
            }.getOrDefault(
                CapabilityStatus.Unavailable,
            )
        }

    fun requestPermission(
        requestCode: Int = 9013,
    ): Boolean =
        runCatching {
            val clazz =
                Class.forName(
                    "rikka.shizuku.Shizuku",
                )
            clazz.getMethod(
                "requestPermission",
                Int::class.javaPrimitiveType,
            ).invoke(null, requestCode)
            true
        }.getOrDefault(false)

    override suspend fun execute(
        request: RootRequest,
    ): Outcome<RootResult> =
        withContext(Dispatchers.IO) {
            try {
                if (
                    status() !=
                    CapabilityStatus.Available
                ) {
                    return@withContext
                        Outcome.Failure(
                            code =
                                "shizuku_permission_required",
                            message =
                                context.getString(
                                    R.string.yfiles_msg_shizuku_permission_required,
                                ),
                        )
                }
                val clazz =
                    Class.forName(
                        "rikka.shizuku.Shizuku",
                    )
                val method =
                    clazz.methods.firstOrNull {
                        it.name == "newProcess" &&
                            it.parameterTypes.size ==
                                3
                    } ?: error(
                        "This Shizuku version does not expose process execution",
                    )
                val process =
                    method.invoke(
                        null,
                        arrayOf(
                            "sh",
                            "-c",
                            request.command,
                        ),
                        null,
                        null,
                    ) as Process
                val finished =
                    process.waitFor(
                        request.timeoutMillis,
                        TimeUnit.MILLISECONDS,
                    )
                if (!finished) {
                    process.destroyForcibly()
                    return@withContext
                        Outcome.Failure(
                            code =
                                "shizuku_timeout",
                            message =
                                context.getString(
                                    R.string.yfiles_msg_shizuku_timeout,
                                ),
                            retryable = true,
                        )
                }
                val stdout =
                    process.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                val stderr =
                    process.errorStream
                        .bufferedReader()
                        .use { it.readText() }
                Outcome.Success(
                    RootResult(
                        exitCode =
                            process.exitValue(),
                        stdout = stdout,
                        stderr = stderr,
                    ),
                )
            } catch (error: Throwable) {
                Outcome.Failure(
                    code =
                        "shizuku_execute_failed",
                    message =
                        error.message
                            ?: "Shizuku command failed",
                    cause = error,
                    retryable = true,
                )
            }
        }
}
