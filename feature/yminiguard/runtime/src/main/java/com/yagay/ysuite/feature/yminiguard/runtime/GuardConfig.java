package com.yagay.ysuite.feature.yminiguard.runtime;

import android.content.SharedPreferences;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class GuardConfig {
    private static volatile SharedPreferences prefs;
    private GuardConfig() {}

    static void initialize(SharedPreferences remote) {
        prefs = remote;
        EngineStatusProvider.updateAuthority(hostPackage());
    }

    static boolean bool(String key) {
        SharedPreferences p = prefs;
        if (p == null) return ConfigKeys.defaultBoolean(key);
        try {
            Object value = p.getAll().get(key);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof String) return Boolean.parseBoolean((String) value);
        } catch (Throwable ignored) {}
        return ConfigKeys.defaultBoolean(key);
    }

    static int integer(String key) {
        SharedPreferences p = prefs;
        if (p == null) return 0;
        try {
            Object value = p.getAll().get(key);
            if (value instanceof Integer) return (Integer) value;
            if (value instanceof Long) return ((Long) value).intValue();
            if (value instanceof String) return Integer.parseInt(((String) value).trim());
        } catch (Throwable ignored) {}
        return 0;
    }

    static long longValue(String key, long fallback) {
        SharedPreferences p = prefs;
        if (p == null) return fallback;
        try {
            Object value = p.getAll().get(key);
            if (value instanceof Long) return (Long) value;
            if (value instanceof Integer) return ((Integer) value).longValue();
            if (value instanceof String) return Long.parseLong(((String) value).trim());
        } catch (Throwable ignored) {}
        return fallback;
    }

    static String string(String key) {
        SharedPreferences p = prefs;
        if (p == null) return "";
        try {
            Object value = p.getAll().get(key);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable ignored) {
            return "";
        }
    }

    static String hostPackage() {
        String value = string(ConfigKeys.HOST_PACKAGE).trim();
        return value.isEmpty() ? "com.yagay.ysuite" : value;
    }

    static long moduleVersion() {
        return longValue(ConfigKeys.MODULE_VERSION, BuildConfig.VERSION_CODE);
    }

    static Set<String> stringSet(String key) {
        SharedPreferences p = prefs;
        if (p == null) return Collections.emptySet();
        try {
            Object value = p.getAll().get(key);
            if (value instanceof Set<?>) {
                Set<String> result = new HashSet<>();
                for (Object item : (Set<?>) value) {
                    if (item != null) result.add(String.valueOf(item));
                }
                return Collections.unmodifiableSet(result);
            }
            if (value instanceof String) {
                Set<String> result = new HashSet<>();
                for (String item : ((String) value).split(",")) {
                    String normalized = item.trim();
                    if (!normalized.isEmpty()) result.add(normalized);
                }
                return Collections.unmodifiableSet(result);
            }
        } catch (Throwable ignored) {}
        return Collections.emptySet();
    }

    static boolean enabled() {
        return bool(ConfigKeys.MASTER_ENABLED);
    }
    static boolean foregroundPackage(String packageName) {
        return enabled() && packageName != null &&
                stringSet(ConfigKeys.FOREGROUND_PACKAGES).contains(packageName);
    }
    static boolean backgroundPlaybackPackage(String packageName) {
        return enabled() && packageName != null &&
                stringSet(ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES).contains(packageName);
    }
    static boolean forceSupportPackage(String packageName) {
        return enabled() && packageName != null &&
                stringSet(ConfigKeys.FORCE_SUPPORT_PACKAGES).contains(packageName);
    }
}
