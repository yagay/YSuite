package com.yagay.suite.core

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.HostCapability
import com.yagay.suite.api.HostCapabilityRequestResult
import com.yagay.suite.api.HostCapabilityState
import com.yagay.suite.api.HostCommandResult
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.HostProcessReloadResult
import java.io.File

/** Type-safe adapter exposed to managed feature runtimes inside both YSuite and standalone hosts. */
internal class SuiteFeatureHost(
    context: Context,
    private val feature: FeatureSpec,
) : FeatureHost {
    override val applicationContext: Context = context.applicationContext
    override val hostPackageName: String get() = applicationContext.packageName
    override val featureId: String get() = feature.id

    private val featurePreferences by lazy {
        applicationContext.getSharedPreferences("ysuite.feature.$featureId", Context.MODE_PRIVATE)
    }

    override fun supports(capability: HostCapability): Boolean = when (capability) {
        HostCapability.ROOT -> SuiteCapability.ROOT in feature.sharedCapabilities
        HostCapability.LSPOSED -> SuiteCapability.LSPOSED in feature.sharedCapabilities
        HostCapability.ACCESSIBILITY -> SuiteCapability.ACCESSIBILITY in feature.sharedCapabilities
        HostCapability.NOTIFICATION_LISTENER -> SuiteCapability.NOTIFICATION_LISTENER in feature.sharedCapabilities
        HostCapability.OVERLAY -> SuiteCapability.OVERLAY in feature.sharedCapabilities
        HostCapability.NOTIFICATIONS -> SuiteCapability.NOTIFICATIONS in feature.sharedCapabilities
        HostCapability.ALL_FILES -> SuiteCapability.ALL_FILES in feature.sharedCapabilities
        HostCapability.FILE_SHARE -> SuiteCapability.FILE_SHARE in feature.sharedCapabilities
        HostCapability.NFC -> SuiteCapability.NFC in feature.sharedCapabilities
    }

    override fun capabilityState(capability: HostCapability): HostCapabilityState {
        if (!supports(capability)) return HostCapabilityState.NOT_DECLARED
        return SuiteCapabilityBroker.state(applicationContext, capability)
    }

    override fun requestCapability(
        activity: Activity,
        capability: HostCapability,
        requestCode: Int,
    ): HostCapabilityRequestResult {
        if (!supports(capability)) return HostCapabilityRequestResult.NOT_DECLARED
        return SuiteCapabilityBroker.request(activity, capability, requestCode)
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

    override fun sharedFileUri(file: File): Uri? {
        if (!supports(HostCapability.FILE_SHARE)) return null
        return runCatching {
            FileProvider.getUriForFile(
                applicationContext,
                "$hostPackageName.files",
                file,
            )
        }.onFailure {
            SuiteLog.e(applicationContext, featureId, "host file-share URI failed: ${file.absolutePath}", it)
        }.getOrNull()
    }

    override fun settingBoolean(key: String, defaultValue: Boolean): Boolean =
        featurePreferences.getBoolean(requireSettingKey(key), defaultValue)

    override fun putSettingBoolean(key: String, value: Boolean) {
        featurePreferences.edit().putBoolean(requireSettingKey(key), value).apply()
    }

    override fun settingString(key: String, defaultValue: String?): String? =
        featurePreferences.getString(requireSettingKey(key), defaultValue)

    override fun putSettingString(key: String, value: String?) {
        val editor = featurePreferences.edit()
        if (value == null) editor.remove(requireSettingKey(key)) else editor.putString(requireSettingKey(key), value)
        editor.apply()
    }

    override fun removeSetting(key: String) {
        featurePreferences.edit().remove(requireSettingKey(key)).apply()
    }

    override fun log(level: HostLogLevel, message: String, error: Throwable?) {
        val code = when (level) {
            HostLogLevel.DEBUG -> "D"
            HostLogLevel.INFO -> "I"
            HostLogLevel.WARN -> "W"
            HostLogLevel.ERROR -> "E"
        }
        SuiteLog.write(applicationContext, featureId, code, message, error)
    }

    private fun requireSettingKey(key: String): String {
        val normalized = key.trim()
        require(normalized.isNotEmpty()) { "Feature setting key must not be blank" }
        return normalized
    }
}
