package com.yagay.suite.core

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The only Root execution gateway for the combined YSuite host.
 *
 * Plugins identify themselves and submit an operation; YSuite owns the actual root shell,
 * timeout policy, logging and result normalization. Standalone feature APKs may keep their local
 * root implementation, but code running through the combined host should call this gateway.
 */
object SuiteRootGateway {
    data class Result(
        val code: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean = false,
        val error: Throwable? = null,
    ) {
        val success: Boolean get() = !timedOut && error == null && code == 0

        fun failureMessage(defaultMessage: String = "Root 操作失败"): String = when {
            timedOut -> "Root 操作超时"
            error != null -> error.message?.takeIf(String::isNotBlank) ?: error.javaClass.simpleName
            stderr.isNotBlank() -> stderr
            code != 0 -> "su exit=$code"
            else -> defaultMessage
        }
    }

    /**
     * Reflection-friendly entry used by independently buildable feature modules when embedded in
     * YSuite. Absence of this class means the feature is running standalone and may use its own
     * shell implementation.
     */
    @JvmStatic
    fun executeFromPlugin(
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long,
    ): Result {
        val context = RootManager.contextOrNull()
            ?: return Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "YSuite root host is not initialized",
                error = IllegalStateException("YSuite root host is not initialized"),
            )
        return execute(context, pluginId, operation, command, timeoutSeconds)
    }

    @JvmStatic
    fun execute(
        context: Context,
        pluginId: String,
        operation: String,
        command: String,
        timeoutSeconds: Long = 15L,
    ): Result {
        val app = context.applicationContext
        RootManager.initialize(app)
        val safePlugin = pluginId.ifBlank { "unknown" }
        val safeOperation = operation.ifBlank { "command" }

        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root begin; plugin=$safePlugin operation=$safeOperation",
        )

        val result = try {
            val future = Shell.cmd(command).enqueue()
            val shellResult = future.get(timeoutSeconds.coerceAtLeast(1L), TimeUnit.SECONDS)
            Result(
                code = shellResult.code,
                stdout = shellResult.out.joinToString("\n"),
                stderr = shellResult.err.joinToString("\n"),
            )
        } catch (timeout: TimeoutException) {
            Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "",
                timedOut = true,
                error = timeout,
            )
        } catch (error: Throwable) {
            Result(
                code = Shell.Result.JOB_NOT_EXECUTED,
                stdout = "",
                stderr = "",
                error = error,
            )
        }

        val detail = if (result.success) {
            "success"
        } else {
            result.failureMessage().replace('\n', ' ').take(240)
        }
        SuiteLog.i(
            app,
            SuiteContract.HOST_MODULE_ID,
            "root end; plugin=$safePlugin operation=$safeOperation result=$detail",
        )
        return result
    }
}
