package com.yagay.YFloat;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import com.yagay.suite.api.FeatureServices;
import com.yagay.suite.api.ManagedFeatureRuntime;
import com.yagay.suite.api.XposedHostBridge;

import java.io.File;

/** One app-side initializer shared by standalone YFloat and YSuite. */
public final class YFloatSuiteRuntime implements ManagedFeatureRuntime {
    private static final FeatureServices SERVICES = FeatureServices.of("yfloat", "YFloat");
    private static final YFloatSuiteRuntime INSTANCE = new YFloatSuiteRuntime();
    private static boolean initialized;
    private static YFloatApp callbacks;
    private static Context appContext;
    private static final LsposedStatusManager.Listener HOOK_LISTENER =
            snapshot -> {
                Context app = appContext;
                if (app != null) HookReloadManager.autoReloadChangedTargets(app, snapshot);
            };

    private volatile boolean enabled = true;

    private YFloatSuiteRuntime() { }

    public static synchronized Object get(Context context) {
        if (!initialized) initializeOnce(context);
        return INSTANCE;
    }

    private static void initializeOnce(Context context) {
        Context app = context == null ? null : context.getApplicationContext();
        if (app == null) app = context;
        if (app == null) return;
        appContext = app;

        try { SettingsMigrator.run(app); }
        catch (Throwable t) { DiagnosticLog.i(app, "APP_MIGRATION", "settings migration failed=" + t); }
        ThemeSettings.applySavedMode(app);
        HookReloadManager.initialize(app);
        try { RemovedFeatureMigration.run(app); }
        catch (Throwable t) { DiagnosticLog.i(app, "APP_MIGRATION", "AI/dictionary cleanup failed=" + t); }
        YFloatHostIdentity.initialize(app);
        LsposedStatusManager.initialize(app);
        registerHotReloadListenerIfStandalone();
        registerActivityCallbacks();
        initialized = true;
    }

    private static void registerHotReloadListenerIfStandalone() {
        Context app = appContext;
        if (app == null) return;
        if (!XposedHostBridge.isSuiteHost(app)) {
            LsposedStatusManager.addListener(HOOK_LISTENER, true);
        } else {
            DiagnosticLog.i(app, "HOOK_RELOAD", "YSuite host owns automatic Hook target reload");
        }
    }

    private static void registerActivityCallbacks() {
        Context app = appContext;
        if (!(app instanceof Application) || callbacks != null) return;
        callbacks = app instanceof YFloatApp ? (YFloatApp) app : new YFloatApp();
        ((Application) app).registerActivityLifecycleCallbacks(callbacks);
    }

    public static Uri sharedFileUri(File file) {
        return SERVICES.sharedFileUri(file);
    }

    @Override
    public synchronized void enable() {
        enabled = true;
        registerActivityCallbacks();
        registerHotReloadListenerIfStandalone();
        Context app = appContext;
        if (app != null) DiagnosticLog.i(app, "RUNTIME", "managed runtime enabled");
    }

    @Override
    public synchronized void disable() {
        if (!enabled) return;
        enabled = false;
        LsposedStatusManager.removeListener(HOOK_LISTENER);
        LsposedStatusManager.clearGoogleCtsSessionRemoteNow();
        LsposedStatusManager.disarmSecureCaptureAsync();
        FloatActionMenu.dismiss();

        Context app = appContext;
        if (app instanceof Application && callbacks != null) {
            ((Application) app).unregisterActivityLifecycleCallbacks(callbacks);
        }
        callbacks = null;
        if (app != null) DiagnosticLog.i(app, "RUNTIME", "managed runtime disabled; UI callbacks and transient Hook leases released");
    }

    @Override
    public synchronized void destroy() {
        disable();
    }
}
