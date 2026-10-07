package com.yagay.ysuite.feature.ynotify.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEvent
import com.yagay.ysuite.feature.ynotify.api.YNotifyEventType
import com.yagay.ysuite.feature.ynotify.api.YNotifyNotificationKind
import java.util.concurrent.ConcurrentHashMap

class YNotifyHookReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pkg = intent.getStringExtra("package").orEmpty().trim()
        val kind = intent.getStringExtra("kind").orEmpty().trim()
        val type = intent.getStringExtra("type").orEmpty().trim()
        val text = intent.getStringExtra("text").orEmpty().trim().take(16_384)
        val className = intent.getStringExtra("class").orEmpty().trim().take(512)
        val notificationKey = intent.getStringExtra("notification_key").orEmpty().trim().take(2_048)
        val nonce = intent.getStringExtra("nonce").orEmpty()
        val signature = intent.getStringExtra("signature").orEmpty()
        val time = intent.getLongExtra("time", 0L)
        val now = System.currentTimeMillis()
        if (pkg.isBlank() || pkg.length > 255 || !PACKAGE.matches(pkg)) return
        if (time <= 0L || kotlin.math.abs(now - time) > 60_000L || nonce.isBlank()) return
        val secret = YNotifyHookAuth.localSecret(context) ?: return
        if (!YNotifyHookAuth.verify(secret, signature, pkg, kind, type, text, className, notificationKey, time, nonce)) return
        if (RECENT.putIfAbsent(nonce, now) != null) return
        if (RECENT.size > 500) RECENT.entries.removeIf { now - it.value > 120_000L }
        val database = YNotifyDatabase(context)
        if (kind in setOf("heads_up", "bubble", "full_screen")) {
            if (notificationKey.isNotBlank()) database.markSurface(notificationKey, kind, time)
            return
        }
        val eventType = when (type.lowercase()) {
            "toast" -> YNotifyEventType.Toast
            "dialog" -> YNotifyEventType.Dialog
            "snackbar" -> YNotifyEventType.Snackbar
            "popup" -> YNotifyEventType.Popup
            "system_ui" -> YNotifyEventType.SystemUi
            else -> YNotifyEventType.OtherUi
        }
        if (
            text.isBlank() ||
            pkg == context.packageName ||
            YNotifyCapturePolicy.isPaused(context, pkg)
        ) return
        val label = runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
        val redacted =
            YNotifyCapturePolicy.isRedacted(
                context,
                pkg,
            )
        database.upsert(
            YNotifyEvent(
                id = 0L,
                eventKey = "xposed:" + pkg + ":" + eventType.name + ":" + time + ":" + text.hashCode(),
                eventType = eventType,
                source = "lsposed",
                packageName = pkg,
                appLabel = label,
                title = null,
                text = if (redacted) null else text,
                fullText = if (redacted) null else text,
                postedAt = time,
                updatedAt = now,
                removedAt = null,
                notificationKey = null,
                notificationKind = YNotifyNotificationKind.Unknown,
                headsUp = false,
                bubbleShown = false,
                fullScreenShown = false,
                ongoing = false,
                foregroundService = false,
                progress = 0,
                progressMax = 0,
                progressIndeterminate = false,
                className = className.ifBlank { null },
                classificationVersion = YNotifyDatabase.CLASSIFICATION_VERSION,
            ),
        )
    }
    companion object {
        const val ACTION = "com.yagay.ysuite.YNOTIFY_XPOSED_EVENT"
        private val PACKAGE = Regex("[A-Za-z0-9_.$]+")
        private val RECENT = ConcurrentHashMap<String, Long>()
    }
}
