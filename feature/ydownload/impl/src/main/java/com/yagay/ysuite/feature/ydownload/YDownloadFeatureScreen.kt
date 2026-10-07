package com.yagay.ysuite.feature.ydownload

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yagay.ysuite.feature.ydownload.api.YDownloadItem
import com.yagay.ysuite.feature.ydownload.api.YDownloadState
import com.yagay.ysuite.feature.ydownload.api.YDownloadTab
import com.yagay.ysuite.feature.ydownload.api.forTab
import com.yagay.ysuite.logging.api.YSuiteLogger
import com.yagay.ysuite.productui.download.QdmAddDownloadDialog
import com.yagay.ysuite.productui.download.QdmAddDownloadLabels
import com.yagay.ysuite.productui.download.QdmAddDownloadModel
import com.yagay.ysuite.productui.download.QdmDownloadActionLabels
import com.yagay.ysuite.productui.download.QdmDownloadFabLabels
import com.yagay.ysuite.productui.download.QdmDownloadList
import com.yagay.ysuite.productui.download.QdmDownloadMenuLabels
import com.yagay.ysuite.productui.download.QdmDownloadPropertiesDialog
import com.yagay.ysuite.productui.download.QdmDownloadProperty
import com.yagay.ysuite.productui.download.QdmDownloadRowModel
import com.yagay.ysuite.productui.download.QdmDownloadTab
import com.yagay.ysuite.productui.download.QdmDownloadWorkspace
import com.yagay.ysuite.ui.YSuiteFeatureBackHandler
import com.yagay.ysuite.ui.YSuiteHostNavigationButton
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YDownloadFeatureScreen(
    environment: YDownloadEnvironment,
    logger: YSuiteLogger,
) {
    val localContext = LocalContext.current
    val context =
        localContext.applicationContext
    val model: YDownloadViewModel =
        viewModel(
            factory =
                YDownloadViewModel.Factory(
                    context = context,
                    environment = environment,
                    logger = logger,
                ),
        )
    val state by
        model.state.collectAsStateWithLifecycle()

    if (state.page == YDownloadPage.Settings) {
        YSuiteFeatureBackHandler(onBack = model::backToMain)
        YDownloadSettingsScreen(
            settings = state.settings,
            systemPatch = state.systemPatch,
            systemPatchScopeCount =
                model.systemPatchScopeCount(),
            onBack = model::backToMain,
            onDefaultBackend =
                model::setDefaultBackend,
            onDefaultTreeUri =
                model::setDefaultTreeUri,
            onMaxConcurrent =
                model::setMaxConcurrent,
            onDefaultThreadCount =
                model::setDefaultThreadCount,
            onSpeedLimit =
                model::setSpeedLimit,
            onWifiOnly =
                model::setWifiOnly,
            onAutoResumeNetwork =
                model::setAutoResumeNetwork,
            onNotifications =
                model::setNotifications,
            onUserAgent =
                model::setDefaultUserAgent,
            onSystemPatchEnabled =
                model::setSystemPatchEnabled,
            onSystemPatchAllowMetered =
                model::setSystemPatchAllowMetered,
            onSystemPatchAllowRoaming =
                model::setSystemPatchAllowRoaming,
            onSystemPatchRequireCharging =
                model::setSystemPatchRequireCharging,
            onSystemPatchRequireIdle =
                model::setSystemPatchRequireIdle,
            onSystemPatchCompletionNotification =
                model::setSystemPatchCompletionNotification,
            onSystemPatchScope =
                model::requestSystemPatchScope,
            onSystemPatchSync =
                model::syncSystemPatch,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val content =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        }.getOrDefault("")
                    }
                model.importUrls(
                    content.split(
                        Regex("[,\\n\\r]+"),
                    ),
                )
            }
        }

    val downloadFolderLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver
                    .takePersistableUriPermission(
                        uri,
                        flags,
                    )
            }
            model.updateDestinationTreeUri(
                uri.toString(),
            )
        }

    val tabs =
        YDownloadTab.entries.map { tab ->
            QdmDownloadTab(
                id = tab.name,
                label = tab.label(),
            )
        }

    val filtered =
        model.sorted(
            state.items.forTab(
                tab = state.selectedTab,
                query = state.searchQuery,
            ),
        )

    QdmDownloadWorkspace(
        title =
            stringResource(
                R.string.ydownload_title,
            ),
        tabs = tabs,
        selectedTabId = state.selectedTab.name,
        onTabSelected = {
            runCatching {
                YDownloadTab.valueOf(it)
            }.getOrNull()
                ?.let(model::selectTab)
        },
        searchActive = state.searchActive,
        searchQuery = state.searchQuery,
        searchPlaceholder =
            stringResource(
                R.string.ydownload_search,
            ),
        addContentDescription =
            stringResource(
                R.string.ydownload_add,
            ),
        closeSearchContentDescription =
            stringResource(
                R.string.ydownload_close_search,
            ),
        onSearchQueryChange =
            model::setSearchQuery,
        onToggleSearch = model::toggleSearch,
        onAdd = { model.showAddDialog() },
        navigationIcon = {
            YSuiteHostNavigationButton()
        },
        menuLabels =
            QdmDownloadMenuLabels(
                sortDate =
                    stringResource(
                        R.string.ydownload_sort_date,
                    ),
                sortName =
                    stringResource(
                        R.string.ydownload_sort_name,
                    ),
                clearCompleted =
                    stringResource(
                        R.string.ydownload_clear_completed,
                    ),
                settings =
                    stringResource(
                        R.string.ydownload_settings,
                    ),
            ),
        fabLabels =
            QdmDownloadFabLabels(
                add =
                    stringResource(
                        R.string.ydownload_add,
                    ),
                paste =
                    stringResource(
                        R.string.ydownload_paste_clipboard,
                    ),
                importFile =
                    stringResource(
                        R.string.ydownload_import_file,
                    ),
            ),
        onSettings = model::showSettings,
        onSortDate = model::sortByDate,
        onSortName = model::sortByName,
        onClearCompleted = model::clearCompleted,
        onPasteClipboard = {
            val clipboard =
                context.getSystemService(
                    Context.CLIPBOARD_SERVICE,
                ) as ClipboardManager
            val text =
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
            model.importUrls(listOf(text))
        },
        onImportFile = {
            importLauncher.launch(
                arrayOf(
                    "text/plain",
                    "text/*",
                ),
            )
        },
    ) { _, _ ->
        QdmDownloadList(
            items =
                filtered.map {
                    it.toRowModel()
                },
            emptyText =
                stringResource(
                    R.string.ydownload_empty,
                ),
            labels =
                QdmDownloadActionLabels(
                    pause =
                        stringResource(
                            R.string.ydownload_pause,
                        ),
                    resume =
                        stringResource(
                            R.string.ydownload_resume,
                        ),
                    cancel =
                        stringResource(
                            R.string.ydownload_cancel,
                        ),
                    open =
                        stringResource(
                            R.string.ydownload_open,
                        ),
                    retry =
                        stringResource(
                            R.string.ydownload_retry,
                        ),
                    remove =
                        stringResource(
                            R.string.ydownload_remove,
                        ),
                    more =
                        stringResource(
                            R.string.ydownload_more,
                        ),
                    share =
                        stringResource(
                            R.string.ydownload_share,
                        ),
                    copyLink =
                        stringResource(
                            R.string.ydownload_copy_link,
                        ),
                    openFolder =
                        stringResource(
                            R.string.ydownload_open_folder,
                        ),
                    properties =
                        stringResource(
                            R.string.ydownload_properties,
                        ),
                    redownload =
                        stringResource(
                            R.string.ydownload_redownload,
                        ),
                ),
            onPause = model::pause,
            onResume = model::resume,
            onCancel = model::cancel,
            onOpen = model::open,
            onRetry = model::retry,
            onRemove = model::remove,
            onShare = model::share,
            onCopyLink = model::copyLink,
            onOpenFolder = model::openFolder,
            onProperties = model::showProperties,
            onRedownload = model::redownload,
        )
    }

    if (state.addDialogVisible) {
        val draft = state.addDraft

        fun chooseSchedule() {
            val initial =
                Calendar.getInstance().apply {
                    draft.scheduledAtMillis?.let {
                        timeInMillis = it
                    }
                }
            DatePickerDialog(
                localContext,
                { _, year, month, day ->
                    val selected =
                        Calendar.getInstance().apply {
                            timeInMillis =
                                initial.timeInMillis
                            set(
                                Calendar.YEAR,
                                year,
                            )
                            set(
                                Calendar.MONTH,
                                month,
                            )
                            set(
                                Calendar.DAY_OF_MONTH,
                                day,
                            )
                        }
                    TimePickerDialog(
                        localContext,
                        { _, hour, minute ->
                            selected.set(
                                Calendar.HOUR_OF_DAY,
                                hour,
                            )
                            selected.set(
                                Calendar.MINUTE,
                                minute,
                            )
                            selected.set(
                                Calendar.SECOND,
                                0,
                            )
                            selected.set(
                                Calendar.MILLISECOND,
                                0,
                            )
                            model.updateScheduledAt(
                                selected.timeInMillis,
                            )
                        },
                        initial.get(
                            Calendar.HOUR_OF_DAY,
                        ),
                        initial.get(Calendar.MINUTE),
                        true,
                    ).show()
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH),
            ).apply {
                datePicker.minDate =
                    System.currentTimeMillis() -
                        60_000L
            }.show()
        }

        QdmAddDownloadDialog(
            model =
                QdmAddDownloadModel(
                    url = draft.url,
                    fileName = draft.fileName,
                    referer = draft.referer,
                    userAgent = draft.userAgent,
                    cookies = draft.cookies,
                    username = draft.username,
                    password = draft.password,
                    destinationText =
                        draft.destinationTreeUri
                            ?: state.settings.defaultTreeUri
                            ?: stringResource(
                                R.string
                                    .ydownload_default_folder_system,
                            ),
                    threadCount = draft.threadCount,
                    threadSelectionEnabled =
                        draft.supportsRanges &&
                            draft.totalBytes > 0L,
                    speedLimitBytesPerSecond =
                        draft.speedLimitBytesPerSecond,
                    customHeadersText =
                        draft.customHeadersText,
                    scheduleText =
                        draft.scheduledAtMillis
                            ?.let {
                                DateFormat
                                    .getDateTimeInstance()
                                    .format(Date(it))
                            }
                            ?: stringResource(
                                R.string
                                    .ydownload_schedule_none,
                            ),
                    hasSchedule =
                        draft.scheduledAtMillis != null,
                    metadataText =
                        metadataText(
                            totalBytes =
                                draft.totalBytes,
                            mimeType = draft.mimeType,
                            supportsRanges =
                                draft.supportsRanges,
                        ),
                    loading = draft.loading,
                    error = draft.error,
                ),
            labels =
                QdmAddDownloadLabels(
                    title =
                        stringResource(
                            R.string.ydownload_add,
                        ),
                    url =
                        stringResource(
                            R.string.ydownload_url,
                        ),
                    fileName =
                        stringResource(
                            R.string.ydownload_file_name,
                        ),
                    referer =
                        stringResource(
                            R.string.ydownload_referer,
                        ),
                    userAgent =
                        stringResource(
                            R.string.ydownload_user_agent,
                        ),
                    cookies =
                        stringResource(
                            R.string.ydownload_cookies,
                        ),
                    username =
                        stringResource(
                            R.string.ydownload_username,
                        ),
                    password =
                        stringResource(
                            R.string.ydownload_password,
                        ),
                    destination =
                        stringResource(
                            R.string.ydownload_save_folder,
                        ),
                    chooseFolder =
                        stringResource(
                            R.string.ydownload_choose_folder,
                        ),
                    useDefaultFolder =
                        stringResource(
                            R.string
                                .ydownload_use_default_folder,
                        ),
                    threads =
                        stringResource(
                            R.string.ydownload_threads,
                        ),
                    speedLimit =
                        stringResource(
                            R.string
                                .ydownload_task_speed_limit,
                        ),
                    customHeaders =
                        stringResource(
                            R.string
                                .ydownload_custom_headers,
                        ),
                    schedule =
                        stringResource(
                            R.string.ydownload_schedule,
                        ),
                    clearSchedule =
                        stringResource(
                            R.string
                                .ydownload_clear_schedule,
                        ),
                    fetch =
                        stringResource(
                            R.string.ydownload_fetch,
                        ),
                    addQueue =
                        stringResource(
                            R.string.ydownload_add_queue,
                        ),
                    start =
                        stringResource(
                            if (
                                draft.scheduledAtMillis !=
                                null
                            ) {
                                R.string.ydownload_schedule
                            } else {
                                R.string.ydownload_start
                            },
                        ),
                    cancel =
                        stringResource(
                            R.string.ydownload_cancel,
                        ),
                ),
            onUrlChange = model::updateUrl,
            onFileNameChange =
                model::updateFileName,
            onRefererChange =
                model::updateReferer,
            onUserAgentChange =
                model::updateUserAgent,
            onCookiesChange =
                model::updateCookies,
            onUsernameChange =
                model::updateUsername,
            onPasswordChange =
                model::updatePassword,
            onChooseFolder = {
                downloadFolderLauncher.launch(null)
            },
            onUseDefaultFolder = {
                model.updateDestinationTreeUri(null)
            },
            onThreadCountChange =
                model::updateThreadCount,
            onSpeedLimitChange =
                model::updateSpeedLimitBytesPerSecond,
            onCustomHeadersChange =
                model::updateCustomHeaders,
            onChooseSchedule = ::chooseSchedule,
            onClearSchedule = {
                model.updateScheduledAt(null)
            },
            onFetch = model::fetchMetadata,
            onAddQueue = model::addToQueue,
            onStart = model::addAndStart,
            onDismiss = model::dismissAddDialog,
        )
    }

    state.propertiesItemId
        ?.let { id ->
            state.items.firstOrNull {
                it.id == id
            }
        }
        ?.let { item ->
            QdmDownloadPropertiesDialog(
                title =
                    stringResource(
                        R.string.ydownload_properties,
                    ),
                properties =
                    item.properties(),
                closeLabel =
                    stringResource(
                        R.string.ydownload_close,
                    ),
                onDismiss =
                    model::dismissProperties,
            )
        }
}

