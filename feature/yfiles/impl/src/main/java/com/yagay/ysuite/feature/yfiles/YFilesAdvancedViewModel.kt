package com.yagay.ysuite.feature.yfiles

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.common.Outcome
import com.yagay.ysuite.feature.yfiles.api.YFileNode
import com.yagay.ysuite.feature.yfiles.api.YFileRef
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.presentation.YSuiteViewModel
import com.yagay.ysuite.feature.yfiles.provider.remote.YFilesNetworkProfile
import com.yagay.ysuite.feature.yfiles.provider.remote.YFilesNetworkProtocol
import com.yagay.ysuite.feature.yfiles.provider.cloud.YFilesCloudKind
import com.yagay.ysuite.feature.yfiles.provider.cloud.YFilesCloudProfile
import com.yagay.ysuite.feature.yfiles.plugin.YFilesPluginDescriptor
import kotlinx.coroutines.launch

data class YFilesNetworkDraft(
    val id: String? = null,
    val name: String = "",
    val protocol: YFilesNetworkProtocol =
        YFilesNetworkProtocol.SMB,
    val host: String = "",
    val port: String =
        YFilesNetworkProtocol.SMB
            .defaultPort.toString(),
    val username: String = "",
    val password: String = "",
    val shareName: String = "",
    val remotePath: String = "/",
    val privateKeyPath: String = "",
    val useTls: Boolean = false,
)

data class YFilesCloudDraft(
    val id: String? = null,
    val name: String = "",
    val kind: YFilesCloudKind =
        YFilesCloudKind.GoogleDrive,
    val accessToken: String = "",
)

