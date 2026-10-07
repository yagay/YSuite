package com.yagay.ysuite.feature.ynotify.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import java.util.concurrent.Executors

class YNotifyAccessibilityService :
    AccessibilityService() {
    private val executor =
        Executors.newSingleThreadExecutor()
    private lateinit var database:
        YNotifyDatabase

    override fun onCreate() {
        super.onCreate()
        database = YNotifyDatabase(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        YNotifyRuntimeState
            .accessibilityConnected = true
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?,
    ) {
        event ?: return
        val packageName =
            event.packageName?.toString()
                .orEmpty()
        if (
            packageName == packageName() ||
            YNotifyCapturePolicy.isPaused(this, packageName)
        ) return
        val className =
            event.className?.toString()
                .orEmpty()
        val text =
            buildString {
                event.text
                    .filterNotNull()
                    .map { it.toString().trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach {
                        if (isNotEmpty()) {
                            append(" · ")
                        }
                        append(it)
                    }
                event.contentDescription
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank() &&
                            !contains(it)
                    }
                    ?.let {
                        if (isNotEmpty()) {
                            append(" · ")
                        }
                        append(it)
                    }
            }
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val type =
            YNotifyDatabase.classifyUiType(
                current =
                    YNotifyEventType.OtherUi,
                source = "accessibility",
                packageName = packageName,
                className = className,
                notificationKey = null,
            )
        val label =
            runCatching {
                val info =
                    packageManager.getApplicationInfo(
                        packageName,
                        0,
                    )
                packageManager
                    .getApplicationLabel(info)
                    .toString()
            }.getOrDefault(packageName)
        val key =
            "a11y:" +
                packageName +
                ":" +
                event.eventType +
                ":" +
                event.windowId +
                ":" +
                text.hashCode() +
                ":" +
                now / 1000L
        val redacted =
            YNotifyCapturePolicy.isRedacted(
                this,
                packageName,
            )
        val record =
            YNotifyEvent(
                id = 0L,
                eventKey = key,
                eventType = type,
                source = "accessibility",
                packageName = packageName,
                appLabel = label,
                title = null,
                text = if (redacted) null else text,
                fullText = if (redacted) null else text,
                postedAt = now,
                updatedAt = now,
                removedAt = null,
                notificationKey = null,
                notificationKind =
                    YNotifyNotificationKind.Unknown,
                headsUp = false,
                bubbleShown = false,
                fullScreenShown = false,
                ongoing = false,
                foregroundService = false,
                progress = 0,
                progressMax = 0,
                progressIndeterminate = false,
                className = className,
                classificationVersion =
                    YNotifyDatabase
                        .CLASSIFICATION_VERSION,
            )
        executor.execute {
            runCatching {
                database.upsert(record)
                database.markPresentation(
                    packageName = packageName,
                    text = text,
                    type = type,
                    now = now,
                )
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        YNotifyRuntimeState
            .accessibilityConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        YNotifyRuntimeState
            .accessibilityConnected = false
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun packageName(): String =
        applicationContext.packageName
}
