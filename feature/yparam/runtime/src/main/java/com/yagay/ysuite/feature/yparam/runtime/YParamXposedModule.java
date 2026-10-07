package com.yagay.ysuite.feature.yparam.runtime;

import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class YParamXposedModule extends XposedModule {
    private static final String TAG = "YSuite/YParamHook";
    private final ConcurrentHashMap<String, YParamEnvironmentHooks> installed =
            new ConcurrentHashMap<>();

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.isFirstPackage()) return;
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