data class YFilesAdvancedUiState(
    val transfers: List<YFilesTransferTask> =
        emptyList(),
    val networkProfiles:
        List<YFilesNetworkProfile> =
        emptyList(),
    val networkDraft: YFilesNetworkDraft? =
        null,
    val cloudProfiles: List<YFilesCloudProfile> =
        emptyList(),
    val cloudDraft: YFilesCloudDraft? = null,
    val shareServerSettings:
        YFilesShareServerSettings? = null,
    val shareServerState:
        YFilesShareServerState =
        YFilesShareServerState(),
    val automationEnabled: Boolean = false,
    val plugins: List<YFilesPluginDescriptor> =
        emptyList(),
    val installedApps: List<YInstalledApp> =
        emptyList(),
    val apkAnalysis: YApkAnalysis? = null,
    val preview: YDocumentPreview? = null,
    val hexPage: YHexPage? = null,
    val hexRef: YFileRef? = null,
    val checksumSet: YChecksumSet? = null,
    val vaultEntries: List<YVaultEntry> =
        emptyList(),
    val integrityResults:
        List<YIntegrityResult> =
        emptyList(),
    val rootModules: List<YRootModule> =
        emptyList(),
    val rootSecurityInfo:
        YRootFileSecurityInfo? = null,
    val encryptedVolumeSupport:
        YEncryptedVolumeSupport? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class YFilesAdvancedViewModel(
    context: Context,
    private val environment: YFilesEnvironment,
    private val logger: YSuiteLogger,
) : YSuiteViewModel<
    YFilesAdvancedUiState,
    Nothing,
    >(
    initialState =
        YFilesAdvancedUiState(
            networkProfiles =
                environment.networkStore
                    .profiles(),
            cloudProfiles =
                environment.cloudStore
                    .profiles(),
            shareServerSettings =
                environment.shareServerStore
                    .settings(),
            automationEnabled =
                environment.automationSettings
                    .enabled(),
            plugins =
                environment.plugins
                    .discover(),
            vaultEntries =
                environment.security
                    .vaultEntries(),
        ),
) {
    private val appContext =
        context.applicationContext

    init {
        viewModelScope.launch {
            environment.transfers.tasks
                .collect { tasks ->
                    updateState {
                        it.copy(
                            transfers = tasks,
                        )
                    }
                }
        }
        viewModelScope.launch {
            YFilesShareServerController.state
                .collect { server ->
                    updateState {
                        it.copy(
                            shareServerState =
                                server,
                        )
                    }
                }
        }
    }

    fun pauseTransfer(id: String) =
        launchAction("pause_transfer") {
            environment.transfers.pause(id)
        }

    fun resumeTransfer(id: String) =
        launchAction("resume_transfer") {
            environment.transfers.resume(id)
        }

    fun retryTransfer(id: String) =
        launchAction("retry_transfer") {
            environment.transfers.retry(id)
        }

    fun cancelTransfer(id: String) =
        launchAction("cancel_transfer") {
            environment.transfers.cancel(id)
        }

    fun removeTransfer(id: String) =
        launchAction("remove_transfer") {
            environment.transfers.remove(id)
        }

    fun moveTransferUp(id: String) =
        launchAction("move_transfer_up") {
            environment.transfers.moveUp(id)
        }

    fun moveTransferDown(id: String) =
        launchAction("move_transfer_down") {
            environment.transfers.moveDown(id)
        }

    fun setTransferSpeedLimit(
        id: String,
        bytesPerSecond: Long,
    ) = launchAction("transfer_speed") {
        environment.transfers
            .setSpeedLimit(
                id,
                bytesPerSecond,
            )
    }

    fun beginNetworkProfile(
        profile: YFilesNetworkProfile? = null,
    ) {
        updateState {
            it.copy(
                networkDraft =
                    profile?.let {
                        YFilesNetworkDraft(
                            id = it.id,
                            name = it.name,
                            protocol =
                                it.protocol,
                            host = it.host,
                            port =
                                it.port
                                    .toString(),
                            username =
                                it.username,
                            password =
                                it.password,
                            shareName =
                                it.shareName,
                            remotePath =
                                it.remotePath,
                            privateKeyPath =
                                it.privateKeyPath,
                            useTls =
                                it.useTls,
                        )
                    } ?: YFilesNetworkDraft(),
                error = null,
                message = null,
            )
        }
    }

    fun updateNetworkDraft(
        field: String,
        value: String,
    ) {
        updateState { current ->
            val draft =
                current.networkDraft
                    ?: YFilesNetworkDraft()
            val next =
                when (field) {
                    "name" ->
                        draft.copy(name = value)
                    "protocol" -> {
                        val protocol =
                            runCatching {
                                YFilesNetworkProtocol
                                    .valueOf(value)
                            }.getOrDefault(
                                draft.protocol,
                            )
                        draft.copy(
                            protocol = protocol,
                            port =
                                protocol.defaultPort
                                    .toString(),
                            useTls =
                                protocol ==
                                    YFilesNetworkProtocol
                                        .FTPS ||
                                    protocol ==
                                        YFilesNetworkProtocol
                                            .WebDAV,
                        )
                    }
                    "host" ->
                        draft.copy(host = value)
                    "port" ->
                        draft.copy(port = value)
                    "username" ->
                        draft.copy(
                            username = value,
                        )
                    "password" ->
                        draft.copy(
                            password = value,
                        )
                    "shareName" ->
                        draft.copy(
                            shareName = value,
                        )
                    "remotePath" ->
                        draft.copy(
                            remotePath = value,
                        )
                    "privateKeyPath" ->
                        draft.copy(
                            privateKeyPath =
                                value,
                        )
                    "useTls" ->
                        draft.copy(
                            useTls =
                                value.toBooleanStrictOrNull()
                                    ?: draft.useTls,
                        )
                    else -> draft
                }
            current.copy(
                networkDraft = next,
            )
        }
    }

    fun cancelNetworkDraft() {
        updateState {
            it.copy(networkDraft = null)
        }
    }

    fun saveNetworkProfile() {
        val draft =
            state.value.networkDraft ?: return
        val profile =
            draft.toProfile()
                ?: run {
                    updateState {
                        it.copy(
                            error =
                                "Host, name and port are required",
                        )
                    }
                    return
                }
        environment.remoteProvider
            .saveProfile(profile)
        updateState {
            it.copy(
                networkDraft = null,
                networkProfiles =
                    environment.networkStore
                        .profiles(),
                message =
                    appContext.getString(
                        R.string.yfiles_msg_network_saved,
                    ),
                error = null,
            )
        }
    }

    fun testNetworkProfile() {
        val draft =
            state.value.networkDraft ?: return
        val profile =
            draft.toProfile()
                ?: run {
                    updateState {
                        it.copy(
                            error =
                                "Host, name and port are required",
                        )
                    }
                    return
                }
        launchOutcome(
            "network_test",
            {
                environment.remoteProvider
                    .test(profile)
            },
        ) {
            updateState {
                it.copy(
                    message =
                        appContext.getString(
                            R.string.yfiles_msg_connection_succeeded,
                        ),
                )
            }
        }
    }

    fun deleteNetworkProfile(id: String) {
        environment.remoteProvider
            .deleteProfile(id)
        updateState {
            it.copy(
                networkProfiles =
                    environment.networkStore
                        .profiles(),
            )
        }
    }

    fun beginCloudProfile(
        profile: YFilesCloudProfile? = null,
    ) {
        updateState {
            it.copy(
                cloudDraft =
                    profile?.let {
                        YFilesCloudDraft(
                            id = it.id,
                            name = it.name,
                            kind = it.kind,
                            accessToken =
                                it.accessToken,
                        )
                    } ?: YFilesCloudDraft(),
                error = null,
                message = null,
            )
        }
    }

    fun updateCloudDraft(
        field: String,
        value: String,
    ) {
        updateState { current ->
            val draft =
                current.cloudDraft
                    ?: YFilesCloudDraft()
            current.copy(
                cloudDraft =
                    when (field) {
                        "name" ->
                            draft.copy(
                                name = value,
                            )
                        "kind" ->
                            draft.copy(
                                kind =
                                    runCatching {
                                        YFilesCloudKind
                                            .valueOf(
                                                value,
                                            )
                                    }.getOrDefault(
                                        draft.kind,
                                    ),
                            )
                        "token" ->
                            draft.copy(
                                accessToken =
                                    value,
                            )
                        else -> draft
                    },
            )
        }
    }

    fun cancelCloudDraft() {
        updateState {
            it.copy(cloudDraft = null)
        }
    }

    fun saveCloudProfile() {
        val draft =
            state.value.cloudDraft ?: return
        if (
            draft.name.isBlank() ||
            draft.accessToken.isBlank()
        ) {
            updateState {
                it.copy(
                    error =
                        "Cloud name and OAuth access token are required",
                )
            }
            return
        }
        environment.cloudProvider
            .saveProfile(
                YFilesCloudProfile(
                    id =
                        draft.id
                            ?: java.util.UUID
                                .randomUUID()
                                .toString(),
                    name =
                        draft.name.trim(),
                    kind = draft.kind,
                    accessToken =
                        draft.accessToken
                            .trim(),
                ),
            )
        updateState {
            it.copy(
                cloudDraft = null,
                cloudProfiles =
                    environment.cloudStore
                        .profiles(),
                message =
                    appContext.getString(
                        R.string.yfiles_msg_cloud_saved,
                    ),
                error = null,
            )
        }
    }

    fun testCloudProfile() {
        val draft =
            state.value.cloudDraft ?: return
        if (
            draft.name.isBlank() ||
            draft.accessToken.isBlank()
        ) {
            return
        }
        val profile =
            YFilesCloudProfile(
                id =
                    draft.id
                        ?: java.util.UUID
                            .randomUUID()
                            .toString(),
                name = draft.name,
                kind = draft.kind,
                accessToken =
                    draft.accessToken,
            )
        launchOutcome(
            "cloud_test",
            {
                environment.cloudProvider
                    .test(profile)
            },
        ) {
            updateState {
                current ->
                current.copy(
                    message =
                        appContext.getString(
                            R.string.yfiles_msg_cloud_succeeded,
                        ),
                )
            }
        }
    }

    fun deleteCloudProfile(id: String) {
        environment.cloudProvider
            .deleteProfile(id)
        updateState {
            it.copy(
                cloudProfiles =
                    environment.cloudStore
                        .profiles(),
            )
        }
    }

    fun saveShareServerSettings(
        settings: YFilesShareServerSettings,
    ) {
        environment.shareServerStore
            .save(settings)
        updateState {
            it.copy(
                shareServerSettings =
                    environment.shareServerStore
                        .settings(),
                message =
                    appContext.getString(
                        R.string.yfiles_msg_share_saved,
                    ),
            )
        }
    }

    fun startShareServer() {
        YFilesShareServerController
            .start(appContext)
    }

    fun stopShareServer() {
        YFilesShareServerController
            .stop(appContext)
    }

    fun setAutomationEnabled(
        enabled: Boolean,
    ) {
        environment.automationSettings
            .setEnabled(enabled)
        updateState {
            it.copy(
                automationEnabled = enabled,
            )
        }
    }

    fun refreshPlugins() {
        updateState {
            it.copy(
                plugins =
                    environment.plugins
                        .discover(),
            )
        }
    }

    fun approvePlugin(
        descriptor: YFilesPluginDescriptor,
        approved: Boolean,
    ) {
        environment.plugins.approve(
            descriptor,
            approved,
        )
        refreshPlugins()
    }

    fun inspectPlugin(
        descriptor: YFilesPluginDescriptor,
    ) = launchOutcome(
        "plugin_inspect",
        {
            environment.plugins
                .inspect(descriptor)
        },
    ) { inspected ->
        updateState {
            current ->
            current.copy(
                plugins =
                    current.plugins
                        .map {
                            if (
                                it.packageName ==
                                    inspected
                                        .packageName &&
                                it.serviceName ==
                                    inspected
                                        .serviceName
                            ) {
                                inspected.copy(
                                    approved =
                                        it.approved,
                                )
                            } else {
                                it
                            }
                        },
                message =
                    appContext.getString(
                        R.string.yfiles_msg_plugin_protocol,
                        inspected.protocolVersion
                            ?.toString()
                            .orEmpty(),
                    ),
            )
        }
    }

    fun mountEncryptedVolume(
        type: String,
        source: String,
        mountPoint: String,
        passphrase: String,
    ) = launchOutcome(
        "encrypted_volume_mount",
        {
            environment.rootTools
                .mountEncryptedVolume(
                    type,
                    source,
                    mountPoint,
                    passphrase,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                message =
                    appContext.getString(
                        R.string.yfiles_msg_volume_mounted,
                    ),
            )
        }
    }

    fun unmountEncryptedVolume(
        mountPoint: String,
    ) = launchOutcome(
        "encrypted_volume_unmount",
        {
            environment.rootTools
                .unmountEncryptedVolume(
                    mountPoint,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                message =
                    appContext.getString(
                        R.string.yfiles_msg_volume_unmounted,
                    ),
            )
        }
    }

    fun requestShizukuPermission() {
        val started =
            environment.shizukuGateway
                .requestPermission()
        updateState {
            it.copy(
                message =
                    if (started) {
                        "Shizuku permission requested"
                    } else {
                        null
                    },
                error =
                    if (started) {
                        null
                    } else {
                        "Shizuku is unavailable"
                    },
            )
        }
    }

    fun loadInstalledApps() =
        launchAction("installed_apps") {
            val apps =
                environment.apps
                    .installedApps()
            updateState {
                it.copy(
                    installedApps = apps,
                )
            }
        }

    fun analyzeApk(ref: YFileRef) =
        launchOutcome(
            "apk_analysis",
            {
                environment.apps
                    .analyzeApk(ref)
            },
        ) {
            updateState {
                current ->
                current.copy(
                    apkAnalysis = it,
                )
            }
        }

    fun preview(node: YFileNode) =
        launchOutcome(
            "preview",
            {
                environment.preview
                    .preview(
                        node.ref,
                        node.name,
                    )
            },
        ) {
            updateState {
                current ->
                current.copy(preview = it)
            }
        }

    fun loadHex(
        ref: YFileRef,
        offset: Long = 0L,
    ) = launchOutcome(
        "hex",
        {
            environment.preview
                .readHexPage(
                    ref,
                    offset,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                hexPage = it,
                hexRef = ref,
            )
        }
    }

    fun writeHex(
        ref: YFileRef,
        offset: Long,
        value: String,
    ) = launchOutcome(
        "hex_write",
        {
            environment.preview
                .writeHex(
                    ref,
                    offset,
                    value,
                )
        },
    ) {
        loadHex(ref, offset)
    }

    fun checksums(ref: YFileRef) =
        launchOutcome(
            "checksums",
            {
                environment.preview
                    .checksums(ref)
            },
        ) {
            updateState {
                current ->
                current.copy(
                    checksumSet = it,
                )
            }
        }

    fun encrypt(
        source: YFileRef,
        destination: YFileRef,
        name: String? = null,
    ) = launchOutcome(
        "encrypt",
        {
            environment.security
                .encryptFile(
                    source,
                    destination,
                    name,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                message =
                    appContext.getString(
                        R.string.yfiles_msg_encrypted_created,
                    ),
            )
        }
    }

    fun decrypt(
        source: YFileRef,
        destination: YFileRef,
        name: String? = null,
    ) = launchOutcome(
        "decrypt",
        {
            environment.security
                .decryptFile(
                    source,
                    destination,
                    name,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                message =
                    appContext.getString(
                        R.string.yfiles_msg_file_decrypted,
                    ),
            )
        }
    }

    fun secureDelete(ref: YFileRef) =
        launchOutcome(
            "secure_delete",
            {
                environment.security
                    .secureDelete(ref)
            },
        ) {
            updateState {
                current ->
                current.copy(
                    message =
                        appContext.getString(
                            R.string.yfiles_msg_secure_delete_done,
                        ),
                )
            }
        }

    fun watchIntegrity(ref: YFileRef) =
        launchOutcome(
            "integrity_watch",
            {
                environment.security
                    .addIntegrityWatch(ref)
            },
        ) {
            scanIntegrity()
        }

    fun scanIntegrity() =
        launchAction("integrity_scan") {
            val results =
                environment.security
                    .scanIntegrity()
            updateState {
                it.copy(
                    integrityResults =
                        results,
                )
            }
        }

    fun vaultAdd(
        ref: YFileRef,
        name: String,
    ) = launchOutcome(
        "vault_add",
        {
            environment.security
                .vaultAdd(ref, name)
        },
    ) {
        refreshVault()
    }

    fun vaultRestore(
        id: String,
        destination: YFileRef,
    ) = launchOutcome(
        "vault_restore",
        {
            environment.security
                .vaultRestore(
                    id,
                    destination,
                )
        },
    ) {
        updateState {
            current ->
            current.copy(
                message =
                    appContext.getString(
                        R.string.yfiles_msg_vault_restored,
                    ),
            )
        }
    }

    fun vaultDelete(id: String) {
        val deleted =
            environment.security
                .vaultDelete(id)
        refreshVault()
        if (!deleted) {
            updateState {
                it.copy(
                    error =
                        "Unable to delete vault item",
                )
            }
        }
    }

    fun refreshVault() {
        updateState {
            it.copy(
                vaultEntries =
                    environment.security
                        .vaultEntries(),
            )
        }
    }

    fun loadRootModules() =
        launchOutcome(
            "root_modules",
            {
                environment.rootTools
                    .modules()
            },
        ) {
            updateState {
                current ->
                current.copy(
                    rootModules = it,
                )
            }
        }

    fun setRootModuleEnabled(
        id: String,
        enabled: Boolean,
    ) = launchOutcome(
        "root_module_toggle",
        {
            environment.rootTools
                .setModuleEnabled(
                    id,
                    enabled,
                )
        },
    ) {
        loadRootModules()
    }

    fun markRootModuleRemoval(
        id: String,
        remove: Boolean,
    ) = launchOutcome(
        "root_module_remove",
        {
            environment.rootTools
                .markModuleForRemoval(
                    id,
                    remove,
                )
        },
    ) {
        loadRootModules()
    }

    fun rootSecurityInfo(path: String) =
        launchOutcome(
            "root_security_info",
            {
                environment.rootTools
                    .securityInfo(path)
            },
        ) {
            updateState {
                current ->
                current.copy(
                    rootSecurityInfo = it,
                )
            }
        }

    fun probeEncryptedVolumes() =
        launchOutcome(
            "encrypted_volume_probe",
            {
                environment.rootTools
                    .encryptedVolumeSupport()
            },
        ) {
            updateState {
                current ->
                current.copy(
                    encryptedVolumeSupport =
                        it,
                )
            }
        }

    fun clearResult() {
        updateState {
            it.copy(
                apkAnalysis = null,
                preview = null,
                hexPage = null,
                hexRef = null,
                checksumSet = null,
                rootSecurityInfo = null,
                error = null,
                message = null,
            )
        }
    }

    private fun YFilesNetworkDraft
        .toProfile():
        YFilesNetworkProfile? {
        val portValue =
            port.toIntOrNull()
                ?.takeIf {
                    it in 1..65535
                }
                ?: return null
        if (
            name.isBlank() ||
            host.isBlank()
        ) {
            return null
        }
        return YFilesNetworkProfile(
            id =
                id ?: java.util.UUID
                    .randomUUID()
                    .toString(),
            name = name.trim(),
            protocol = protocol,
            host = host.trim(),
            port = portValue,
            username =
                username.trim(),
            password = password,
            shareName =
                shareName.trim(),
            remotePath =
                remotePath.trim()
                    .ifBlank { "/" },
            privateKeyPath =
                privateKeyPath.trim(),
            useTls = useTls,
        )
    }

    private fun launchAction(
        name: String,
        action: suspend () -> Unit,
    ) {
        updateState {
            it.copy(
                busy = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                action()
            } catch (error: Throwable) {
                fail(name, error)
            } finally {
                updateState {
                    it.copy(busy = false)
                }
            }
        }
    }

    private fun <T> launchOutcome(
        name: String,
        outcome: suspend () -> Outcome<T>,
        success: (T) -> Unit,
    ) {
        updateState {
            it.copy(
                busy = true,
                error = null,
            )
        }
        viewModelScope.launch {
            try {
                when (val result = outcome()) {
                    is Outcome.Success ->
                        success(result.value)
                    is Outcome.Failure -> {
                        updateState {
                            it.copy(
                                error =
                                    result.message,
                            )
                        }
                        logger.error(
                            TAG,
                            name,
                            result.cause,
                        )
                    }
                }
            } catch (error: Throwable) {
                fail(name, error)
            } finally {
                updateState {
                    it.copy(busy = false)
                }
            }
        }
    }

    private fun fail(
        name: String,
        error: Throwable,
    ) {
        updateState {
            it.copy(
                error =
                    error.message
                        ?: "YFiles advanced operation failed",
            )
        }
        logger.error(
            TAG,
            name,
            error,
        )
    }

    companion object {
        private const val TAG =
            "YSuite/YFilesAdvanced"
    }
}
