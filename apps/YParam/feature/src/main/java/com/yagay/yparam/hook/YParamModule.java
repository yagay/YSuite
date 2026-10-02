package com.yagay.yparam.hook;

import android.util.Log;

import com.yagay.suite.api.RuntimeOwnerGate;

import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class YParamModule extends XposedModule {
    private static final String TAG = "YParam";
    private final ConcurrentHashMap<String, EnvironmentHooks> installed = new ConcurrentHashMap<>();

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        if (!RuntimeOwnerGate.shouldRun("yparam", getModuleApplicationInfo())) {
            log(Log.INFO, TAG, "Standalone hooks passive; YSuite owns yparam runtime");
            return;
        }
        String pkg = param.getPackageName();
        if (pkg == null || pkg.equals("com.yagay.yparam")) return;
        installed.computeIfAbsent(pkg, ignored -> {
            try {
                EnvironmentHooks hooks = new EnvironmentHooks(this, pkg);
                hooks.install();
                log(Log.INFO, TAG, "Hooks ready for " + pkg);
                return hooks;
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Failed to initialize " + pkg, t);
                return null;
            }
        });
    }
}
