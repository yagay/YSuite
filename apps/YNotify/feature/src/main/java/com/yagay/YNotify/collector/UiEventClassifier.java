package com.yagay.YNotify.collector;

import android.view.accessibility.AccessibilityEvent;
import com.yagay.YNotify.data.EventTypes;
import java.util.Locale;

/**
 * Accessibility is a fallback for explicit transient UI events, not a screen
 * text recorder. A view's class name alone never creates an event.
 */
final class UiEventClassifier {
    static String classify(int eventType, String eventClass,
                           String sourceClass, boolean hasNotificationPayload) {
        String c = normalize(eventClass);
        String source = normalize(sourceClass);
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (hasNotificationPayload) return null;
            return toast(c) || toast(source) ? EventTypes.TOAST : null;
        }
        // Ordinary content changes, scrolling, clicks and announcements are not popups.
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return null;
        }
        if (c.contains("snackbar") || c.contains("basetransientbottombar"))
            return EventTypes.SNACKBAR;
        if (toast(c)) return EventTypes.TOAST;
        if (c.contains("alertdialog") || c.contains("dialog")) return EventTypes.DIALOG;
        if (c.contains("popupwindow") || c.contains("listpopupwindow")
                || c.contains("popup")) return EventTypes.POPUP;
        // Source nodes may identify the root type, but only on a window transition.
        if (source.contains("alertdialog") || source.contains("dialog")) return EventTypes.DIALOG;
        if (source.contains("popupwindow") || source.contains("listpopupwindow"))
            return EventTypes.POPUP;
        return null;
    }

    private static boolean toast(String c) {
        return c.contains("toast") || c.contains("transientnotification");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
    private UiEventClassifier(){}
}
