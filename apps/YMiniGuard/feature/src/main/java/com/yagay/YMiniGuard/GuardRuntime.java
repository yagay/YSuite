package com.yagay.YMiniGuard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.util.Log;

import com.yagay.suite.api.FeatureHost;
import com.yagay.suite.api.ManagedFeatureRuntime;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Host-neutral runtime shared by standalone YMiniGuard and YSuite. */
public final class GuardRuntime implements XposedServiceHelper.OnServiceListener, ManagedFeatureRuntime {
    private static final String TAG = "YMiniGuard";
    private static final String SUITE_BROKER = "com.yagay.suite.core.SuiteXposedServiceBroker";

    private static final String[] BOOLEAN_KEYS = {
            ConfigKeys.MASTER_ENABLED,
            ConfigKeys.ENGINE_AUTO_RELOAD,
            ConfigKeys.SYSTEM_IMPORTANCE_TOP,
            ConfigKeys.SYSTEM_HAS_RESUMED,
            ConfigKeys.SYSTEM_BLOCK_REMOVE_KILL,
            ConfigKeys.DIAGNOSTICS_ACTIVE
    };

    private static final String[] INT_KEYS = {
            ConfigKeys.ENGINE_RELOAD_SEQ
    };

    private static final String[] STRING_SET_KEYS = {
            ConfigKeys.FOREGROUND_PACKAGES,
            ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES,
            ConfigKeys.FORCE_SUPPORT_PACKAGES
    };

    private static volatile GuardRuntime instance;
    private static volatile XposedService service;
    private static volatile String frameworkName = "";

    private final Context context;
    private volatile boolean enabled = true;
    private volatile FeatureHost host;

    private GuardRuntime(Context context) {
        Context app = context.getApplicationContext();
        this.context = app != null ? app : context;
        boolean suiteHost = attachToSuiteBroker();
        if (!suiteHost) {
            // Standalone still owns its local LSPosed service listener, but crash handling is never
            // process-global Feature infrastructure. Host diagnostics remains the single owner.
            XposedServiceHelper.registerListener(this);
        }
    }

    public static GuardRuntime get(Context context) {
        GuardRuntime local = instance;
        if (local != null) return local;
        synchronized (GuardRuntime.class) {
            local = instance;
            if (local == null) {
                local = new GuardRuntime(context);
                instance = local;
            }
            return local;
        }
    }

    @Override
    public void attach(FeatureHost host) {
        this.host = host;
    }

    @Override
    public void enable() {
        enabled = true;
        Log.i(TAG, "managed runtime enabled");
    }

    @Override
    public void disable() {
        enabled = false;
        service = null;
        frameworkName = "";
        Log.i(TAG, "managed runtime disabled; LSPosed service released");
    }

    @Override
    public void destroy() {
        disable();
        host = null;
    }

