package com.yagay.suite.api;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * Cross-process ownership gate for reusable LSPosed feature code.
 *
 * <p>YSuite writes a small Global Settings marker through its existing root gateway when it takes
 * ownership of a separately installed feature APK. Embedded YSuite code always remains active;
 * standalone hook code becomes passive while the marker points at YSuite. Reads intentionally
 * fail open so a missing/old YSuite installation can never break a standalone APK.</p>
 */
public final class RuntimeOwnerGate {
    public static final String SUITE_PACKAGE = "com.yagay.YSuite";
    public static final String OWNER_SUITE = "ysuite";
    public static final String KEY_PREFIX = "ysuite_runtime_owner_";

    private RuntimeOwnerGate() {}

    public static String settingsKey(String featureId) {
        return KEY_PREFIX + sanitizeFeatureId(featureId);
    }

    /**
     * Returns whether runtime code belonging to {@code hostPackage} should actively affect the
     * hooked process. The combined YSuite host always wins when it is the selected owner.
     */
    public static boolean shouldRun(String featureId, String hostPackage) {
        if (SUITE_PACKAGE.equals(hostPackage)) return true;
        return !OWNER_SUITE.equals(readOwner(featureId));
    }

    /** Convenience overload for modern libxposed callers using getModuleApplicationInfo(). */
    public static boolean shouldRun(String featureId, android.content.pm.ApplicationInfo moduleInfo) {
        String hostPackage = moduleInfo == null ? "" : moduleInfo.packageName;
        return shouldRun(featureId, hostPackage);
    }

    public static String readOwner(String featureId) {
        Context context = currentContext();
        if (context == null) return "";
        try {
            String value = Settings.Global.getString(
                    context.getContentResolver(),
                    settingsKey(featureId));
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Context currentContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object app = currentApplication.invoke(null);
            if (app instanceof Application) return ((Application) app).getApplicationContext();

            Method currentThread = activityThread.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread != null) {
                Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
                getSystemContext.setAccessible(true);
                Object system = getSystemContext.invoke(thread);
                if (system instanceof Context) return (Context) system;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String sanitizeFeatureId(String value) {
        if (value == null) return "unknown";
        String cleaned = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }
}
