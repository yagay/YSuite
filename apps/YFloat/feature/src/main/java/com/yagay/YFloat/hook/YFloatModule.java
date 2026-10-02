package com.yagay.YFloat.hook;

import android.util.Log;

import com.yagay.suite.api.RuntimeHandoffGate;
import com.yagay.suite.api.RuntimeOwnerGate;

import io.github.libxposed.api.XposedModule;

/** libxposed API 102 entry point for YFloat controlled providers. */
public final class YFloatModule extends XposedModule {
    private static final String TAG = "YFloat-LSPosed";
    private static final String FEATURE_ID = "yfloat";
    private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private LsposedRuntimeProvider runtimeProvider;
    private boolean googleCtsInspectorInstalled;
    private boolean suiteHost;
    private String processName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName() == null ? "" : param.getProcessName();
        suiteHost = RuntimeHandoffGate.isSuiteHost(getModuleApplicationInfo());
        if (suiteHost && !processName.isEmpty()) {
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID, processName);
        }

        runtimeProvider = new LsposedRuntimeProvider(this, processName);
        runtimeProvider.start();
        log(Log.INFO, TAG,
                "Module loaded in " + processName
                        + "; provider=" + (runtimeProvider.isActive() ? "enabled" : "disabled")
                        + "; owner=" + RuntimeOwnerGate.readOwner(FEATURE_ID)
                        + "; suiteHost=" + suiteHost);
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        final String target = "system_server";
        if (suiteHost) {
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID, target);
        } else if (!RuntimeHandoffGate.shouldStandaloneFallback(FEATURE_ID, target)) {
            log(Log.INFO, TAG, "Standalone system_server hook passive; live YSuite hook acknowledged");
            return;
        }

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

        final String target = processName.isEmpty() ? param.getPackageName() : processName;
        if (suiteHost) {
            RuntimeHandoffGate.markSuiteActive(FEATURE_ID, target);
        } else if (!RuntimeHandoffGate.shouldStandaloneFallback(FEATURE_ID, target)) {
            log(Log.INFO, TAG,
                    "Standalone Google CTS hook passive; live YSuite hook acknowledged target=" + target);
            return;
        }

        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) return;
        try {
            new GoogleCtsRuntimeInspector(this, provider, param.getClassLoader()).install();
            googleCtsInspectorInstalled = true;
            log(Log.INFO, TAG,
                    "Google CTS marked-session inspector installed; owner="
                            + RuntimeOwnerGate.readOwner(FEATURE_ID)
                            + "; suiteHost=" + suiteHost
                            + "; target=" + target);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install Google CTS marked-session inspector", t);
        }
    }
}