@Composable
private fun YDownloadTab.label(): String =
    stringResource(
        when (this) {
            YDownloadTab.All ->
                R.string.ydownload_tab_all
            YDownloadTab.Downloading ->
                R.string.ydownload_tab_downloading
            YDownloadTab.Pending ->
                R.string.ydownload_tab_pending
            YDownloadTab.Queue ->
                R.string.ydownload_tab_queue
            YDownloadTab.Finished ->
                R.string.ydownload_tab_finished
            YDownloadTab.Error ->
                R.string.ydownload_tab_error
            YDownloadTab.Scheduled ->
                R.string.ydownload_tab_scheduled
        },
    )

@Composable
private fun YDownloadItem.toRowModel():
    QdmDownloadRowModel {
    val stateLabel =
        stringResource(
            when (state) {
                YDownloadState.Pending ->
                    if (queued) {
                        R.string.ydownload_state_queued
                    } else {
                        R.string.ydownload_state_pending
                    }
                YDownloadState.Connecting ->
                    R.string.ydownload_state_connecting
                YDownloadState.Downloading ->
                    R.string.ydownload_state_downloading
                YDownloadState.Paused ->
                    R.string.ydownload_state_paused
                YDownloadState.Completed ->
                    R.string.ydownload_state_completed
                YDownloadState.Failed ->
                    R.string.ydownload_state_failed
                YDownloadState.Cancelled ->
                    R.string.ydownload_state_cancelled
                YDownloadState.Scheduled ->
                    R.string.ydownload_state_scheduled
            },
        )

    val sizeText =
        if (totalBytes > 0L) {
            formatBytes(downloadedBytes) +
                " / " +
                formatBytes(totalBytes)
        } else {
            formatBytes(downloadedBytes)
        }

    val speedEta =
        if (
            state == YDownloadState.Downloading ||
            state == YDownloadState.Connecting
        ) {
            val speed =
                formatBytes(
                    speedBytesPerSecond,
                ) + "/s"
            val eta =
                if (etaSeconds > 0L) {
                    formatDuration(etaSeconds)
                } else {
                    "--"
                }
            speed + " · " + eta
        } else {
            errorMessage
        }

    return QdmDownloadRowModel(
        id = id,
        fileName = fileName,
        sizeProgressText = sizeText,
        stateText = stateLabel,
        speedEtaText = speedEta,
        progress = progress,
        canPause =
            state == YDownloadState.Downloading ||
                state == YDownloadState.Connecting,
        canResume =
            state == YDownloadState.Paused ||
                state == YDownloadState.Cancelled ||
                (
                    state == YDownloadState.Pending &&
                        !queued
                ),
        canCancel =
            state == YDownloadState.Downloading ||
                state == YDownloadState.Connecting ||
                state == YDownloadState.Paused ||
                state == YDownloadState.Pending ||
                state == YDownloadState.Scheduled,
        canOpen =
            state == YDownloadState.Completed &&
                outputUri != null,
        canRetry =
            state == YDownloadState.Failed,
        canRemove =
            state != YDownloadState.Downloading &&
                state != YDownloadState.Connecting,
    )
}

