package com.yagay.suite.api;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

/**
 * Target-process acknowledgement used during standalone -> YSuite runtime handoff.
 *
 * <p>The suite hook reports presence from inside the actual target process. A standalone hook only
 * retires when the YSuite host has acknowledged the same feature in the same live process. If the
 * provider is missing, the target was not hooked by YSuite, or any IPC fails, this class deliberately
 * fails open so the standalone module remains a working compatibility fallback.</p>
 */
public final class RuntimeHandoffGate {
    public static final String AUTHORITY = "com.yagay.YSuite.runtime_handoff";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/status");
    public static final String METHOD_MARK_ACTIVE = "mark_active";
    public static final String METHOD_IS_ACTIVE = "is_active";
    public static final String EXTRA_FEATURE = "feature";
    public static final String EXTRA_TARGET = "target";
    public static final String RESULT_ACTIVE = "active";

    private RuntimeHandoffGate() {}

    public static boolean isSuiteHost(android.content.pm.ApplicationInfo moduleInfo) {
        return moduleInfo != null && RuntimeOwnerGate.SUITE_PACKAGE.equals(moduleInfo.packageName);
    }

    /** Called only by code running as an embedded YSuite logical plugin. */
    public static void markSuiteActive(String featureId, String targetProcess) {
        Context context = currentContext();
        if (context == null) return;
        Bundle extras = extras(featureId, targetProcess);
        try {
            context.getContentResolver().call(URI, METHOD_MARK_ACTIVE, null, extras);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Returns true only when YSuite has positively acknowledged this exact live target process.
     * Any uncertainty means false, keeping the standalone hook active as the safe fallback.
     */
    public static boolean isSuiteActiveHere(String featureId, String targetProcess) {
        Context context = currentContext();
        if (context == null) return false;
        Bundle extras = extras(featureId, targetProcess);
        try {
            Bundle result = context.getContentResolver().call(URI, METHOD_IS_ACTIVE, null, extras);
            return result != null && result.getBoolean(RESULT_ACTIVE, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when this standalone module should keep providing compatibility behavior. */
    public static boolean shouldStandaloneFallback(String featureId, String targetProcess) {
        if (!RuntimeOwnerGate.OWNER_SUITE.equals(RuntimeOwnerGate.readOwner(featureId))) return true;
        return !isSuiteActiveHere(featureId, targetProcess);
    }

    private static Bundle extras(String featureId, String targetProcess) {
        Bundle extras = new Bundle();
        extras.putString(EXTRA_FEATURE, sanitize(featureId));
        extras.putString(EXTRA_TARGET, targetProcess == null ? "" : targetProcess);
        extras.putInt("pid", Process.myPid());
        return extras;
    }

    private static String sanitize(String value) {
        if (value == null) return "unknown";
        String cleaned = value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private static Context currentContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object app = currentApplication.invoke(null);
            if (app instanceof android.app.Application) {
                return ((android.app.Application) app).getApplicationContext();
            }

            java.lang.reflect.Method currentThread = activityThread.getDeclaredMethod("currentActivityThread");
            currentThread.setAccessible(true);
            Object thread = currentThread.invoke(null);
            if (thread != null) {
                java.lang.reflect.Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
                getSystemContext.setAccessible(true);
                Object system = getSystemContext.invoke(thread);
                if (system instanceof Context) return (Context) system;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
