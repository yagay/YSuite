package com.yagay.ysuite.feature.ynotify

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyRuntimeStatus
import com.yagay.ysuite.feature.ynotify.runtime.YNotifyRuntimeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YNotifyViewMode {
    History,
    Apps,
}

enum class YNotifyKindFilter {
    All,
    FullScreen,
    Bubble,
    Call,
    Alarm,
    Media,
    Progress,
    ForegroundService,
    Message,
    System,
    Ongoing,
    Silent,
    Standard,
    Unknown,
}

data class YNotifyAppSummary(
    val packageName: String,
    val label: String,
    val count: Int,
    val latestAt: Long,
)

enum class YNotifyTypeFilter {
    All,
    Notifications,
    Toast,
    Dialog,
    Snackbar,
    Popup,
    SystemUi,
    OtherUi,
}

data class YNotifyUiState(
    val events: List<YNotifyEvent> = emptyList(),
    val searchResults: List<YNotifyEvent>? = null,
    val appSummaries: List<YNotifyAppSummary> = emptyList(),
    val query: String = "",
    val viewMode: YNotifyViewMode =
        YNotifyViewMode.History,
    val typeFilter: YNotifyTypeFilter =
        YNotifyTypeFilter.All,
    val kindFilter: YNotifyKindFilter =
        YNotifyKindFilter.All,
    val selectedPackage: String? = null,
    val selectedEventId: Long? = null,
    val runtimeStatus:
        YNotifyRuntimeStatus =
        YNotifyRuntimeStatus(
            notificationListenerConnected = false,
            accessibilityConnected = false,
            storedEventCount = 0,
        ),
    val exportUri: String? = null,
    val retentionDays: Int = 30,
    val policyVersion: Int = 0,
)

