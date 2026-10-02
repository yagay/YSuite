package com.yagay.YFloat.hook;

import android.util.Log;

import com.yagay.suite.api.RuntimeOwnerGate;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point for YFloat controlled providers. */
public final class YFloatModule extends XposedModule {
    private static final String TAG = "YFloat-LSPosed";
    private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private LsposedRuntimeProvider runtimeProvider;
    private boolean googleCtsInspectorInstalled;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        runtimeProvider = new LsposedRuntimeProvider(this, param.getProcessName());
        runtimeProvider.start();
        log(Log.INFO, TAG,
                "Module loaded in " + param.getProcessName()
                        + "; provider=" + (runtimeProvider.isActive() ? "enabled" : "disabled")
                        + "; owner=" + RuntimeOwnerGate.readOwner("yfloat"));
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        // Do not retire the standalone hook solely from the owner marker. YSuite can claim the
        // feature before its LSPosed entry is actually loaded/reloaded in this target process. If we
        // return here, secure screenshot / Google Circle support has no active owner at all.
        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) {
            log(Log.ERROR, TAG, "system_server provider missing; secure screenshot hook not installed");
            return;
        }
        try {
            new SecureScreenshotHook(this, provider, param.getClassLoader()).install();
        } catch (Throwable t) {
            // Never let an optional screenshot enhancement destabilize system_server startup.
            log(Log.ERROR, TAG, "Failed to install controlled secure screenshot hooks", t);
        }
        // Google CTS isolation is handled entirely inside YFloat-marked Google App sessions.
        // Never hook ContextualSearch in system_server: a process-wide component block can
        // interfere with the user's native Home/gesture Circle to Search session.
        log(Log.INFO, TAG,
                "Google contextual-search system_server blocker disabled; native CTS left untouched");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        // Google CTS hooks are installed at PackageReady so the app ClassLoader is final.
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (googleCtsInspectorInstalled || !GOOGLE_PACKAGE.equals(param.getPackageName())) return;

        // Keep the already-loaded compatibility hook alive until YSuite has a positively verified
        // target-process handoff. A Global Settings owner bit is not proof that the suite module is
        // scoped and resident in the Google process. The inspector is session-scoped and therefore
        // safer to keep than to create a zero-owner gap where Circle to Search stops completely.
        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) return;
        try {
            new GoogleCtsRuntimeInspector(this, provider, param.getClassLoader()).install();
            googleCtsInspectorInstalled = true;
            log(Log.INFO, TAG,
                    "Google CTS marked-session inspector installed; owner="
                            + RuntimeOwnerGate.readOwner("yfloat"));
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install Google CTS marked-session inspector", t);
        }
    }
}
