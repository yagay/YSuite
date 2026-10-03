package com.yagay.suite.core

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.FeatureSettings
import com.yagay.suite.api.HostBinaryCommandResult
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

    private val featureSettings by lazy {
        FeatureSettings.named(applicationContext, "ysuite.feature.$featureId")
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

    override fun rootExecuteBinary(
        operation: String,
        command: String,
        timeoutSeconds: Long,
        maxStdoutBytes: Int,
        mergeError: Boolean,
    ): HostBinaryCommandResult {
        if (!supports(HostCapability.ROOT)) {
            return HostBinaryCommandResult(
                code = -1,
                stdout = ByteArray(0),
                stderr = "Feature $featureId did not declare ROOT capability",
                errorMessage = "ROOT capability not declared",
            )
        }
        RootManager.initialize(applicationContext)
        val result = SuiteRootGateway.executeBinaryFromPlugin(
            pluginId = featureId,
            operation = operation,
            command = command,
            timeoutSeconds = timeoutSeconds,
            maxStdoutBytes = maxStdoutBytes,
            mergeError = mergeError,
        )
        return HostBinaryCommandResult(
            code = result.code,
            stdout = result.stdout,
            stderr = result.stderr,
            timedOut = result.timedOut,
            errorMessage = result.errorMessage,
        )
    }

    override fun rootStart(operation: String, command: String): Process? {
        if (!supports(HostCapability.ROOT)) return null
        // startFromPlugin is also used by reflection-era plugins and resolves the host Context from
        // RootManager. Ensure the typed FeatureHost path initializes that shared owner first.
        RootManager.initialize(applicationContext)
        return SuiteRootGateway.startFromPlugin(
            pluginId = featureId,
            operation = operation,
            command = command,
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
        featureSettings.boolean(requireSettingKey(key), defaultValue)

    override fun putSettingBoolean(key: String, value: Boolean) {
        featureSettings.putBoolean(requireSettingKey(key), value)
    }

    override fun settingString(key: String, defaultValue: String?): String? =
        featureSettings.string(requireSettingKey(key), defaultValue)

    override fun putSettingString(key: String, value: String?) {
        val settingKey = requireSettingKey(key)
        if (value == null) featureSettings.remove(settingKey) else featureSettings.putString(settingKey, value)
    }

    override fun settingInt(key: String, defaultValue: Int): Int =
        featureSettings.int(requireSettingKey(key), defaultValue)

    override fun putSettingInt(key: String, value: Int) {
        featureSettings.putInt(requireSettingKey(key), value)
    }

    override fun settingLong(key: String, defaultValue: Long): Long =
        featureSettings.long(requireSettingKey(key), defaultValue)

    override fun putSettingLong(key: String, value: Long) {
        featureSettings.putLong(requireSettingKey(key), value)
    }

    override fun settingStringSet(key: String, defaultValue: Set<String>): Set<String> =
        featureSettings.stringSet(requireSettingKey(key), defaultValue)

    override fun putSettingStringSet(key: String, value: Set<String>) {
        featureSettings.putStringSet(requireSettingKey(key), value)
    }

    override fun removeSetting(key: String) {
        featureSettings.remove(requireSettingKey(key))
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
