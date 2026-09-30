package com.yagay.YNotify.collector;

import android.app.Notification;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.yagay.YNotify.data.CapturePolicy;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.util.AppInfoUtil;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable accessibility event consumer.
 *
 * Standalone YNotify owns its AccessibilityService and delegates here. YSuite owns one shared
 * AccessibilityService for the whole host and delegates the same event stream here, so YNotify
 * does not need a second service/grant inside the merged APK.
 */
public final class UiAccessibilityBridge {
    private static volatile boolean connected;
    private static final ConcurrentHashMap<String, Long> recent = new ConcurrentHashMap<>();

    private UiAccessibilityBridge() {}

    public static void onServiceConnected(Context context) {
        connected = true;
    }

    public static void onServiceDisconnected(Context context) {
        connected = false;
        recent.clear();
    }

    public static boolean isConnected() {
        return connected;
    }

    public static void onAccessibilityEvent(Context context, AccessibilityEvent event) {
        if (context == null || event == null || event.getPackageName() == null) return;
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        String pkg = event.getPackageName().toString();
        if (app.getPackageName().equals(pkg) || CapturePolicy.isIgnored(app, pkg)) return;

        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        String sourceClass = "";
        try {
            AccessibilityNodeInfo src = event.getSource();
            if (src != null && src.getClassName() != null) sourceClass = src.getClassName().toString();
        } catch (Throwable ignored) {}

        String text = join(event.getText());
        if ((text == null || text.isBlank()) && event.getContentDescription() != null) {
            text = event.getContentDescription().toString();
        }
        if (text == null || text.isBlank()) return;

        String type = classify(event, className, sourceClass);
        if (type == null) return;

        long now = System.currentTimeMillis();
        String dedupe = pkg + "|" + event.getEventType() + "|" + type + "|" + text;
        Long last = recent.put(dedupe, now);
        if (last != null && now - last < 300L) return;
        if (recent.size() > 300) recent.entrySet().removeIf(e -> now - e.getValue() > 5_000L);

        EventRecord r = new EventRecord();
        r.eventType = type;
        r.source = "accessibility";
        r.packageName = pkg;
        r.appLabel = AppInfoUtil.label(app, pkg);
        r.text = text;
        r.fullText = text;
        r.className = className;
        r.accessibilityEventType = event.getEventType();
        r.accessibilitySourceClass = sourceClass;
        r.accessibilityWindowId = event.getWindowId();
        r.accessibilityContentChangeTypes = event.getContentChangeTypes();
        r.postedAt = now;
        r.updatedAt = now;
        r.eventKey = "a11y:" + pkg + ":" + type + ":" + now + ":" + Integer.toHexString(text.hashCode());
        if (CapturePolicy.isRedacted(app, pkg)) CapturePolicy.redact(r);
        EventStore.save(app, r);
    }

    private static String classify(AccessibilityEvent event, String className, String sourceClass) {
        String c = (className + " " + sourceClass).toLowerCase();
        int t = event.getEventType();

        if (t == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (event.getParcelableData() instanceof Notification) return null;
            if (c.contains("toast") || c.contains("transientnotification")) return EventTypes.TOAST;
            if ("com.android.systemui".contentEquals(event.getPackageName())) return EventTypes.SYSTEM_UI;
            return null;
        }

        if (c.contains("snackbar")) return EventTypes.SNACKBAR;
        if (c.contains("toast") || c.contains("transientnotification")) return EventTypes.TOAST;
        if (c.contains("popup")) return EventTypes.POPUP;
        if (c.contains("dialog") || c.contains("alertdialog")) return EventTypes.DIALOG;
        if ("com.android.systemui".contentEquals(event.getPackageName())) return EventTypes.SYSTEM_UI;
        if (t == AccessibilityEvent.TYPE_ANNOUNCEMENT) return EventTypes.OTHER_UI;
        return null;
    }

    private static String join(List<CharSequence> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (CharSequence c : list) {
            if (c == null || c.toString().isBlank()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
