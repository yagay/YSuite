package com.yagay.ydownload.xposed;

import android.util.Log;
import androidx.annotation.NonNull;
import com.yagay.suite.api.RuntimeOwnerGate;
import io.github.libxposed.api.XposedModule;

/** Integration point for the later DownloadManager/DownloadProvider compatibility bridge. */
public final class YDownloadModule extends XposedModule {
    @Override public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, "YDownloadXposed", "Loaded in " + param.getProcessName());
    }
    @Override public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!RuntimeOwnerGate.shouldRun("ydownload", getModuleApplicationInfo())) return;
        // Deliberately no provider replacement hook yet: the stock provider remains the safe compatibility owner.
    }
    @Override public boolean onHotReloading(@NonNull HotReloadingParam param) { return true; }
    @Override public void onHotReloaded(@NonNull HotReloadedParam param) { param.getOldHookHandles().forEach(HookHandle::unhook); }
}
