package com.yagay.ysuite.feature.yfiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yagay.ysuite.designsystem.component.YSuiteFormField
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteStatusBadge
import com.yagay.ysuite.designsystem.component.YSuiteStatusTone
import com.yagay.ysuite.designsystem.component.YSuiteSwitchItem
import com.yagay.ysuite.designsystem.component.YSuiteTextFormDialog
import com.yagay.ysuite.designsystem.component.YSuiteTextInputDialog
import com.yagay.ysuite.designsystem.theme.YSuiteSpacing
import com.yagay.ysuite.feature.yfiles.api.YFileType
import com.yagay.ysuite.feature.yfiles.plugin.YFilesPluginDescriptor
import com.yagay.ysuite.feature.yfiles.provider.cloud.YFilesCloudKind
import com.yagay.ysuite.feature.yfiles.provider.remote.YFilesNetworkProtocol
import com.yagay.ysuite.productui.filemanager.FileExplorerBackButton
import com.yagay.ysuite.productui.filemanager.FileExplorerToolAction
import com.yagay.ysuite.productui.filemanager.FileExplorerToolGroup
import com.yagay.ysuite.productui.filemanager.FileExplorerUtilitySurface
import com.yagay.ysuite.productui.settings.ComposeSettingsGroup
import com.yagay.ysuite.productui.settings.ComposeSettingsLink
import com.yagay.ysuite.productui.settings.ComposeSettingsSwitch

@Composable
internal fun YFilesTransfersSurface(
    state: YFilesAdvancedUiState,
    advanced: YFilesAdvancedViewModel,
    onBack: () -> Unit,
) {
    FileExplorerUtilitySurface(
        title = "Transfers",
        navigationIcon = {
            FileExplorerBackButton(
                contentDescription = "Files",
                onClick = onBack,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement =
                Arrangement.spacedBy(
                    YSuiteSpacing.Small,
                ),
        ) {
            if (state.transfers.isEmpty()) {
                item {
                    YSuiteListItem(
                        title = "No transfers",
                        subtitle =
                            "Copy and move operations will appear here.",
                    )
                }
            }
            items(
                items = state.transfers,
                key = { it.id },
            ) { task ->
                FileExplorerToolGroup(
                    title =
                        when (task.operation) {
                            YFilesTransferOperation.Copy ->
                                "Copy"
                            YFilesTransferOperation.Move ->
                                "Move"
                        },
                ) {
                    YSuiteListItem(
                        title =
                            task.currentName
                                ?: task.sources
                                    .firstOrNull()
                                    ?.path
                                    ?.substringAfterLast('/')
                                    .orEmpty()
                                    .ifBlank {
                                        "Transfer"
                                    },
                        subtitle =
                            buildString {
                                append(
                                    task.state.name,
                                )
                                append(" • ")
                                append(
                                    task.completedItems,
                                )
                                append("/")
                                append(
                                    task.totalItems,
                                )
                                task.totalBytes
                                    ?.takeIf {
                                        it > 0L
                                    }
                                    ?.let {
                                        append(" • ")
                                        append(
                                            (
                                                task.progress
                                                    ?: 0f
                                                )
                                                .times(100)
                                                .toInt(),
                                        )
                                        append("%")
                                    }
                                if (
                                    task
                                        .speedLimitBytesPerSecond >
                                    0L
                                ) {
                                    append(" • limit ")
                                    append(
                                        formatBytes(
                                            task.speedLimitBytesPerSecond,
                                        ),
                                    )
                                    append("/s")
                                }
                            },
                    )
                    when (task.state) {
                        YFilesTransferState.Pending,
                        YFilesTransferState.Running ->
                            FileExplorerToolAction(
                                text = "Pause",
                                onClick = {
                                    advanced.pauseTransfer(
                                        task.id,
                                    )
                                },
                            )
                        YFilesTransferState.Paused ->
                            FileExplorerToolAction(
                                text = "Resume",
                                onClick = {
                                    advanced.resumeTransfer(
                                        task.id,
                                    )
                                },
                            )
                        YFilesTransferState.Failed ->
                            FileExplorerToolAction(
                                text = "Retry",
                                onClick = {
                                    advanced.retryTransfer(
                                        task.id,
                                    )
                                },
                            )
                        else -> Unit
                    }
                    if (
                        task.state !=
                        YFilesTransferState.Completed &&
                        task.state !=
                        YFilesTransferState.Cancelled
                    ) {
                        FileExplorerToolAction(
                            text = "Cancel",
                            onClick = {
                                advanced.cancelTransfer(
                                    task.id,
                                )
                            },
                        )
                    }
                    FileExplorerToolAction(
                        text = "Move up",
                        onClick = {
                            advanced.moveTransferUp(
                                task.id,
                            )
                        },
                    )
                    FileExplorerToolAction(
                        text = "Move down",
                        onClick = {
                            advanced.moveTransferDown(
                                task.id,
                            )
                        },
                    )
                    FileExplorerToolAction(
                        text = "Remove from history",
                        onClick = {
                            advanced.removeTransfer(
                                task.id,
                            )
                        },
                    )
                    task.error
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            YSuiteStatusBadge(
                                text = it,
                                tone =
                                    YSuiteStatusTone
                                        .Error,
                            )
                        }
                }
            }
        }
    }
}

