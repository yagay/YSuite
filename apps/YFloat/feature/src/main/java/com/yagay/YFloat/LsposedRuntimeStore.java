package com.yagay.YFloat;

import android.content.SharedPreferences;

import io.github.libxposed.service.XposedService;

/** Single schema/write boundary for framework-backed LSPosed runtime preferences. */
final class LsposedRuntimeStore {
    static SharedPreferences open(XposedService service) {
        if (service == null) return null;
        return service.getRemotePreferences(LsposedRuntimeConfig.GROUP);
    }

    static SharedPreferences syncPersistent(XposedService service,
                                            boolean enhanced,
                                            boolean lsposed,
                                            boolean secureScreenshot,
                                            boolean diagnostic,
                                            long updatedAt, FloatSettings settings) {
        SharedPreferences remote = open(service);
        if (remote == null) return null;
        boolean committed = remote.edit()
                .putInt(LsposedRuntimeConfig.K_SCHEMA_VERSION, LsposedRuntimeConfig.SCHEMA_VERSION)
                .putBoolean(LsposedRuntimeConfig.K_ENHANCED_MODE, enhanced)
                .putBoolean(LsposedRuntimeConfig.K_LSPOSED_ENABLED, lsposed)
                .putBoolean(LsposedRuntimeConfig.K_SECURE_SCREENSHOT_ENABLED, secureScreenshot)
                .putBoolean(LsposedRuntimeConfig.K_DIAGNOSTIC_ENABLED, diagnostic)
                .putLong(LsposedRuntimeConfig.K_UPDATED_AT, updatedAt)
                .commit();
        return committed ? remote : null;
    }




    static SharedPreferences armSecure(XposedService service,
                                       boolean enhanced,
                                       boolean lsposed,
                                       boolean secureScreenshot,
                                       long armedUntil,
                                       long updatedAt) {
        SharedPreferences remote = open(service);
        if (remote == null) return null;
        boolean committed = remote.edit()
                .putInt(LsposedRuntimeConfig.K_SCHEMA_VERSION, LsposedRuntimeConfig.SCHEMA_VERSION)
                .putBoolean(LsposedRuntimeConfig.K_ENHANCED_MODE, enhanced)
                .putBoolean(LsposedRuntimeConfig.K_LSPOSED_ENABLED, lsposed)
                .putBoolean(LsposedRuntimeConfig.K_SECURE_SCREENSHOT_ENABLED, secureScreenshot)
                .putLong(LsposedRuntimeConfig.K_SECURE_CAPTURE_ARMED_UNTIL, armedUntil)
                .putLong(LsposedRuntimeConfig.K_UPDATED_AT, updatedAt)
                .commit();
        return committed ? remote : null;
    }

    static SharedPreferences disarmSecure(XposedService service, long updatedAt) {
        SharedPreferences remote = open(service);
        if (remote == null) return null;
        boolean committed = remote.edit()
                .putLong(LsposedRuntimeConfig.K_SECURE_CAPTURE_ARMED_UNTIL, 0L)
                .putLong(LsposedRuntimeConfig.K_UPDATED_AT, updatedAt)
                .commit();
        return committed ? remote : null;
    }

    private LsposedRuntimeStore() {}
}
