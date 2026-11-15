package com.yagay.YNotify.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local YNotify popup preferences. Monitoring only reads opted-in accessibility/SystemUI events;
 * suppression is an independent, default-off setting, mirrored to LSPosed remote preferences.
 */
public final class PopupSettings {
    public static final String PREFS = "ynotify_popup_controls";
    public static final String KEY_CAPTURE = "capture_popup_events";
    public static final String KEY_HIDE_CLIPBOARD = "hide_clipboard_overlay";

    private PopupSettings() {}

    public static boolean captureEnabled(Context context) {
        return preferences(context).getBoolean(KEY_CAPTURE, true);
    }

    public static boolean hideClipboardEnabled(Context context) {
        return preferences(context).getBoolean(KEY_HIDE_CLIPBOARD, false);
    }

    public static void setCaptureEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_CAPTURE, enabled).apply();
    }

    public static void setHideClipboardEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_HIDE_CLIPBOARD, enabled).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
