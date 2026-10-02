package com.yagay.yfiles.xposed;

import android.util.Log;
import androidx.annotation.NonNull;
import com.yagay.suite.api.RuntimeOwnerGate;
import io.github.libxposed.api.XposedModule;

/** Safe integration point for future DocumentsUI/SAF augmentation. */
public final class YFilesModule extends XposedModule {
    @Override public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, "YFilesXposed", "Loaded in " + param.getProcessName());
    }
    @Override public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!RuntimeOwnerGate.shouldRun("yfiles", getModuleApplicationInfo())) return;
        // DocumentsUI remains installed. Future hooks augment picker UX instead of replacing SAF ownership.
    }
    @Override public boolean onHotReloading(@NonNull HotReloadingParam param) { return true; }
    @Override public void onHotReloaded(@NonNull HotReloadedParam param) { param.getOldHookHandles().forEach(HookHandle::unhook); }
}
