package com.yagay.YNotify.collector;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.yagay.YNotify.data.CapturePolicy;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.PopupSettings;
import com.yagay.YNotify.util.AppInfoUtil;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UI popup observer shared by standalone YNotify and YSuite.
 *
 * Accessibility cannot intercept arbitrary application drawing. Only event/window evidence
 * is recorded; security dialogs and editable/password fields are never inspected.
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

    /** Compatibility entry for lightweight hosts that only pass a Context. */
    public static void onAccessibilityEvent(Context context, AccessibilityEvent event) {
        capture(context, null, event);
    }

    /** Preferred entry: a live service allows resolving additional application windows. */
    public static void onAccessibilityEvent(AccessibilityService service, AccessibilityEvent event) {
        capture(service, service, event);
    }

    private static void capture(Context context, AccessibilityService service, AccessibilityEvent event) {
        if (context == null || event == null) return;
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (!PopupSettings.captureEnabled(app)) return;

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                && (event.getWindowChanges() & AccessibilityEvent.WINDOWS_CHANGE_ADDED) == 0) return;

        AccessibilityNodeInfo source = null;
        AccessibilityWindowInfo window = null;
        try {
            source = event.getSource();
            if (service != null && event.getWindowId() >= 0) {
                List<AccessibilityWindowInfo> windows = service.getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo candidate : windows) {
                        if (candidate.getId() == event.getWindowId()) {
                            window = candidate;
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (pkg.isEmpty() && source != null && source.getPackageName() != null)
            pkg = source.getPackageName().toString();
        if (pkg.isEmpty() && window != null) {
            try {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && root.getPackageName() != null) pkg = root.getPackageName().toString();
            } catch (Throwable ignored) {}
        }
        if (pkg.isEmpty() || pkg.equals(app.getPackageName())
                || CapturePolicy.isIgnored(app, pkg)) return;

        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        String sourceClass = source != null && source.getClassName() != null
                ? source.getClassName().toString() : "";
        // Never record the content of password or editable input fields.
        try {
            if (source != null && (source.isPassword() || source.isEditable()
                    || sourceClass.contains("EditText"))) return;
        } catch (Throwable ignored) {}
        String title = "";
        try {
            if (window != null && window.getTitle() != null) title = window.getTitle().toString();
        } catch (Throwable ignored) {}

        String identity = (className + " " + sourceClass + " " + title).toLowerCase(Locale.ROOT);
        if (protectedWindow(pkg, identity)) return;

        String type = classify(event, pkg, identity, window, service);
        if (type == null) return;

        // A clipboard preview must never copy clipboard content into the notification database.
        boolean clipboard = "com.android.systemui".equals(pkg) && identity.contains("clipboard");
        String text = clipboard ? EventTypes.CLIPBOARD_SHOWN_MARKER : join(event.getText());
        if (!clipboard && (text == null || text.isBlank()) && event.getContentDescription() != null)
            text = event.getContentDescription().toString();
        if (!clipboard && (text == null || text.isBlank()) && source != null)
            text = safeWindowText(source);
        if (!clipboard && (text == null || text.isBlank()) && window != null
                && (EventTypes.DIALOG.equals(type) || EventTypes.POPUP.equals(type))) {
            try { text = safeWindowText(window.getRoot()); } catch (Throwable ignored) {}
        }
        if (text == null || text.isBlank()) text = EventTypes.EMPTY_UI_MARKER;
        if (text.length() > 2048) text = text.substring(0, 2048);

        long now = System.currentTimeMillis();
        String dedupe = pkg + "|" + type + "|" + event.getWindowId() + "|" + text;
        Long last = recent.put(dedupe, now);
        if (last != null && now - last < 1500L) return;
        if (recent.size() > 300) recent.entrySet().removeIf(e -> now - e.getValue() > 12_000L);

        EventRecord record = new EventRecord();
        record.eventType = type;
        record.source = "accessibility";
        record.packageName = pkg;
        record.appLabel = AppInfoUtil.label(app, pkg);
        record.title = title.isBlank() ? null : title;
        record.text = text;
        record.fullText = text;
        record.className = className;
        record.accessibilityEventType = eventType;
        record.accessibilitySourceClass = sourceClass;
        record.accessibilityWindowId = event.getWindowId();
        record.accessibilityContentChangeTypes = event.getContentChangeTypes();
        record.postedAt = now;
        record.updatedAt = now;
        record.eventKey = "a11y:" + pkg + ":" + type + ":" + now + ":" + Integer.toHexString(dedupe.hashCode());
        if (CapturePolicy.isRedacted(app, pkg)) CapturePolicy.redact(record);
        EventStore.save(app, record);
    }

    private static String classify(AccessibilityEvent event, String pkg, String identity,
                                   AccessibilityWindowInfo window, AccessibilityService service) {
        int t = event.getEventType();
        if (t == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            try {
                if (event.getParcelableData() instanceof Notification) return null;
            } catch (Throwable ignored) {}
            // A non-Notification TYPE_NOTIFICATION_STATE_CHANGED is most commonly a Toast.
            return identity.contains("toast") || identity.contains("transientnotification")
                    || !event.getText().isEmpty() ? EventTypes.TOAST : null;
        }
        if (identity.contains("snackbar")) return EventTypes.SNACKBAR;
        if (identity.contains("toast") || identity.contains("transientnotification")) return EventTypes.TOAST;
        if (identity.contains("popup") || identity.contains("dropdown")
                || identity.contains("contextmenu")) return EventTypes.POPUP;
        if (identity.contains("dialog") || identity.contains("alertdialog")) return EventTypes.DIALOG;
        if ("com.android.systemui".equals(pkg) && (
                identity.contains("clipboard") || identity.contains("screenshot")
                        || identity.contains("volume") || identity.contains("charging")
                        || identity.contains("powerui"))) return EventTypes.SYSTEM_UI;
        if (t == AccessibilityEvent.TYPE_ANNOUNCEMENT) return EventTypes.OTHER_UI;

        // Some apps use a generic FrameLayout for a dialog. A newly added focused, additional
        // application window is useful evidence; this is a popup candidate, not a security dialog.
        if (t == AccessibilityEvent.TYPE_WINDOWS_CHANGED && service != null && window != null
                && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
            int count = 0;
            try {
                for (AccessibilityWindowInfo candidate : service.getWindows()) {
                    if (candidate.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                    AccessibilityNodeInfo root = candidate.getRoot();
                    if (root != null && root.getPackageName() != null
                            && pkg.contentEquals(root.getPackageName())) count++;
                    if (count >= 2) return EventTypes.POPUP;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean protectedWindow(String pkg, String identity) {
        String name = pkg.toLowerCase(Locale.ROOT);
        return name.contains("permissioncontroller") || name.contains("packageinstaller")
                || identity.contains("biometric") || identity.contains("keyguard")
                || identity.contains("password") || identity.contains("credential")
                || identity.contains("emergency") || identity.contains("fingerprint");
    }

    private static String safeWindowText(AccessibilityNodeInfo root) {
        if (root == null) return null;
        LinkedHashSet<String> words = new LinkedHashSet<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int inspected = 0;
        while (!queue.isEmpty() && inspected++ < 60 && words.size() < 20) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node == null) continue;
            try {
                String cls = node.getClassName() == null ? "" : node.getClassName().toString();
                if (node.isPassword() || node.isEditable() || cls.contains("EditText")) continue;
                CharSequence t = node.getText();
                if (t == null || t.length() == 0) t = node.getContentDescription();
                if (t != null && t.length() > 0 && t.length() < 512) words.add(t.toString());
                int size = Math.min(node.getChildCount(), 16);
                for (int i = 0; i < size; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.addLast(child);
                }
            } catch (Throwable ignored) {}
        }
        return words.isEmpty() ? null : String.join("\n", words);
    }

    private static String join(List<CharSequence> list) {
        if (list == null || list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (CharSequence item : list) {
            if (item == null || item.toString().isBlank()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(item);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
