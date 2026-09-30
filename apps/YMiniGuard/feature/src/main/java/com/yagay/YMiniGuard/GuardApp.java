package com.yagay.YMiniGuard;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Set;

/** Standalone APK shell. All reusable state lives in GuardRuntime. */
public final class GuardApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GuardRuntime.get(this);
    }

    static boolean isXposedServiceConnected() { return GuardRuntime.isXposedServiceConnected(); }
    static boolean isUserUnlocked() { return GuardRuntime.isUserUnlocked(); }
    static long getExpectedVersionCode() { return GuardRuntime.getExpectedVersionCode(); }
    static List<String> getFrameworkScope() { return GuardRuntime.getFrameworkScope(); }
    static boolean hasSystemScope() { return GuardRuntime.hasSystemScope(); }
    static EngineStatusProvider.Status getEngineStatus() { return GuardRuntime.getEngineStatus(); }
    static long getLoadedEngineVersionCode() { return GuardRuntime.getLoadedEngineVersionCode(); }
    static long getEngineStartedAt() { return GuardRuntime.getEngineStartedAt(); }
    static int getEnginePid() { return GuardRuntime.getEnginePid(); }
    static int getEngineHookCount() { return GuardRuntime.getEngineHookCount(); }
    static long getBootstrapVersionCode() { return GuardRuntime.getBootstrapVersionCode(); }
    static boolean isHotReloadAvailable() { return GuardRuntime.isHotReloadAvailable(); }
    static long getEngineGeneration() { return GuardRuntime.getEngineGeneration(); }
    static String getEngineReloadMessage() { return GuardRuntime.getEngineReloadMessage(); }
    static int getEngineActiveSessions() { return GuardRuntime.getEngineActiveSessions(); }
    static boolean isSystemEngineActive() { return GuardRuntime.isSystemEngineActive(); }
    static boolean isSystemEngineCurrent() { return GuardRuntime.isSystemEngineCurrent(); }
    static boolean isEngineUpdatePending() { return GuardRuntime.isEngineUpdatePending(); }
    static String getFrameworkName() { return GuardRuntime.getFrameworkName(); }
    static SharedPreferences localPrefs() { return GuardRuntime.localPrefs(); }
    static boolean getBoolean(String key) { return GuardRuntime.getBoolean(key); }
    static int getInt(String key) { return GuardRuntime.getInt(key); }
    static String getString(String key) { return GuardRuntime.getString(key); }
    static void putBoolean(String key, boolean value) { GuardRuntime.putBoolean(key, value); }
    static void putInt(String key, int value) { GuardRuntime.putInt(key, value); }
    static void putString(String key, String value) { GuardRuntime.putString(key, value); }
    static Set<String> getStringSet(String key) { return GuardRuntime.getStringSet(key); }
    static void putStringSet(String key, Set<String> value) { GuardRuntime.putStringSet(key, value); }
    static boolean packageSelected(String key, String packageName) { return GuardRuntime.packageSelected(key, packageName); }
    static boolean requestEngineReload() { return GuardRuntime.requestEngineReload(); }
    static void resetDefaults() { GuardRuntime.resetDefaults(); }
    static boolean syncAll() { return GuardRuntime.syncAll(); }
}
