package com.yagay.YNotify.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.yagay.YNotify.util.DiagLog;

public final class ListenerStateStore {
    private static final String PREFS = "listener_state";
    private ListenerStateStore() {}

    public static void markProcessStarted(Context context) {
        prefs(context).edit()
                .putBoolean("connected", false)
                .putLong("process_started", System.currentTimeMillis())
                .apply();
        DiagLog.i(context, "ListenerState", "process started; stale connected state cleared");
    }

    public static long markConnected(Context context) {
        SharedPreferences sp = prefs(context);
        long previousDisconnect = sp.getLong("last_disconnected", 0L);
        sp.edit()
                .putBoolean("connected", true)
                .putLong("last_connected", System.currentTimeMillis())
                .remove("last_error")
                .apply();
        DiagLog.i(context, "ListenerState", "notification listener connected");
        return previousDisconnect;
    }

    public static void markDisconnected(Context context) {
        prefs(context).edit()
                .putBoolean("connected", false)
                .putLong("last_disconnected", System.currentTimeMillis())
                .apply();
        DiagLog.w(context, "ListenerState", "notification listener disconnected");
    }

    public static void markEvent(Context context) {
        prefs(context).edit().putLong("last_event", System.currentTimeMillis()).apply();
    }

    public static void markNotificationReceived(Context context, String pkg, String key) {
        prefs(context).edit()
                .putLong("last_notification_received", System.currentTimeMillis())
                .putString("last_notification_package", pkg == null ? "" : pkg)
                .putString("last_notification_key", key == null ? "" : key)
                .apply();
        DiagLog.i(context, "NotificationCapture", "received pkg=" + (pkg == null ? "" : pkg));
    }

    public static void markNotificationSaved(Context context, String pkg, String key) {
        prefs(context).edit()
                .putLong("last_notification_saved", System.currentTimeMillis())
                .putString("last_saved_package", pkg == null ? "" : pkg)
                .putString("last_saved_key", key == null ? "" : key)
                .remove("last_error")
                .apply();
        DiagLog.i(context, "NotificationCapture", "saved pkg=" + (pkg == null ? "" : pkg));
    }

    public static void markError(Context context, String stage, String pkg, Throwable error) {
        String message = error == null ? "unknown" : error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage());
        if (message.length() > 800) message = message.substring(0, 800);
        prefs(context).edit()
                .putLong("last_error_time", System.currentTimeMillis())
                .putString("last_error", stage + " · " + (pkg == null ? "" : pkg) + " · " + message)
                .apply();
        DiagLog.e(context, "ListenerState", stage + " pkg=" + (pkg == null ? "" : pkg), error);
    }

    public static boolean isConnected(Context context) {
        return prefs(context).getBoolean("connected", false);
    }

    public static long lastConnected(Context context) { return prefs(context).getLong("last_connected", 0L); }
    public static long lastDisconnected(Context context) { return prefs(context).getLong("last_disconnected", 0L); }
    public static long lastEvent(Context context) { return prefs(context).getLong("last_event", 0L); }
    public static long lastNotificationReceived(Context context) { return prefs(context).getLong("last_notification_received", 0L); }
    public static long lastNotificationSaved(Context context) { return prefs(context).getLong("last_notification_saved", 0L); }
    public static String lastNotificationPackage(Context context) { return prefs(context).getString("last_notification_package", ""); }
    public static String lastError(Context context) { return prefs(context).getString("last_error", ""); }
    public static long lastErrorTime(Context context) { return prefs(context).getLong("last_error_time", 0L); }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
