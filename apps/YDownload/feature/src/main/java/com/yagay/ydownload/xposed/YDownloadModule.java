package com.yagay.ydownload.xposed;

import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.yagay.suite.api.RuntimeOwnerGate;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedModule;

/**
 * Patch-first DownloadManager integration.
 *
 * The hook never replaces DownloadProvider, never swallows enqueue(), and never fabricates download
 * IDs. It only enriches Request policy and then proceeds through Android's original implementation.
 */
public final class YDownloadModule extends XposedModule {
    private static final String TAG = "YDownloadXposed";
    private static final String PREFS = "ydownload_patch";
    private static final Set<String> HARD_EXCLUDED = Set.of(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.google.android.gms",
            "com.android.vending"
    );
    private final ConcurrentHashMap<String, Boolean> installed = new ConcurrentHashMap<>();

    @Override public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        if (!RuntimeOwnerGate.shouldRun("ydownload", getModuleApplicationInfo())) return;
        final String pkg = param.getPackageName();
        if (pkg == null || HARD_EXCLUDED.contains(pkg) || pkg.startsWith("com.yagay.ydownload")) return;
        installed.computeIfAbsent(pkg, ignored -> {
            installDownloadManagerPatch(pkg);
            return Boolean.TRUE;
        });
    }

    private void installDownloadManagerPatch(String packageName) {
        try {
            Method enqueue = DownloadManager.class.getDeclaredMethod("enqueue", DownloadManager.Request.class);
            hook(enqueue).intercept(chain -> {
                SharedPreferences prefs = getRemotePreferences(PREFS);
                if (!prefs.getBoolean("enabled", true)) return chain.proceed();
                Object arg = chain.getArg(0);
                if (arg instanceof DownloadManager.Request request) {
                    applyRequestPatch(request, prefs);
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "DownloadManager patch ready for " + packageName);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "DownloadManager patch unavailable for " + packageName, t);
        }
    }

    private void applyRequestPatch(DownloadManager.Request request, SharedPreferences prefs) {
        try {
            request.setAllowedOverMetered(prefs.getBoolean("allow_metered", true));
            request.setAllowedOverRoaming(prefs.getBoolean("allow_roaming", false));
            if (prefs.getBoolean("force_completion_notification", true)) {
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                request.setRequiresCharging(prefs.getBoolean("require_charging", false));
                request.setRequiresDeviceIdle(prefs.getBoolean("require_device_idle", false));
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Request patch partially skipped", t);
        }
    }

    @Override public boolean onHotReloading(@NonNull HotReloadingParam param) { return true; }

    @Override public void onHotReloaded(@NonNull HotReloadedParam param) {
        param.getOldHookHandles().forEach(HookHandle::unhook);
        installed.clear();
    }
}
