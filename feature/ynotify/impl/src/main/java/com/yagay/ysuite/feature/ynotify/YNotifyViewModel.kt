package com.yagay.ysuite.feature.ynotify

import android.content.Context
import android.content.ComponentName
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import com.yagay.ysuite.feature.ynotify.runtime.YNotifyNotificationListenerService
import com.yagay.ysuite.feature.ynotify.runtime.YNotifyAccessibilityService
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyRuntimeStatus
import com.yagay.ysuite.feature.ynotify.runtime.YNotifyRuntimeStore
import com.yagay.ysuite.feature.ynotify.runtime.YNotifyRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class YNotifyViewMode {
    History,
    Apps,
    Settings,
}

/** Matches the original YNotify timeline: visual presentation is not a type. */
enum class YNotifyTimelineFilter {
    All, Notifications, HeadsUp, Bubble, FullScreen, Toast, Dialog, Popup, Snackbar,
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
    val timelineFilter: YNotifyTimelineFilter = YNotifyTimelineFilter.All,
    val notificationAuthorized: Boolean = false,
    val accessibilityAuthorized: Boolean = false,
    val captureMessage: String? = null,
    val loadError: String? = null,
    val typeFilter: YNotifyTypeFilter =
        YNotifyTypeFilter.All,
    val kindFilter: YNotifyKindFilter =
        YNotifyKindFilter.All,
    val selectedPackage: String? = null,
    val selectedEventId: Long? = null,
    val revisions: List<YNotifyRevision> = emptyList(),
    val runtimeStatus:
        YNotifyRuntimeStatus =
        YNotifyRuntimeStatus(
            notificationListenerConnected = false,
            accessibilityConnected = false,
            storedEventCount = 0,
        ),
    val exportUri: String? = null,
    val retentionDays: Int = 90,
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
    private val historyPageLimit = MutableStateFlow(1_000)
    private var lastAutomaticRebindAt = 0L

