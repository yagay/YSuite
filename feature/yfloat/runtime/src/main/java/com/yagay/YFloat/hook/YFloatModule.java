package com.yagay.YFloat.hook;

import android.util.Log;

import com.yagay.ysuite.runtime.RuntimeOwnerGate;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YFloatModule extends XposedModule {
    private static final String TAG = "YFloat-LSPosed";
    private static final String GOOGLE_PACKAGE = "com.google.android.googlequicksearchbox";
    private LsposedRuntimeProvider runtimeProvider;
    private boolean googleCtsInspectorInstalled;
    private String processName = "";
    private String moduleHostPackage = "";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        processName = param.getProcessName() == null ? "" : param.getProcessName();
        try {
            if (getModuleApplicationInfo() != null
                    && getModuleApplicationInfo().packageName != null) {
                moduleHostPackage =
                        getModuleApplicationInfo().packageName;
            }
        } catch (Throwable ignored) {
            moduleHostPackage = "";
        }
        RuntimeOwnerGate.announce(
                "yfloat",
                moduleHostPackage);
        runtimeProvider = new LsposedRuntimeProvider(this, processName);
        runtimeProvider.start();
        log(Log.INFO, TAG, "Module loaded in " + processName
                + "; provider=" + (runtimeProvider.isActive() ? "enabled" : "disabled")
                + "; host=YSuite");
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        if (!RuntimeOwnerGate.shouldRun(
                "yfloat",
                moduleHostPackage)) {
            return;
        }
        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) {
            log(Log.ERROR, TAG, "system_server provider missing");
            return;
        }
        try {
            new SecureScreenshotHook(this, provider, param.getClassLoader()).install();
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install secure screenshot hooks", error);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!RuntimeOwnerGate.shouldRun(
                "yfloat",
                moduleHostPackage)) {
            return;
        }
        if (googleCtsInspectorInstalled || !GOOGLE_PACKAGE.equals(param.getPackageName())) return;
        LsposedRuntimeProvider provider = runtimeProvider;
        if (provider == null) return;
        try {
            new GoogleCtsRuntimeInspector(this, provider, param.getClassLoader()).install();
            googleCtsInspectorInstalled = true;
            log(Log.INFO, TAG, "Google CTS marked-session inspector installed target=" + processName);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Failed to install Google CTS marked-session inspector", error);
        }
    }
}
