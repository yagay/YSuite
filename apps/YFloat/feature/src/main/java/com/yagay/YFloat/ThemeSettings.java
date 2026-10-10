package com.yagay.YFloat;

import android.app.Activity;
import android.content.Context;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.yagay.yui.YAppearanceSettings;

/**
 * Backward-compatible facade for existing YFloat callers. The setting is owned by YUI.
 * Do not add feature-local theme preferences here.
 */
final class ThemeSettings {
    static final int MODE_SYSTEM = YAppearanceSettings.MODE_SYSTEM;
    static final int MODE_LIGHT = YAppearanceSettings.MODE_LIGHT;
    static final int MODE_DARK = YAppearanceSettings.MODE_DARK;

    static int mode(Context context) {
        return context == null ? MODE_SYSTEM : YAppearanceSettings.mode(context);
    }

    static boolean setMode(Context context, int value) {
        return context != null && YAppearanceSettings.setMode(context, value);
    }

    static void applySavedMode(Context context) {
        if (context != null) YAppearanceSettings.applySavedMode(context);
    }

    static boolean isDark(Context context) {
        return context != null && YAppearanceSettings.isDark(context);
    }

    static void applySystemBars(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        try {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                    activity.getWindow(), activity.getWindow().getDecorView());
            boolean light = !isDark(activity);
            controller.setAppearanceLightStatusBars(light);
            controller.setAppearanceLightNavigationBars(light);
        } catch (Throwable ignored) { }
    }

    private ThemeSettings() { }
}