@Composable
private fun YDownloadItem.properties():
    List<QdmDownloadProperty> =
    buildList {
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_file_name,
                ),
                fileName,
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_url,
                ),
                url,
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_size,
                ),
                if (totalBytes > 0L) {
                    formatBytes(totalBytes)
                } else {
                    "--"
                },
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_downloaded,
                ),
                formatBytes(downloadedBytes),
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_mime,
                ),
                mimeType.ifBlank { "--" },
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_save_folder,
                ),
                destinationTreeUri
                    ?: stringResource(
                        R.string.ydownload_default_folder_system,
                    ),
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_threads,
                ),
                threadCount.toString(),
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_task_speed_limit,
                ),
                if (speedLimitBytesPerSecond > 0L) {
                    formatBytes(
                        speedLimitBytesPerSecond,
                    ) + "/s"
                } else {
                    stringResource(
                        R.string.ydownload_unlimited,
                    )
                },
            ),
        )
        if (customHeaders.isNotEmpty()) {
            add(
                QdmDownloadProperty(
                    stringResource(
                        R.string.ydownload_property_custom_headers,
                    ),
                    customHeaders.entries
                        .joinToString("\n") {
                            it.key + ": " + it.value
                        },
                ),
            )
        }
        scheduledAtMillis?.let {
            add(
                QdmDownloadProperty(
                    stringResource(
                        R.string.ydownload_property_scheduled,
                    ),
                    DateFormat.getDateTimeInstance()
                        .format(Date(it)),
                ),
            )
        }
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_resumable,
                ),
                stringResource(
                    if (supportsRanges) {
                        R.string.ydownload_yes
                    } else {
                        R.string.ydownload_no
                    },
                ),
            ),
        )
        add(
            QdmDownloadProperty(
                stringResource(
                    R.string.ydownload_property_added,
                ),
                DateFormat.getDateTimeInstance()
                    .format(
                        Date(addedAtMillis),
                    ),
            ),
        )
        completedAtMillis?.let {
            add(
                QdmDownloadProperty(
                    stringResource(
                        R.string.ydownload_property_completed,
                    ),
                    DateFormat.getDateTimeInstance()
                        .format(Date(it)),
                ),
            )
        }
        errorMessage?.let {
            add(
                QdmDownloadProperty(
                    stringResource(
                        R.string.ydownload_property_error,
                    ),
                    it,
                ),
            )
        }
    }

@Composable
private fun metadataText(
    totalBytes: Long,
    mimeType: String,
    supportsRanges: Boolean,
): String? {
    if (
        totalBytes < 0L &&
        mimeType.isBlank() &&
        !supportsRanges
    ) {
        return null
    }

    val size =
        if (totalBytes >= 0L) {
            formatBytes(totalBytes)
        } else {
            "--"
        }
    val range =
        stringResource(
            if (supportsRanges) {
                R.string.ydownload_resume_supported
            } else {
                R.string.ydownload_resume_unknown
            },
        )

    return listOf(
        size,
        mimeType.takeIf(String::isNotBlank),
        range,
    ).filterNotNull()
        .joinToString(" · ")
}
