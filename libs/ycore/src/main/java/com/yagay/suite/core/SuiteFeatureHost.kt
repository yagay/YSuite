package com.yagay.suite.core

import android.content.Context
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCommandResult
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.HostProcessReloadResult

/** Type-safe adapter exposed to managed feature runtimes inside the combined host. */
internal class SuiteFeatureHost(
    context: Context,
    private val feature: FeatureSpec,
) : FeatureHost {
    override val applicationContext: Context = context.applicationContext
    override val hostPackageName: String get() = applicationContext.packageName
    override val featureId: String get() = feature.id

    override fun supports(capability: HostCapability): Boolean = when (capability) {
        HostCapability.ROOT -> SuiteCapability.ROOT in feature.sharedCapabilities
        HostCapability.LSPOSED -> SuiteCapability.LSPOSED in feature.sharedCapabilities
        HostCapability.ACCESSIBILITY -> SuiteCapability.ACCESSIBILITY in feature.sharedCapabilities
        HostCapability.NOTIFICATION_LISTENER -> SuiteCapability.NOTIFICATION_LISTENER in feature.sharedCapabilities
        HostCapability.OVERLAY -> SuiteCapability.OVERLAY in feature.sharedCapabilities
        HostCapability.NOTIFICATIONS -> SuiteCapability.NOTIFICATIONS in feature.sharedCapabilities
    }

    override fun rootExecute(
        operation: String,
        command: String,
        timeoutSeconds: Long,
    ): HostCommandResult {
        if (!supports(HostCapability.ROOT)) {
            return HostCommandResult(
                code = -1,
                stdout = "",
                stderr = "Feature $featureId did not declare ROOT capability",
                errorMessage = "ROOT capability not declared",
            )
        }
        val result = SuiteRootGateway.execute(
            context = applicationContext,
            pluginId = featureId,
            operation = operation,
            command = command,
            timeoutSeconds = timeoutSeconds,
        )
        return HostCommandResult(
            code = result.code,
            stdout = result.stdout,
            stderr = result.stderr,
            timedOut = result.timedOut,
            errorMessage = result.error?.message,
        )
    }

    override fun reloadPackage(
        packageName: String,
        timeoutSeconds: Long,
    ): HostProcessReloadResult {
        if (!supports(HostCapability.ROOT)) {
            return HostProcessReloadResult(
                packageName = packageName,
                success = false,
                killedCount = 0,
                detail = "ROOT capability not declared",
            )
        }
        val result = SuiteProcessManager.reloadPackageProcesses(
            context = applicationContext,
            pluginId = featureId,
            packageName = packageName,
            timeoutSeconds = timeoutSeconds,
        )
        return HostProcessReloadResult(
            packageName = result.packageName,
            success = result.success,
            killedCount = result.killedCount,
            detail = result.detail,
        )
    }

    override fun frameworkStatus(): String = SuiteXposedServiceBroker.statusLabel()

    override fun log(level: HostLogLevel, message: String, error: Throwable?) {
        val code = when (level) {
            HostLogLevel.DEBUG -> "D"
            HostLogLevel.INFO -> "I"
            HostLogLevel.WARN -> "W"
            HostLogLevel.ERROR -> "E"
        }
        SuiteLog.write(applicationContext, featureId, code, message, error)
    }
}