class YNotifyViewModel(
    private val context: Context,
) : ViewModel() {
    private val store =
        YNotifyRuntimeStore(context)
    private val mutableState =
        MutableStateFlow(YNotifyUiState())
    val state: StateFlow<YNotifyUiState> =
        mutableState.asStateFlow()

    private var searchJob: Job? = null

    init {
        mutableState.value =
            mutableState.value.copy(
                retentionDays =
                    store.retentionDays(),
            )
        viewModelScope.launch {
            store.observeEvents().collect {
                    events ->
                val aggregates =
                    withContext(
                        Dispatchers.IO,
                    ) {
                        store.appAggregates()
                    }.map { aggregate ->
                        YNotifyAppSummary(
                            packageName =
                                aggregate.packageName,
                            label =
                                aggregate.appLabel,
                            count =
                                aggregate.count,
                            latestAt =
                                aggregate.latestAt,
                        )
                    }
                val current =
                    mutableState.value
                mutableState.value =
                    current.copy(
                        events = events,
                        appSummaries =
                            aggregates,
                        runtimeStatus =
                            store.status(),
                    )
                if (
                    current.query
                        .isNotBlank()
                ) {
                    scheduleSearch(
                        current.query,
                    )
                }
            }
        }
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(
                query = value,
                searchResults =
                    if (value.isBlank()) {
                        null
                    } else {
                        mutableState.value
                            .searchResults
                    },
            )
        scheduleSearch(value)
    }

    private fun scheduleSearch(
        value: String,
    ) {
        searchJob?.cancel()
        val query = value.trim()
        if (query.isBlank()) {
            mutableState.value =
                mutableState.value.copy(
                    searchResults = null,
                )
            return
        }
        searchJob =
            viewModelScope.launch {
                delay(250L)
                val result =
                    withContext(
                        Dispatchers.IO,
                    ) {
                        store.search(
                            query,
                        )
                    }
                if (
                    mutableState.value
                        .query
                        .trim() ==
                    query
                ) {
                    mutableState.value =
                        mutableState.value.copy(
                            searchResults =
                                result,
                        )
                }
            }
    }

    fun setViewMode(
        value: YNotifyViewMode,
    ) {
        mutableState.value =
            mutableState.value.copy(
                viewMode = value,
                selectedEventId = null,
            )
    }

    fun setTypeFilter(
        value: YNotifyTypeFilter,
    ) {
        mutableState.value =
            mutableState.value.copy(
                typeFilter = value,
            )
    }

    fun setKindFilter(
        value: YNotifyKindFilter,
    ) {
        mutableState.value =
            mutableState.value.copy(
                kindFilter = value,
            )
    }

    fun selectPackage(
        packageName: String?,
    ) {
        mutableState.value =
            mutableState.value.copy(
                selectedPackage =
                    packageName,
                viewMode =
                    YNotifyViewMode.History,
                selectedEventId = null,
            )
    }

    fun appSummaries():
        List<YNotifyAppSummary> {
        val state = mutableState.value
        val needle =
            state.query.trim()
        return state.appSummaries
            .asSequence()
            .filter {
                needle.isBlank() ||
                    it.label.contains(
                        needle,
                        true,
                    ) ||
                    it.packageName
                        .contains(
                            needle,
                            true,
                        )
            }
            .sortedWith(
                compareByDescending<
                    YNotifyAppSummary
                    > {
                    it.latestAt
                }.thenBy {
                    it.label.lowercase()
                },
            )
            .toList()
    }

    fun visibleEvents(): List<YNotifyEvent> {
        val state = mutableState.value
        val needle = state.query.trim()
        val source =
            if (needle.isBlank()) {
                state.events
            } else {
                state.searchResults
                    ?: emptyList()
            }
        return source.filter { event ->
            val typeMatch =
                when (state.typeFilter) {
                    YNotifyTypeFilter.All -> true
                    YNotifyTypeFilter.Notifications ->
                        event.eventType ==
                            YNotifyEventType.Notification
                    YNotifyTypeFilter.Toast ->
                        event.eventType ==
                            YNotifyEventType.Toast
                    YNotifyTypeFilter.Dialog ->
                        event.eventType ==
                            YNotifyEventType.Dialog
                    YNotifyTypeFilter.Snackbar ->
                        event.eventType ==
                            YNotifyEventType.Snackbar
                    YNotifyTypeFilter.Popup ->
                        event.eventType ==
                            YNotifyEventType.Popup
                    YNotifyTypeFilter.SystemUi ->
                        event.eventType ==
                            YNotifyEventType.SystemUi
                    YNotifyTypeFilter.OtherUi ->
                        event.eventType ==
                            YNotifyEventType.OtherUi
                }
            val packageMatch =
                state.selectedPackage == null ||
                    event.packageName ==
                    state.selectedPackage
            val kindMatch =
                when (
                    state.kindFilter
                ) {
                    YNotifyKindFilter.All ->
                        true
                    else ->
                        event.eventType ==
                            YNotifyEventType.Notification &&
                            event.notificationKind.name ==
                            state.kindFilter.name
                }
            val textMatch =
                needle.isBlank() ||
                    event.appLabel.contains(
                        needle,
                        ignoreCase = true,
                    ) ||
                    event.packageName.contains(
                        needle,
                        ignoreCase = true,
                    ) ||
                    event.title
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.fullText
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.subText
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.summaryText
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.channelName
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.messagesJson
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.actionsJson
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true ||
                    event.rawExtras
                        ?.contains(
                            needle,
                            ignoreCase = true,
                        ) == true
            typeMatch &&
                packageMatch &&
                kindMatch &&
                textMatch
        }
    }

    fun select(event: YNotifyEvent?) {
        mutableState.value =
            mutableState.value.copy(
                selectedEventId = event?.id,
            )
    }

    fun selectedEvent(): YNotifyEvent? {
        val state =
            mutableState.value
        return (
            state.searchResults
                ?: state.events
            ).firstOrNull {
                it.id ==
                    state.selectedEventId
            }
    }

    fun isPaused(packageName: String): Boolean =
        store.isPaused(packageName)

    fun isRedacted(packageName: String): Boolean =
        store.isRedacted(packageName)

    fun togglePaused(packageName: String) {
        store.setPaused(
            packageName,
            !store.isPaused(packageName),
        )
        mutableState.value =
            mutableState.value.copy(
                policyVersion =
                    mutableState.value.policyVersion + 1,
            )
    }

    fun toggleRedacted(packageName: String) {
        store.setRedacted(
            packageName,
            !store.isRedacted(packageName),
        )
        mutableState.value =
            mutableState.value.copy(
                policyVersion =
                    mutableState.value.policyVersion + 1,
            )
    }

    fun setRetentionDays(days: Int) {
        store.setRetentionDays(days)
        mutableState.value =
            mutableState.value.copy(
                retentionDays = days,
            )
    }

    fun clear() {
        store.clear()
    }

    fun reclassify() {
        store.reclassify()
    }

    fun export() {
        viewModelScope.launch {
            val uri =
                withContext(Dispatchers.IO) {
                    store.export()
                }
            mutableState.value =
                mutableState.value.copy(
                    exportUri = uri,
                )
        }
    }

    fun openNotificationAccess() {
        openSettings(
            Settings
                .ACTION_NOTIFICATION_LISTENER_SETTINGS,
        )
    }

    fun openAccessibility() {
        openSettings(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
        )
    }

    private fun openSettings(action: String) {
        context.startActivity(
            Intent(action).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            },
        )
    }

    class Factory(
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
        ): T =
            YNotifyViewModel(
                context.applicationContext,
            ) as T
    }
}
