package com.yagay.ysuite.feature.ydownload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.yagay.ysuite.productui.download.QdmDownloadList
import com.yagay.ysuite.productui.download.QdmDownloadRowModel
import com.yagay.ysuite.productui.download.QdmDownloadTab
import com.yagay.ysuite.productui.download.QdmDownloadWorkspace
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun YDownloadFeatureScreen(
    environment: YDownloadEnvironment,
    logger: YSuiteLogger,
) {
    val context =
        LocalContext.current.applicationContext
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

    val tabs =
        YDownloadTab.entries.map { tab ->
            QdmDownloadTab(
                id = tab.name,
                label = tab.label(),
            )
        }

    val filtered =
        state.items.forTab(
            tab = state.selectedTab,
            query = state.searchQuery,
        )

    QdmDownloadWorkspace(
        title = stringResource(R.string.ydownload_title),
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
            stringResource(R.string.ydownload_search),
        addContentDescription =
            stringResource(R.string.ydownload_add),
        closeSearchContentDescription =
            stringResource(
                R.string.ydownload_close_search,
            ),
        onSearchQueryChange =
            model::setSearchQuery,
        onToggleSearch = model::toggleSearch,
        onAdd = model::showAddDialog,
        navigationIcon = {
            YSuiteHostNavigationButton()
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
                ),
            onPause = model::pause,
            onResume = model::resume,
            onCancel = model::cancel,
            onOpen = model::open,
            onRetry = model::retry,
            onRemove = model::remove,
        )
    }

    if (state.addDialogVisible) {
        val draft = state.addDraft
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
                            R.string.ydownload_start,
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
            onFetch = model::fetchMetadata,
            onAddQueue = model::addToQueue,
            onStart = model::addAndStart,
            onDismiss = model::dismissAddDialog,
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
                state == YDownloadState.Pending,
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