    private boolean attachToSuiteBroker() {
        final Class<?> broker;
        try {
            broker = Class.forName(SUITE_BROKER, false, GuardRuntime.class.getClassLoader());
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (Throwable error) {
            Log.e(TAG, "YSuite broker lookup failed", error);
            return true;
        }

        try {
            Method attach = broker.getMethod("attachFromPlugin", String.class, Object.class);
            Object result = attach.invoke(null, "yminiguard", this);
            if (!Boolean.TRUE.equals(result)) {
                Log.e(TAG, "YSuite broker rejected YMiniGuard listener");
            }
        } catch (Throwable error) {
            Log.e(TAG, "YSuite broker attach failed", error);
        }
        return true;
    }

    @Override
    public void onServiceBind(XposedService bound) {
        if (!enabled) return;
        service = bound;
        try {
            frameworkName = bound.getFrameworkName();
        } catch (Throwable ignored) {
            frameworkName = "LSPosed";
        }

        if (isUserUnlocked()) syncAll();
        Log.i(TAG, "LSPosed service connected: " + frameworkName);
    }

    @Override
    public void onServiceDied(XposedService dead) {
        if (service == dead) service = null;
        frameworkName = "";
        Log.w(TAG, "LSPosed service disconnected");
    }

    static boolean isXposedServiceConnected() {
        return service != null;
    }

    static boolean isUserUnlocked() {
        GuardRuntime runtime = instance;
        if (runtime == null) return false;
        try {
            UserManager manager = runtime.context.getSystemService(UserManager.class);
            return manager == null || manager.isUserUnlocked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static long getExpectedVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    static List<String> getFrameworkScope() {
        XposedService current = service;
        if (current == null) return Collections.emptyList();
        try {
            List<String> scope = current.getScope();
            return scope == null ? Collections.emptyList() : scope;
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read LSPosed scope", t);
            return Collections.emptyList();
        }
    }

    static boolean hasSystemScope() {
        return getFrameworkScope().contains("system");
    }

    static EngineStatusProvider.Status getEngineStatus() {
        GuardRuntime runtime = instance;
        return EngineStatusProvider.read(runtime == null ? null : runtime.context);
    }

    static long getLoadedEngineVersionCode() { return getEngineStatus().versionCode; }
    static long getEngineStartedAt() { return getEngineStatus().startedAt; }
    static int getEnginePid() { return getEngineStatus().pid; }
    static int getEngineHookCount() { return getEngineStatus().hookCount; }
    static long getBootstrapVersionCode() { return getEngineStatus().bootstrapVersionCode; }
    static boolean isHotReloadAvailable() { return getEngineStatus().hotReload; }
    static long getEngineGeneration() { return getEngineStatus().generation; }
    static String getEngineReloadMessage() { return getEngineStatus().reloadMessage; }
    static int getEngineActiveSessions() { return getEngineStatus().activeSessions; }

    static boolean isSystemEngineActive() {
        EngineStatusProvider.Status status = getEngineStatus();
        return service != null
                && hasSystemScope()
                && status.versionCode > 0
                && status.pid > 0
                && status.hookCount > 0
                && status.isFromCurrentBoot();
    }

    static boolean isSystemEngineCurrent() {
        long expected = getExpectedVersionCode();
        EngineStatusProvider.Status status = getEngineStatus();
        return isSystemEngineActive()
                && expected > 0
                && status.versionCode == expected;
    }

    static boolean isEngineUpdatePending() {
        return isSystemEngineActive() && !isSystemEngineCurrent();
    }

    static String getFrameworkName() {
        return frameworkName.isEmpty() ? com.yagay.suite.api.YLocale.text(com.yagay.YMiniGuard.R.string.ymg_generated_661bf07ed641) : frameworkName;
    }

    static SharedPreferences localPrefs() {
        GuardRuntime runtime = instance;
        if (runtime == null) throw new IllegalStateException("GuardRuntime not initialized");
        return runtime.context.getSharedPreferences(ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE);
    }

    static boolean getBoolean(String key) {
        return localPrefs().getBoolean(key, ConfigKeys.defaultBoolean(key));
    }

    static int getInt(String key) {
        return localPrefs().getInt(key, ConfigKeys.defaultInt(key));
    }

    static String getString(String key) {
        String value = localPrefs().getString(key, "");
        return value == null ? "" : value;
    }

    static void putBoolean(String key, boolean value) {
        localPrefs().edit().putBoolean(key, value).apply();
        syncAll();
    }

    static void putInt(String key, int value) {
        localPrefs().edit().putInt(key, value).apply();
        syncAll();
    }

    static void putString(String key, String value) {
        localPrefs().edit().putString(key, value == null ? "" : value).apply();
        syncAll();
    }

    static Set<String> getStringSet(String key) {
        Set<String> value = localPrefs().getStringSet(key, Collections.emptySet());
        return value == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(value));
    }

    static void putStringSet(String key, Set<String> value) {
        localPrefs().edit().putStringSet(
                key,
                value == null ? Collections.emptySet() : new HashSet<>(value)).apply();
        syncAll();
    }

    static boolean packageSelected(String key, String packageName) {
        return packageName != null && getStringSet(key).contains(packageName);
    }

    static boolean requestEngineReload() {
        int seq = getInt(ConfigKeys.ENGINE_RELOAD_SEQ) + 1;
        localPrefs().edit().putInt(ConfigKeys.ENGINE_RELOAD_SEQ, seq).apply();
        return syncAll();
    }

    static void resetDefaults() {
        localPrefs().edit().clear().commit();
        syncAll();
    }

    static synchronized boolean syncAll() {
        XposedService current = service;
        GuardRuntime runtime = instance;
        if (current == null || runtime == null || !runtime.enabled || !isUserUnlocked()) return false;

        try {
            SharedPreferences.Editor editor = current.getRemotePreferences(ConfigKeys.REMOTE_GROUP).edit();
            for (String key : BOOLEAN_KEYS) editor.putBoolean(key, getBoolean(key));
            for (String key : INT_KEYS) editor.putInt(key, getInt(key));
            for (String key : STRING_SET_KEYS) {
                editor.putStringSet(key, new HashSet<>(getStringSet(key)));
            }
            editor.putString(ConfigKeys.DIAGNOSTICS_STARTED_AT, getString(ConfigKeys.DIAGNOSTICS_STARTED_AT));
            editor.putString(ConfigKeys.HOST_PACKAGE, runtime.context.getPackageName());
            editor.putLong(ConfigKeys.MODULE_VERSION, BuildConfig.VERSION_CODE);
            return editor.commit();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to sync remote preferences", t);
            return false;
        }
    }
}
