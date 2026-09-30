package com.yagay.YNotify.data;

import android.app.Notification;

/** Classifies what a notification is; presentation surfaces are stored separately. */
public final class NotificationKindClassifier {
    private NotificationKindClassifier() {}

    public static void apply(EventRecord r) {
        r.notificationKind = classify(r);
    }

    public static String classify(EventRecord r) {
        if (Notification.CATEGORY_CALL.equals(r.category)) return "call";
        if (Notification.CATEGORY_ALARM.equals(r.category)) return "alarm";
        if (Notification.CATEGORY_TRANSPORT.equals(r.category) || containsMediaSession(r.rawExtras)) return "media";
        if (r.progressMax > 0 || r.progressIndeterminate) return "progress";
        if (r.foregroundService) return "foreground_service";
        if (Notification.CATEGORY_MESSAGE.equals(r.category) || r.conversation) return "message";
        if (Notification.CATEGORY_SYSTEM.equals(r.category)
                || Notification.CATEGORY_STATUS.equals(r.category)
                || Notification.CATEGORY_SERVICE.equals(r.category)) return "system";
        if (r.ongoing) return "ongoing";
        if (r.silent) return "silent";
        return "standard";
    }

    private static boolean containsMediaSession(String extrasJson) {
        return extrasJson != null && extrasJson.contains(Notification.EXTRA_MEDIA_SESSION);
    }
}
