package com.yagay.suite.api;

import android.content.Context;
import android.provider.Settings;

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
        return KEY_PREFIX + FeatureIds.normalize(featureId);
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
        Context context = AndroidRuntimeContext.current();
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
}
