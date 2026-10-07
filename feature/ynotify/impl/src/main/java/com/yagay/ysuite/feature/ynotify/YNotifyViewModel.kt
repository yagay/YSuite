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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val query: String = "",
    val typeFilter: YNotifyTypeFilter =
        YNotifyTypeFilter.All,
    val selectedEventId: Long? = null,
    val runtimeStatus:
        YNotifyRuntimeStatus =
        YNotifyRuntimeStatus(
            notificationListenerConnected = false,
            accessibilityConnected = false,
            storedEventCount = 0,
        ),
    val exportUri: String? = null,
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

    init {
        viewModelScope.launch {
            store.observeEvents().collect {
                mutableState.value =
                    mutableState.value.copy(
                        events = it,
                        runtimeStatus =
                            store.status(),
                    )
            }
        }
    }

    fun setQuery(value: String) {
        mutableState.value =
            mutableState.value.copy(query = value)
    }

    fun setTypeFilter(
        value: YNotifyTypeFilter,
    ) {
        mutableState.value =
            mutableState.value.copy(
                typeFilter = value,
            )
    }

    fun visibleEvents(): List<YNotifyEvent> {
        val state = mutableState.value
        val needle = state.query.trim()
        return state.events.filter { event ->
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
                        ) == true
            typeMatch && textMatch
        }
    }

    fun select(event: YNotifyEvent?) {
        mutableState.value =
            mutableState.value.copy(
                selectedEventId = event?.id,
            )
    }

    fun selectedEvent(): YNotifyEvent? =
        mutableState.value.events
            .firstOrNull {
                it.id ==
                    mutableState.value
                        .selectedEventId
            }

    fun clear() {
        store.clear()
    }

    fun reclassify() {
        store.reclassify()
    }

    fun export() {
        val events = mutableState.value.events
        viewModelScope.launch {
            val uri =
                withContext(Dispatchers.IO) {
                    store.export(events)
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