    init {
        // The capture services may connect after the screen first opens.
        // Observing the database alone never updates a newly granted permission.
        viewModelScope.launch {
            while (isActive) {
                try {
                    refreshCaptureState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.value = mutableState.value.copy(
                        captureMessage = "capture_status_failed",
                    )
                }
                delay(4_000L)
            }
        }
        mutableState.value =
            mutableState.value.copy(
                retentionDays =
                    store.retentionDays(),
            )
        viewModelScope.launch {
            store.observeEvents(historyPageLimit)
                .catch { error ->
                    mutableState.value = mutableState.value.copy(
                        loadError = error.message ?: "history_load_failed",
                    )
                }
                .collect { events ->
                val (rawAggregates, runtimeStatus) =
                    withContext(Dispatchers.IO) {
                        store.appAggregates() to store.status()
                    }
                val aggregates =
                    rawAggregates.map { aggregate ->
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
                        runtimeStatus = runtimeStatus,
                        loadError = null,
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

    private suspend fun refreshCaptureState() {
        val snapshot = withContext(Dispatchers.IO) {
            val listener = ComponentName(
                context, YNotifyNotificationListenerService::class.java,
            )
            val accessibility = ComponentName(
                context, YNotifyAccessibilityService::class.java,
            )
            val notificationPermission = runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(listener)
            }.getOrDefault(false)
            val enabledServices = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ).orEmpty()
            }.getOrDefault("")
            val accessibilityPermission = enabledServices.split(':').any {
                ComponentName.unflattenFromString(it) == accessibility
            }
            Triple(store.status(), notificationPermission, accessibilityPermission)
        }
        mutableState.value = mutableState.value.copy(
            runtimeStatus = snapshot.first,
            notificationAuthorized = snapshot.second,
            accessibilityAuthorized = snapshot.third,
        )
        val now = android.os.SystemClock.elapsedRealtime()
        if (snapshot.second && !snapshot.first.notificationListenerConnected &&
            now - lastAutomaticRebindAt > 60_000L
        ) {
            lastAutomaticRebindAt = now
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(
                        context, YNotifyNotificationListenerService::class.java,
                    ),
                )
            }
        }
    }

    fun requestReconnect() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    NotificationListenerService.requestRebind(
                        ComponentName(
                            context, YNotifyNotificationListenerService::class.java,
                        ),
                    )
                }
            }
            mutableState.value = mutableState.value.copy(
                captureMessage = if (result.isSuccess) "rebind_requested"
                    else "rebind_failed",
            )
            refreshCaptureState()
        }
    }

    fun setTimelineFilter(value: YNotifyTimelineFilter) {
        mutableState.value = mutableState.value.copy(
            timelineFilter = value,
            typeFilter = YNotifyTypeFilter.All,
            kindFilter = YNotifyKindFilter.All,
        )
    }

    fun loadOlder() {
        historyPageLimit.value =
            (historyPageLimit.value + 1_000).coerceAtMost(10_000)
    }

    fun canLoadOlder(): Boolean {
        val current = mutableState.value
        return current.events.size < current.runtimeStatus.storedEventCount &&
            historyPageLimit.value < 10_000
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(
                query = value,
                // Results belong to the previous query; invalidating them
                // avoids a transient empty or incorrect notification list.
                searchResults = null,
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
                timelineFilter = YNotifyTimelineFilter.All,
            )
    }

    fun setKindFilter(
        value: YNotifyKindFilter,
    ) {
        mutableState.value =
            mutableState.value.copy(
                kindFilter = value,
                timelineFilter = YNotifyTimelineFilter.All,
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
        // Search is asynchronous. Retain the locally loaded history until
        // the search results arrive, then apply the same filters below.
        val source =
            if (needle.isBlank()) {
                state.events
            } else {
                state.searchResults
                    ?: state.events
            }
        return source.filter { event ->
            val timelineMatch = when (state.timelineFilter) {
                YNotifyTimelineFilter.All -> true
                YNotifyTimelineFilter.Notifications ->
                    event.eventType == YNotifyEventType.Notification
                YNotifyTimelineFilter.HeadsUp ->
                    event.eventType == YNotifyEventType.Notification && event.headsUp
                YNotifyTimelineFilter.Bubble ->
                    event.eventType == YNotifyEventType.Notification && event.bubbleShown
                YNotifyTimelineFilter.FullScreen ->
                    event.eventType == YNotifyEventType.Notification && event.fullScreenShown
                YNotifyTimelineFilter.Toast -> event.eventType == YNotifyEventType.Toast
                YNotifyTimelineFilter.Dialog -> event.eventType == YNotifyEventType.Dialog
                YNotifyTimelineFilter.Popup -> event.eventType == YNotifyEventType.Popup
                YNotifyTimelineFilter.Snackbar -> event.eventType == YNotifyEventType.Snackbar
            }
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
            timelineMatch && typeMatch &&
                packageMatch &&
                kindMatch &&
                textMatch
        }
    }

    fun select(event: YNotifyEvent?) {
        mutableState.value = mutableState.value.copy(
            selectedEventId = event?.id,
            revisions = emptyList(),
        )
        if (event?.eventType == YNotifyEventType.Notification) {
            viewModelScope.launch {
                val revisions = withContext(Dispatchers.IO) {
                    store.revisions(event.eventKey)
                }
                if (mutableState.value.selectedEventId == event.id) {
                    mutableState.value = mutableState.value.copy(
                        revisions = revisions,
                    )
                }
            }
        }
    }

    fun selectedEvent(): YNotifyEvent? {
        val state =
            mutableState.value
        return state.events.firstOrNull {
            it.id == state.selectedEventId
        } ?: state.searchResults?.firstOrNull {
            it.id == state.selectedEventId
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.setRetentionDays(days)
            }
            mutableState.value =
                mutableState.value.copy(
                    retentionDays = days,
                )
        }
    }

    fun clearPackage(packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.clearPackage(packageName)
            }
            mutableState.value = mutableState.value.copy(
                selectedPackage = null,
                selectedEventId = null,
                viewMode = YNotifyViewMode.Apps,
            )
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.clear()
            }
        }
    }

    fun setManualClassification(
        event: YNotifyEvent,
        type: YNotifyEventType,
        headsUp: Boolean = event.headsUp,
        bubble: Boolean = event.bubbleShown,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.setManualClassification(event.id, type, headsUp, bubble)
            }
        }
    }

    fun resetManualClassification(event: YNotifyEvent) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.clearManualClassification(event.id)
            }
        }
    }

    fun reclassify() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.reclassify()
            }
        }
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
        val component = ComponentName(
            context, YNotifyNotificationListenerService::class.java,
        )
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString(),
            )
        if (!openSettingsSafely(intent)) {
            openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    fun openAccessibility() {
        val component = ComponentName(context, YNotifyAccessibilityService::class.java)
        val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        if (!openSettingsSafely(intent)) {
            openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }

    private fun openSettingsSafely(intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess

    private fun openSettings(action: String) {
        if (!openSettingsSafely(Intent(action))) {
            mutableState.value = mutableState.value.copy(
                captureMessage = "settings_unavailable",
            )
        }
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
