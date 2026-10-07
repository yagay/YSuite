package com.yagay.ysuite.feature.ynotify.api

enum class YNotifyEventType {
    Notification,
    Toast,
    Dialog,
    Snackbar,
    Popup,
    SystemUi,
    OtherUi,
}

enum class YNotifyNotificationKind {
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

data class YNotifyEvent(
    val id: Long,
    val eventKey: String,
    val eventType: YNotifyEventType,
    val source: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val fullText: String?,
    val postedAt: Long,
    val updatedAt: Long,
    val removedAt: Long?,
    val notificationKey: String?,
    val notificationKind: YNotifyNotificationKind,
    val headsUp: Boolean,
    val bubbleShown: Boolean,
    val fullScreenShown: Boolean,
    val ongoing: Boolean,
    val foregroundService: Boolean,
    val progress: Int,
    val progressMax: Int,
    val progressIndeterminate: Boolean,
    val className: String?,
    val classificationVersion: Int,
)

data class YNotifyRuntimeStatus(
    val notificationListenerConnected: Boolean,
    val accessibilityConnected: Boolean,
    val storedEventCount: Int,
)
