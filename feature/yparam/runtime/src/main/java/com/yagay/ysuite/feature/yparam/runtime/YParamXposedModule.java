package com.yagay.ysuite.feature.yparam.runtime;

import android.util.Log;

import com.yagay.ysuite.runtime.RuntimeOwnerGate;

import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YParamXposedModule extends XposedModule {
    private static final String TAG = "YSuite/YParamHook";
    private String moduleHostPackage = "";
    private final ConcurrentHashMap<String, YParamEnvironmentHooks> installed =
            new ConcurrentHashMap<>();

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        try {
            if (getModuleApplicationInfo() != null
                    && getModuleApplicationInfo().packageName != null) {
                moduleHostPackage =
                        getModuleApplicationInfo().packageName;
            }
        } catch (Throwable ignored) {
            moduleHostPackage = "";
        }
        RuntimeOwnerGate.announce("yparam", moduleHostPackage);
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName()
                + " host=" + moduleHostPackage);
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.isFirstPackage()) return;
        if (!RuntimeOwnerGate.shouldRun("yparam", moduleHostPackage)) return;
        String packageName = param.getPackageName();
        if (packageName == null || packageName.equals("com.yagay.ysuite")) return;

        installed.computeIfAbsent(
                packageName,
                ignored -> {
                    try {
                        YParamEnvironmentHooks hooks =
                                new YParamEnvironmentHooks(this, packageName);
                        hooks.install();
                        log(Log.INFO, TAG, "Hooks ready for " + packageName);
                        return hooks;
                    } catch (Throwable error) {
                        log(Log.ERROR, TAG, "Failed to initialize " + packageName, error);
                        return null;
                    }
                });
    }
}