internal fun LazyListScope.advancedToolsContent(
    browserState: YFilesUiState,
    advancedState: YFilesAdvancedUiState,
    advanced: YFilesAdvancedViewModel,
) {
    val selected =
        browserState.entries.filter {
            it.ref in browserState.selected
        }
    val single =
        selected.singleOrNull()
    val file =
        single?.takeIf {
            it.type == YFileType.File
        }
    val directory =
        browserState.directory

    item {
        FileExplorerToolGroup(
            title = "Advanced",
        ) {
            if (file != null) {
                FileExplorerToolAction(
                    text = "Document preview",
                    onClick = {
                        advanced.preview(file)
                    },
                )
                FileExplorerToolAction(
                    text = "Hex viewer / editor",
                    onClick = {
                        advanced.loadHex(
                            file.ref,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = "All checksums",
                    onClick = {
                        advanced.checksums(
                            file.ref,
                        )
                    },
                )
                if (
                    file.name.endsWith(
                        ".apk",
                        ignoreCase = true,
                    )
                ) {
                    FileExplorerToolAction(
                        text = "APK Analyzer",
                        onClick = {
                            advanced.analyzeApk(
                                file.ref,
                            )
                        },
                    )
                }
                if (directory != null) {
                    FileExplorerToolAction(
                        text = "Encrypt file",
                        onClick = {
                            advanced.encrypt(
                                file.ref,
                                directory,
                            )
                        },
                    )
                    if (
                        file.name.endsWith(
                            ".encrypted",
                            ignoreCase = true,
                        )
                    ) {
                        FileExplorerToolAction(
                            text = "Decrypt file",
                            onClick = {
                                advanced.decrypt(
                                    file.ref,
                                    directory,
                                )
                            },
                        )
                    }
                }
                FileExplorerToolAction(
                    text = "Add to encrypted vault",
                    onClick = {
                        advanced.vaultAdd(
                            file.ref,
                            file.name,
                        )
                    },
                )
                FileExplorerToolAction(
                    text =
                        "Watch file integrity",
                    onClick = {
                        advanced.watchIntegrity(
                            file.ref,
                        )
                    },
                )
                if (
                    file.ref.providerId ==
                    "local"
                ) {
                    FileExplorerToolAction(
                        text =
                            "Best-effort secure delete",
                        onClick = {
                            advanced.secureDelete(
                                file.ref,
                            )
                        },
                    )
                }
                if (
                    file.ref.providerId ==
                        "root"
                ) {
                    FileExplorerToolAction(
                        text =
                            "SELinux / mount info",
                        onClick = {
                            advanced.rootSecurityInfo(
                                file.ref.path,
                            )
                        },
                    )
                }
            } else {
                YSuiteListItem(
                    title =
                        "Select one file for advanced tools",
                )
            }

            FileExplorerToolAction(
                text = "Installed apps",
                onClick =
                    advanced::loadInstalledApps,
            )
            FileExplorerToolAction(
                text = "Root modules",
                onClick =
                    advanced::loadRootModules,
            )
            FileExplorerToolAction(
                text = "Integrity scan",
                onClick =
                    advanced::scanIntegrity,
            )
            FileExplorerToolAction(
                text =
                    "Probe encrypted volumes",
                onClick =
                    advanced::probeEncryptedVolumes,
            )
        }
    }

    advancedState.preview?.let {
        preview ->
        item {
            FileExplorerToolGroup(
                title = "Preview",
            ) {
                preview.imagePath?.let {
                    YSuiteListItem(
                        title =
                            "Rendered PDF preview",
                        subtitle = it,
                    )
                }
                if (
                    preview.text.isNotBlank()
                ) {
                    YSuiteListItem(
                        title = preview.title,
                        subtitle =
                            preview.text.take(
                                20_000,
                            ),
                    )
                }
                preview.pageCount?.let {
                    YSuiteListItem(
                        title = "Pages",
                        subtitle =
                            it.toString(),
                    )
                }
            }
        }
    }

    advancedState.hexPage?.let {
        page ->
        item {
            FileExplorerToolGroup(
                title = "Hex",
            ) {
                YSuiteListItem(
                    title =
                        "Offset 0x" +
                            page.offset
                                .toString(16),
                    subtitle =
                        page.formatted,
                )
                advancedState.hexRef
                    ?.let { ref ->
                        FileExplorerToolAction(
                            text = "Next page",
                            onClick = {
                                advanced.loadHex(
                                    ref,
                                    page.offset +
                                        page.bytes.size,
                                )
                            },
                        )
                    }
            }
        }
    }

    advancedState.checksumSet?.let {
        checksums ->
        item {
            FileExplorerToolGroup(
                title = "Checksums",
            ) {
                checksums.values
                    .forEach {
                        (algorithm, value) ->
                        YSuiteListItem(
                            title = algorithm,
                            subtitle = value,
                        )
                    }
            }
        }
    }

    advancedState.apkAnalysis?.let {
        apk ->
        item {
            FileExplorerToolGroup(
                title = "APK Analyzer",
            ) {
                YSuiteListItem(
                    title =
                        apk.packageName
                            ?: apk.fileName,
                    subtitle =
                        listOfNotNull(
                            apk.versionName,
                            apk.versionCode
                                ?.toString(),
                        ).joinToString(
                            " • ",
                        ),
                )
                YSuiteListItem(
                    title = "SDK",
                    subtitle =
                        "min " +
                            (
                                apk.minSdk
                                    ?.toString()
                                    ?: "?"
                                ) +
                            " • target " +
                            (
                                apk.targetSdk
                                    ?.toString()
                                    ?: "?"
                                ),
                )
                YSuiteListItem(
                    title = "DEX methods",
                    subtitle =
                        apk.dexMethodCount
                            .toString(),
                )
                YSuiteListItem(
                    title = "Permissions",
                    subtitle =
                        apk.permissions
                            .joinToString(
                                "
",
                            )
                            .ifBlank {
                                "None declared"
                            },
                )
                apk.signingSha256
                    .forEachIndexed {
                        index,
                        value,
                        ->
                        YSuiteListItem(
                            title =
                                "Signer " +
                                    (
                                        index + 1
                                        ),
                            subtitle = value,
                        )
                    }
            }
        }
    }

    if (
        advancedState.installedApps
            .isNotEmpty()
    ) {
        item {
            FileExplorerToolGroup(
                title = "Installed apps",
            ) {
                advancedState.installedApps
                    .take(100)
                    .forEach {
                        app ->
                        YSuiteListItem(
                            title = app.label,
                            subtitle =
                                app.packageName +
                                    (
                                        if (
                                            app.system
                                        ) {
                                            " • system"
                                        } else {
                                            ""
                                        }
                                        ),
                        )
                    }
            }
        }
    }

    if (
        advancedState.vaultEntries
            .isNotEmpty()
    ) {
        item {
            FileExplorerToolGroup(
                title = "Encrypted vault",
            ) {
                advancedState.vaultEntries
                    .forEach {
                        entry ->
                        YSuiteListItem(
                            title =
                                entry.originalName,
                            subtitle =
                                formatBytes(
                                    entry.sizeBytes,
                                ),
                        )
                        if (directory != null) {
                            FileExplorerToolAction(
                                text =
                                    "Restore " +
                                        entry.originalName,
                                onClick = {
                                    advanced.vaultRestore(
                                        entry.id,
                                        directory,
                                    )
                                },
                            )
                        }
                        FileExplorerToolAction(
                            text =
                                "Delete vault item",
                            onClick = {
                                advanced.vaultDelete(
                                    entry.id,
                                )
                            },
                        )
                    }
            }
        }
    }

    if (
        advancedState.integrityResults
            .isNotEmpty()
    ) {
        item {
            FileExplorerToolGroup(
                title = "Integrity",
            ) {
                advancedState.integrityResults
                    .forEach {
                        result ->
                        YSuiteListItem(
                            title =
                                result.watch.ref.path,
                            subtitle =
                                result.status.name,
                        )
                    }
            }
        }
    }

    if (
        advancedState.rootModules
            .isNotEmpty()
    ) {
        item {
            FileExplorerToolGroup(
                title = "Root modules",
            ) {
                advancedState.rootModules
                    .forEach {
                        module ->
                        YSuiteListItem(
                            title = module.name,
                            subtitle =
                                listOfNotNull(
                                    module.version,
                                    module.author,
                                ).joinToString(
                                    " • ",
                                ),
                        )
                        FileExplorerToolAction(
                            text =
                                if (
                                    module.disabled
                                ) {
                                    "Enable module"
                                } else {
                                    "Disable module"
                                },
                            onClick = {
                                advanced
                                    .setRootModuleEnabled(
                                        module.id,
                                        module.disabled,
                                    )
                            },
                        )
                        FileExplorerToolAction(
                            text =
                                if (
                                    module
                                        .removeOnReboot
                                ) {
                                    "Cancel removal"
                                } else {
                                    "Remove on reboot"
                                },
                            onClick = {
                                advanced
                                    .markRootModuleRemoval(
                                        module.id,
                                        !module
                                            .removeOnReboot,
                                    )
                            },
                        )
                    }
            }
        }
    }

    advancedState.rootSecurityInfo
        ?.let {
            info ->
            item {
                FileExplorerToolGroup(
                    title =
                        "Root security",
                ) {
                    YSuiteListItem(
                        title =
                            "SELinux context",
                        subtitle =
                            info.selinuxContext
                                ?: "Unavailable",
                    )
                    YSuiteListItem(
                        title = "Mount",
                        subtitle =
                            info.mountLine
                                ?: "Unavailable",
                    )
                }
            }
        }

    advancedState.encryptedVolumeSupport
        ?.let {
            support ->
            item {
                FileExplorerToolGroup(
                    title =
                        "Encrypted volumes",
                ) {
                    YSuiteListItem(
                        title = "gocryptfs",
                        subtitle =
                            support.gocryptfs
                                .toString(),
                    )
                    YSuiteListItem(
                        title = "EncFS",
                        subtitle =
                            support.encfs
                                .toString(),
                    )
                    YSuiteListItem(
                        title = "fusermount",
                        subtitle =
                            support.fusermount
                                .toString(),
                    )
                }
            }
        }

    advancedState.message?.let {
        item {
            YSuiteStatusBadge(
                text = it,
                tone =
                    YSuiteStatusTone.Positive,
            )
        }
    }
    advancedState.error?.let {
        item {
            YSuiteStatusBadge(
                text = it,
                tone =
                    YSuiteStatusTone.Error,
            )
        }
    }
}

@Composable
internal fun YFilesAdvancedSettingsContent(
    state: YFilesAdvancedUiState,
    advanced: YFilesAdvancedViewModel,
    browserState: YFilesUiState,
    browser: YFilesViewModel,
) {
    var shareDialog by
        remember { mutableStateOf(false) }
    var shareRoot by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.rootPath
                    .orEmpty(),
            )
        }
    var shareUser by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.username
                    .orEmpty(),
            )
        }
    var sharePassword by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.password
                    .orEmpty(),
            )
        }
    var shareHttpPort by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.httpPort
                    ?.toString()
                    ?: "8088",
            )
        }
    var shareFtpPort by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.ftpPort
                    ?.toString()
                    ?: "2121",
            )
        }
    var shareLan by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.bindLan
                    ?: false,
            )
        }
    var shareHttp by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.httpEnabled
                    ?: true,
            )
        }
    var shareFtp by
        remember(
            state.shareServerSettings,
        ) {
            mutableStateOf(
                state.shareServerSettings
                    ?.ftpEnabled
                    ?: false,
            )
        }

    ComposeSettingsGroup(
        title = "File manager",
    ) {
        ComposeSettingsSwitch(
            title = "Dual pane",
            subtitle =
                "Two independent browser panes",
            checked =
                browserState
                    .dualPaneEnabled,
            onCheckedChange =
                browser::setDualPaneEnabled,
        )
        ComposeSettingsLink(
            title = "Transfers",
            subtitle =
                state.transfers.size
                    .toString() +
                    " task(s)",
            onClick = {
                browser.setTab(
                    YFilesTab.Transfers,
                )
            },
        )
        ComposeSettingsLink(
            title = "Shizuku access",
            subtitle =
                browserState
                    .shizukuStatus
                    .name,
            onClick = {
                advanced
                    .requestShizukuPermission()
                browser
                    .refreshShizukuStatus()
            },
        )
    }

    ComposeSettingsGroup(
        title = "Network connections",
    ) {
        state.networkProfiles
            .forEach {
                profile ->
                ComposeSettingsLink(
                    title = profile.name,
                    subtitle =
                        profile.protocol.name +
                            " • " +
                            profile.host +
                            ":" +
                            profile.port,
                    onClick = {
                        advanced
                            .beginNetworkProfile(
                                profile,
                            )
                    },
                )
            }
        ComposeSettingsLink(
            title = "Add SMB / SFTP / FTP / WebDAV",
            onClick = {
                advanced
                    .beginNetworkProfile()
            },
        )
    }

    ComposeSettingsGroup(
        title = "Cloud accounts",
    ) {
        if (
            state.cloudProfiles.isEmpty()
        ) {
            ComposeSettingsLink(
                title =
                    "No cloud accounts configured",
                subtitle =
                    "Google Drive, Dropbox and OneDrive require an OAuth access token.",
                enabled = false,
                onClick = {},
            )
        }
        state.cloudProfiles
            .forEach {
                profile ->
                ComposeSettingsLink(
                    title = profile.name,
                    subtitle =
                        profile.kind.name,
                    onClick = {
                        advanced
                            .beginCloudProfile(
                                profile,
                            )
                    },
                )
            }
        ComposeSettingsLink(
            title = "Add cloud account",
            onClick = {
                advanced
                    .beginCloudProfile()
            },
        )
    }

    ComposeSettingsGroup(
        title = "LAN sharing",
    ) {
        ComposeSettingsLink(
            title =
                if (
                    state.shareServerState
                        .running
                ) {
                    "Stop share server"
                } else {
                    "Start share server"
                },
            subtitle =
                state.shareServerSettings
                    ?.let {
                        buildString {
                            if (
                                it.httpEnabled
                            ) {
                                append(
                                    "HTTP :",
                                )
                                append(
                                    it.httpPort,
                                )
                            }
                            if (
                                it.ftpEnabled
                            ) {
                                if (
                                    isNotEmpty()
                                ) {
                                    append(" • ")
                                }
                                append(
                                    "FTP :",
                                )
                                append(
                                    it.ftpPort,
                                )
                            }
                            append(
                                if (
                                    it.bindLan
                                ) {
                                    " • LAN"
                                } else {
                                    " • localhost"
                                },
                            )
                        }
                    },
            onClick = {
                if (
                    state.shareServerState
                        .running
                ) {
                    advanced.stopShareServer()
                } else {
                    advanced.startShareServer()
                }
            },
        )
        ComposeSettingsLink(
            title = "Share server settings",
            onClick = {
                shareDialog = true
            },
        )
    }

    ComposeSettingsGroup(
        title = "Automation",
    ) {
        ComposeSettingsSwitch(
            title =
                "Tasker / Automate broadcast API",
            subtitle =
                "Disabled by default. Enable only if you intentionally use external automation.",
            checked =
                state.automationEnabled,
            onCheckedChange =
                advanced::setAutomationEnabled,
        )
    }

    ComposeSettingsGroup(
        title = "Plugins",
    ) {
        ComposeSettingsLink(
            title = "Refresh plugins",
            onClick =
                advanced::refreshPlugins,
        )
        if (state.plugins.isEmpty()) {
            ComposeSettingsLink(
                title =
                    "No YFiles plugins found",
                enabled = false,
                onClick = {},
            )
        }
        state.plugins.forEach {
            plugin ->
            PluginSetting(
                plugin = plugin,
                advanced = advanced,
            )
        }
    }

    state.message?.let {
        YSuiteStatusBadge(
            text = it,
            tone =
                YSuiteStatusTone.Positive,
        )
    }
    state.error?.let {
        YSuiteStatusBadge(
            text = it,
            tone =
                YSuiteStatusTone.Error,
        )
    }

    state.networkDraft?.let {
        draft ->
        YSuiteTextFormDialog(
            title =
                "Network connection",
            fields =
                listOf(
                    YSuiteFormField(
                        "name",
                        "Name",
                        draft.name,
                    ),
                    YSuiteFormField(
                        "host",
                        "Host",
                        draft.host,
                    ),
                    YSuiteFormField(
                        "port",
                        "Port",
                        draft.port,
                    ),
                    YSuiteFormField(
                        "username",
                        "Username",
                        draft.username,
                    ),
                    YSuiteFormField(
                        "password",
                        "Password",
                        draft.password,
                    ),
                    YSuiteFormField(
                        "shareName",
                        "SMB share",
                        draft.shareName,
                    ),
                    YSuiteFormField(
                        "remotePath",
                        "Remote path",
                        draft.remotePath,
                    ),
                    YSuiteFormField(
                        "privateKeyPath",
                        "SFTP private key path",
                        draft.privateKeyPath,
                    ),
                ),
            confirmText = "Save",
            dismissText = "Cancel",
            onValueChange =
                advanced::updateNetworkDraft,
            onConfirm =
                advanced::saveNetworkProfile,
            onDismiss =
                advanced::cancelNetworkDraft,
            extraContent = {
                Text(
                    text =
                        "Protocol: " +
                            draft.protocol.name,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
                YFilesNetworkProtocol.entries
                    .forEach {
                        protocol ->
                        FileExplorerToolAction(
                            text =
                                (
                                    if (
                                        protocol ==
                                        draft.protocol
                                    ) {
                                        "✓ "
                                    } else {
                                        ""
                                    }
                                    ) +
                                    protocol.name,
                            onClick = {
                                advanced
                                    .updateNetworkDraft(
                                        "protocol",
                                        protocol.name,
                                    )
                            },
                        )
                    }
                YSuiteSwitchItem(
                    title = "TLS",
                    checked = draft.useTls,
                    onCheckedChange = {
                        advanced
                            .updateNetworkDraft(
                                "useTls",
                                it.toString(),
                            )
                    },
                )
                FileExplorerToolAction(
                    text = "Test connection",
                    onClick =
                        advanced::testNetworkProfile,
                )
                draft.id?.let {
                    FileExplorerToolAction(
                        text =
                            "Delete connection",
                        onClick = {
                            advanced
                                .deleteNetworkProfile(
                                    it,
                                )
                            advanced
                                .cancelNetworkDraft()
                        },
                    )
                }
            },
        )
    }

    state.cloudDraft?.let {
        draft ->
        YSuiteTextFormDialog(
            title = "Cloud account",
            fields =
                listOf(
                    YSuiteFormField(
                        "name",
                        "Name",
                        draft.name,
                    ),
                    YSuiteFormField(
                        "token",
                        "OAuth access token",
                        draft.accessToken,
                    ),
                ),
            confirmText = "Save",
            dismissText = "Cancel",
            onValueChange =
                advanced::updateCloudDraft,
            onConfirm =
                advanced::saveCloudProfile,
            onDismiss =
                advanced::cancelCloudDraft,
            extraContent = {
                Text(
                    text =
                        "Provider: " +
                            draft.kind.name,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )
                YFilesCloudKind.entries
                    .forEach {
                        kind ->
                        FileExplorerToolAction(
                            text =
                                (
                                    if (
                                        kind ==
                                        draft.kind
                                    ) {
                                        "✓ "
                                    } else {
                                        ""
                                    }
                                    ) +
                                    kind.name,
                            onClick = {
                                advanced
                                    .updateCloudDraft(
                                        "kind",
                                        kind.name,
                                    )
                            },
                        )
                    }
                FileExplorerToolAction(
                    text = "Test account",
                    onClick =
                        advanced::testCloudProfile,
                )
                draft.id?.let {
                    FileExplorerToolAction(
                        text =
                            "Delete account",
                        onClick = {
                            advanced
                                .deleteCloudProfile(
                                    it,
                                )
                            advanced
                                .cancelCloudDraft()
                        },
                    )
                }
            },
        )
    }

    if (shareDialog) {
        YSuiteTextFormDialog(
            title =
                "Share server settings",
            fields =
                listOf(
                    YSuiteFormField(
                        "root",
                        "Shared root",
                        shareRoot,
                    ),
                    YSuiteFormField(
                        "username",
                        "Username",
                        shareUser,
                    ),
                    YSuiteFormField(
                        "password",
                        "Password",
                        sharePassword,
                    ),
                    YSuiteFormField(
                        "httpPort",
                        "HTTP port",
                        shareHttpPort,
                    ),
                    YSuiteFormField(
                        "ftpPort",
                        "FTP port",
                        shareFtpPort,
                    ),
                ),
            confirmText = "Save",
            dismissText = "Cancel",
            onValueChange = {
                field,
                value,
                ->
                when (field) {
                    "root" ->
                        shareRoot = value
                    "username" ->
                        shareUser = value
                    "password" ->
                        sharePassword = value
                    "httpPort" ->
                        shareHttpPort =
                            value
                    "ftpPort" ->
                        shareFtpPort =
                            value
                }
            },
            onConfirm = {
                advanced
                    .saveShareServerSettings(
                        YFilesShareServerSettings(
                            rootPath =
                                shareRoot,
                            username =
                                shareUser,
                            password =
                                sharePassword,
                            bindLan =
                                shareLan,
                            httpPort =
                                shareHttpPort
                                    .toIntOrNull()
                                    ?: 8088,
                            ftpPort =
                                shareFtpPort
                                    .toIntOrNull()
                                    ?: 2121,
                            httpEnabled =
                                shareHttp,
                            ftpEnabled =
                                shareFtp,
                        ),
                    )
                shareDialog = false
            },
            onDismiss = {
                shareDialog = false
            },
            extraContent = {
                YSuiteSwitchItem(
                    title = "Allow LAN access",
                    subtitle =
                        "Off keeps the server on localhost only.",
                    checked = shareLan,
                    onCheckedChange = {
                        shareLan = it
                    },
                )
                YSuiteSwitchItem(
                    title = "HTTP server",
                    checked = shareHttp,
                    onCheckedChange = {
                        shareHttp = it
                    },
                )
                YSuiteSwitchItem(
                    title = "FTP server",
                    checked = shareFtp,
                    onCheckedChange = {
                        shareFtp = it
                    },
                )
            },
        )
    }
}

@Composable
private fun PluginSetting(
    plugin: YFilesPluginDescriptor,
    advanced: YFilesAdvancedViewModel,
) {
    ComposeSettingsSwitch(
        title = plugin.label,
        subtitle =
            plugin.packageName +
                "
SHA-256 " +
                plugin.certificateSha256,
        checked = plugin.approved,
        onCheckedChange = {
            advanced.approvePlugin(
                plugin,
                it,
            )
        },
    )
    ComposeSettingsLink(
        title = "Inspect plugin",
        subtitle =
            plugin.protocolVersion
                ?.let {
                    "Protocol $it • " +
                        plugin.capabilities
                            .joinToString()
                },
        onClick = {
            advanced.inspectPlugin(
                plugin,
            )
        },
    )
}
