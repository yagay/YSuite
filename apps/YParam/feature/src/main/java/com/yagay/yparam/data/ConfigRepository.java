package com.yagay.yparam.data;

import android.content.SharedPreferences;

import com.yagay.yparam.YParamApp;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.service.XposedService;

public final class ConfigRepository {
    private static final String PREFIX = "app.";
    private ConfigRepository() {}

    public static String key(String packageName) { return PREFIX + packageName; }

    public static AppConfig get(String packageName) {
        SharedPreferences p = YParamApp.remotePrefs();
        return p == null ? new AppConfig() : AppConfig.fromJson(p.getString(key(packageName), null));
    }

    public static boolean save(String packageName, AppConfig config) {
        SharedPreferences p = YParamApp.remotePrefs();
        if (p == null) return false;
        SharedPreferences.Editor e = p.edit();
        if (config == null || config.isEmpty()) e.remove(key(packageName));
        else e.putString(key(packageName), config.toJson());
        return e.commit();
    }

    public static boolean reset(String packageName) {
        SharedPreferences p = YParamApp.remotePrefs();
        return p != null && p.edit().remove(key(packageName)).commit();
    }

    public static boolean isConfigured(String packageName) {
        SharedPreferences p = YParamApp.remotePrefs();
        return p != null && p.contains(key(packageName));
    }

    public static Set<String> configuredPackages() {
        SharedPreferences p = YParamApp.remotePrefs();
        if (p == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (String k : p.getAll().keySet()) if (k.startsWith(PREFIX)) out.add(k.substring(PREFIX.length()));
        return out;
    }

    public static boolean isInScope(String packageName) {
        XposedService s = YParamApp.getService();
        if (s == null) return false;
        try { return s.getScope().contains(packageName); }
        catch (Throwable ignored) { return false; }
    }

    public static void requestScope(String packageName, XposedService.OnScopeEventListener listener) {
        XposedService s = YParamApp.getService();
        if (s != null) s.requestScope(Collections.singletonList(packageName), listener);
    }
}
