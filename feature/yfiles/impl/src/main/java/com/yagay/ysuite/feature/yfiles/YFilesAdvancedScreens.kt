package com.yagay.ysuite.feature.yfiles

import com.yagay.ysuite.ui.YSuiteBackNavigationButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    val transferLabel =
        stringResource(R.string.yfiles_adv_transfer)
    val speedLimitLabel =
        stringResource(R.string.yfiles_adv_speed_limit)

    FileExplorerUtilitySurface(
        title = stringResource(R.string.yfiles_adv_transfers),
        navigationIcon = {
            YSuiteBackNavigationButton(
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
                        title = stringResource(R.string.yfiles_adv_no_transfers),
                        subtitle =
                            stringResource(R.string.yfiles_adv_no_transfers_subtitle),
                    )
                }
            }
            items(
                items = state.transfers,
                key = { it.id },
            ) { task ->
                val operationLabel =
                    when (task.operation) {
                        YFilesTransferOperation.Copy ->
                            stringResource(R.string.yfiles_copy)
                        YFilesTransferOperation.Move ->
                            stringResource(R.string.yfiles_move)
                    }
                val stateLabel =
                    when (task.state) {
                        YFilesTransferState.Pending ->
                            stringResource(R.string.yfiles_adv_state_pending)
                        YFilesTransferState.Running ->
                            stringResource(R.string.yfiles_adv_state_running)
                        YFilesTransferState.Paused ->
                            stringResource(R.string.yfiles_adv_state_paused)
                        YFilesTransferState.Completed ->
                            stringResource(R.string.yfiles_adv_state_completed)
                        YFilesTransferState.Failed ->
                            stringResource(R.string.yfiles_adv_state_failed)
                        YFilesTransferState.Cancelled ->
                            stringResource(R.string.yfiles_adv_state_cancelled)
                    }

                FileExplorerToolGroup(
                    title = operationLabel,
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
                                        transferLabel
                                    },
                        subtitle =
                            buildString {
                                append(stateLabel)
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
                                    append(" • ")
                                    append(speedLimitLabel)
                                    append(" ")
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
                                text = stringResource(R.string.yfiles_adv_pause),
                                onClick = {
                                    advanced.pauseTransfer(
                                        task.id,
                                    )
                                },
                            )
                        YFilesTransferState.Paused ->
                            FileExplorerToolAction(
                                text = stringResource(R.string.yfiles_adv_resume),
                                onClick = {
                                    advanced.resumeTransfer(
                                        task.id,
                                    )
                                },
                            )
                        YFilesTransferState.Failed ->
                            FileExplorerToolAction(
                                text = stringResource(R.string.yfiles_adv_retry),
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
                            text = stringResource(R.string.yfiles_adv_cancel),
                            onClick = {
                                advanced.cancelTransfer(
                                    task.id,
                                )
                            },
                        )
                    }
                    FileExplorerToolAction(
                        text = stringResource(R.string.yfiles_adv_move_up),
                        onClick = {
                            advanced.moveTransferUp(
                                task.id,
                            )
                        },
                    )
                    FileExplorerToolAction(
                        text = stringResource(R.string.yfiles_adv_move_down),
                        onClick = {
                            advanced.moveTransferDown(
                                task.id,
                            )
                        },
                    )
                    FileExplorerToolAction(
                        text = stringResource(R.string.yfiles_adv_remove_history),
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
            title = stringResource(R.string.yfiles_adv_advanced),
        ) {
            if (file != null) {
                FileExplorerToolAction(
                    text = stringResource(R.string.yfiles_adv_document_preview),
                    onClick = {
                        advanced.preview(file)
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(R.string.yfiles_adv_hex_editor),
                    onClick = {
                        advanced.loadHex(
                            file.ref,
                        )
                    },
                )
                FileExplorerToolAction(
                    text = stringResource(R.string.yfiles_adv_all_checksums),
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
                        text = stringResource(R.string.yfiles_adv_apk_analyzer),
                        onClick = {
                            advanced.analyzeApk(
                                file.ref,
                            )
                        },
                    )
                }
                if (directory != null) {
                    FileExplorerToolAction(
                        text = stringResource(R.string.yfiles_adv_encrypt_file),
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
                            text = stringResource(R.string.yfiles_adv_decrypt_file),
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
                    text = stringResource(R.string.yfiles_adv_add_vault),
                    onClick = {
                        advanced.vaultAdd(
                            file.ref,
                            file.name,
                        )
                    },
                )
                FileExplorerToolAction(
                    text =
                        stringResource(R.string.yfiles_adv_watch_integrity),
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
                            stringResource(R.string.yfiles_adv_secure_delete),
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
                            stringResource(R.string.yfiles_adv_selinux_mount_info),
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
                        stringResource(R.string.yfiles_adv_select_one_file),
                )
            }

            FileExplorerToolAction(
                text = stringResource(R.string.yfiles_adv_installed_apps),
                onClick =
                    advanced::loadInstalledApps,
            )
            FileExplorerToolAction(
                text = stringResource(R.string.yfiles_adv_root_modules),
                onClick =
                    advanced::loadRootModules,
            )
            FileExplorerToolAction(
                text = stringResource(R.string.yfiles_adv_integrity_scan),
                onClick =
                    advanced::scanIntegrity,
            )
            FileExplorerToolAction(
                text =
                    stringResource(R.string.yfiles_adv_probe_encrypted_volumes),
                onClick =
                    advanced::probeEncryptedVolumes,
            )
        }
    }

    advancedState.preview?.let {
        preview ->
        item {
            FileExplorerToolGroup(
                title = stringResource(R.string.yfiles_adv_preview),
            ) {
                preview.imagePath?.let {
                    YSuiteListItem(
                        title =
                            stringResource(R.string.yfiles_adv_rendered_pdf_preview),
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
                        title = stringResource(R.string.yfiles_adv_pages),
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
                title = stringResource(R.string.yfiles_adv_hex),
            ) {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string.yfiles_adv_offset_hex,
                            page.offset.toString(16),
                        ),
                    subtitle =
                        page.formatted,
                )
                advancedState.hexRef
                    ?.let { ref ->
                        FileExplorerToolAction(
                            text = stringResource(R.string.yfiles_adv_next_page),
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
                title = stringResource(R.string.yfiles_adv_checksums),
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
                title = stringResource(R.string.yfiles_adv_apk_analyzer),
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
                    title = stringResource(R.string.yfiles_adv_sdk),
                    subtitle =
                        stringResource(
                            R.string.yfiles_adv_sdk_summary,
                            apk.minSdk?.toString() ?: "?",
                            apk.targetSdk?.toString() ?: "?",
                        ),
                )
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_adv_dex_methods),
                    subtitle =
                        apk.dexMethodCount
                            .toString(),
                )
                YSuiteListItem(
                    title = stringResource(R.string.yfiles_adv_permissions),
                    subtitle =
                        apk.permissions
                            .joinToString("\n")
                            .ifBlank {
                                stringResource(
                                    R.string.yfiles_adv_none_declared,
                                )
                            },
                )
                apk.signingSha256
                    .forEachIndexed {
                        index,
                        value,
                        ->
                        YSuiteListItem(
                            title =
                                stringResource(
                                    R.string.yfiles_adv_signer,
                                    index + 1,
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
                title = stringResource(R.string.yfiles_adv_installed_apps),
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
                                        if (app.system) {
                                            " • " +
                                                stringResource(
                                                    R.string.yfiles_adv_system,
                                                )
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
                title = stringResource(R.string.yfiles_adv_encrypted_vault),
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
                                    stringResource(
                                        R.string.yfiles_adv_restore,
                                        entry.originalName,
                                    ),
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
                                stringResource(R.string.yfiles_adv_delete_vault_item),
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
                title = stringResource(R.string.yfiles_adv_integrity),
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
                title = stringResource(R.string.yfiles_adv_root_modules),
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
                                stringResource(
                                    if (module.disabled) {
                                        R.string.yfiles_adv_enable_module
                                    } else {
                                        R.string.yfiles_adv_disable_module
                                    },
                                ),
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
                                stringResource(
                                    if (module.removeOnReboot) {
                                        R.string.yfiles_adv_cancel_removal
                                    } else {
                                        R.string.yfiles_adv_remove_on_reboot
                                    },
                                ),
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
                        stringResource(R.string.yfiles_adv_root_security),
                ) {
                    YSuiteListItem(
                        title =
                            stringResource(R.string.yfiles_adv_selinux_context),
                        subtitle =
                            info.selinuxContext
                                ?: stringResource(
                                    R.string.yfiles_adv_unavailable,
                                ),
                    )
                    YSuiteListItem(
                        title = stringResource(R.string.yfiles_adv_mount),
                        subtitle =
                            info.mountLine
                                ?: stringResource(
                                    R.string.yfiles_adv_unavailable,
                                ),
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
                        stringResource(R.string.yfiles_adv_encrypted_volumes),
                ) {
                    YSuiteListItem(
                        title = stringResource(R.string.yfiles_adv_gocryptfs),
                        subtitle =
                            support.gocryptfs
                                .toString(),
                    )
                    YSuiteListItem(
                        title = stringResource(R.string.yfiles_adv_encfs),
                        subtitle =
                            support.encfs
                                .toString(),
                    )
                    YSuiteListItem(
                        title = stringResource(R.string.yfiles_adv_fusermount),
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
        title = stringResource(R.string.yfiles_adv_file_manager),
    ) {
        ComposeSettingsSwitch(
            title = stringResource(R.string.yfiles_adv_dual_pane),
            subtitle =
                stringResource(R.string.yfiles_adv_dual_pane_subtitle),
            checked =
                browserState
                    .dualPaneEnabled,
            onCheckedChange =
                browser::setDualPaneEnabled,
        )
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_adv_transfers),
            subtitle =
                stringResource(
                    R.string.yfiles_adv_transfer_count,
                    state.transfers.size,
                ),
            onClick = {
                browser.setTab(
                    YFilesTab.Transfers,
                )
            },
        )
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_adv_shizuku_access),
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
        title = stringResource(R.string.yfiles_adv_network_connections),
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
            title = stringResource(R.string.yfiles_adv_add_network),
            onClick = {
                advanced
                    .beginNetworkProfile()
            },
        )
    }

    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_adv_cloud_accounts),
    ) {
        if (
            state.cloudProfiles.isEmpty()
        ) {
            ComposeSettingsLink(
                title =
                    stringResource(R.string.yfiles_adv_no_cloud),
                subtitle =
                    stringResource(R.string.yfiles_adv_no_cloud_subtitle),
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
            title = stringResource(R.string.yfiles_adv_add_cloud),
            onClick = {
                advanced
                    .beginCloudProfile()
            },
        )
    }

    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_adv_lan_sharing),
    ) {
        ComposeSettingsLink(
            title =
                stringResource(
                    if (state.shareServerState.running) {
                        R.string.yfiles_adv_stop_share
                    } else {
                        R.string.yfiles_adv_start_share
                    },
                ),
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
            title = stringResource(R.string.yfiles_adv_share_settings),
            onClick = {
                shareDialog = true
            },
        )
    }

    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_adv_automation),
    ) {
        ComposeSettingsSwitch(
            title =
                stringResource(R.string.yfiles_adv_automation_api),
            subtitle =
                stringResource(R.string.yfiles_adv_automation_subtitle),
            checked =
                state.automationEnabled,
            onCheckedChange =
                advanced::setAutomationEnabled,
        )
    }

    ComposeSettingsGroup(
        title = stringResource(R.string.yfiles_adv_plugins),
    ) {
        ComposeSettingsLink(
            title = stringResource(R.string.yfiles_adv_refresh_plugins),
            onClick =
                advanced::refreshPlugins,
        )
        if (state.plugins.isEmpty()) {
            ComposeSettingsLink(
                title =
                    stringResource(R.string.yfiles_adv_no_plugins),
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
                stringResource(R.string.yfiles_adv_network_connection),
            fields =
                listOf(
                    YSuiteFormField(
                        "name",
                        stringResource(R.string.yfiles_adv_name),
                        draft.name,
                    ),
                    YSuiteFormField(
                        "host",
                        stringResource(R.string.yfiles_adv_host),
                        draft.host,
                    ),
                    YSuiteFormField(
                        "port",
                        stringResource(R.string.yfiles_adv_port),
                        draft.port,
                    ),
                    YSuiteFormField(
                        "username",
                        stringResource(R.string.yfiles_adv_username),
                        draft.username,
                    ),
                    YSuiteFormField(
                        "password",
                        stringResource(R.string.yfiles_adv_password),
                        draft.password,
                    ),
                    YSuiteFormField(
                        "shareName",
                        stringResource(R.string.yfiles_adv_smb_share),
                        draft.shareName,
                    ),
                    YSuiteFormField(
                        "remotePath",
                        stringResource(R.string.yfiles_adv_remote_path),
                        draft.remotePath,
                    ),
                    YSuiteFormField(
                        "privateKeyPath",
                        stringResource(R.string.yfiles_adv_sftp_key),
                        draft.privateKeyPath,
                    ),
                ),
            confirmText = stringResource(R.string.yfiles_adv_save),
            dismissText = stringResource(R.string.yfiles_adv_cancel),
            onValueChange =
                advanced::updateNetworkDraft,
            onConfirm =
                advanced::saveNetworkProfile,
            onDismiss =
                advanced::cancelNetworkDraft,
            extraContent = {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string.yfiles_adv_protocol,
                            draft.protocol.name,
                        ),
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
                    title = stringResource(R.string.yfiles_adv_tls),
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
                    text = stringResource(R.string.yfiles_adv_test_connection),
                    onClick =
                        advanced::testNetworkProfile,
                )
                draft.id?.let {
                    FileExplorerToolAction(
                        text =
                            stringResource(R.string.yfiles_adv_delete_connection),
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
            title = stringResource(R.string.yfiles_adv_cloud_account),
            fields =
                listOf(
                    YSuiteFormField(
                        "name",
                        stringResource(R.string.yfiles_adv_name),
                        draft.name,
                    ),
                    YSuiteFormField(
                        "token",
                        stringResource(R.string.yfiles_adv_oauth_token),
                        draft.accessToken,
                    ),
                ),
            confirmText = stringResource(R.string.yfiles_adv_save),
            dismissText = stringResource(R.string.yfiles_adv_cancel),
            onValueChange =
                advanced::updateCloudDraft,
            onConfirm =
                advanced::saveCloudProfile,
            onDismiss =
                advanced::cancelCloudDraft,
            extraContent = {
                YSuiteListItem(
                    title =
                        stringResource(
                            R.string.yfiles_adv_provider,
                            draft.kind.name,
                        ),
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
                    text = stringResource(R.string.yfiles_adv_test_account),
                    onClick =
                        advanced::testCloudProfile,
                )
                draft.id?.let {
                    FileExplorerToolAction(
                        text =
                            stringResource(R.string.yfiles_adv_delete_account),
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
                stringResource(R.string.yfiles_adv_share_settings),
            fields =
                listOf(
                    YSuiteFormField(
                        "root",
                        stringResource(R.string.yfiles_adv_shared_root),
                        shareRoot,
                    ),
                    YSuiteFormField(
                        "username",
                        stringResource(R.string.yfiles_adv_username),
                        shareUser,
                    ),
                    YSuiteFormField(
                        "password",
                        stringResource(R.string.yfiles_adv_password),
                        sharePassword,
                    ),
                    YSuiteFormField(
                        "httpPort",
                        stringResource(R.string.yfiles_adv_http_port),
                        shareHttpPort,
                    ),
                    YSuiteFormField(
                        "ftpPort",
                        stringResource(R.string.yfiles_adv_ftp_port),
                        shareFtpPort,
                    ),
                ),
            confirmText = stringResource(R.string.yfiles_adv_save),
            dismissText = stringResource(R.string.yfiles_adv_cancel),
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
                    title = stringResource(R.string.yfiles_adv_allow_lan),
                    subtitle =
                        stringResource(R.string.yfiles_adv_allow_lan_subtitle),
                    checked = shareLan,
                    onCheckedChange = {
                        shareLan = it
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yfiles_adv_http_server),
                    checked = shareHttp,
                    onCheckedChange = {
                        shareHttp = it
                    },
                )
                YSuiteSwitchItem(
                    title = stringResource(R.string.yfiles_adv_ftp_server),
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
                "\nSHA-256 " +
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
        title = stringResource(R.string.yfiles_adv_inspect_plugin),
        subtitle =
            plugin.protocolVersion
                ?.let {
                    stringResource(
                        R.string.yfiles_adv_plugin_protocol,
                        it,
                        plugin.capabilities.joinToString(),
                    )
                },
        onClick = {
            advanced.inspectPlugin(
                plugin,
            )
        },
    )
}
