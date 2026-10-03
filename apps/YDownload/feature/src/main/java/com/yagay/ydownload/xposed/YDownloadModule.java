package com.yagay.ydownload.xposed;

import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import com.yagay.suite.api.RuntimeOwnerGate;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patch-first DownloadManager integration.
 *
 * The hook never replaces DownloadProvider, never swallows enqueue(), and never fabricates download
 * IDs. All hook callbacks are fail-open: if LSPosed state, remote preferences, OEM reflection or a
 * patch API is unavailable, Android's original DownloadManager call is executed unchanged.
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
        try {
            log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
        } catch (Throwable ignored) { }
    }

    @Override public void onPackageReady(@NonNull PackageReadyParam param) {
        try {
            if (!param.isFirstPackage()) return;
            if (!RuntimeOwnerGate.shouldRun("ydownload", getModuleApplicationInfo())) return;
            final String pkg = param.getPackageName();
            if (pkg == null || HARD_EXCLUDED.contains(pkg) || pkg.startsWith("com.yagay.ydownload")) return;
            installed.computeIfAbsent(pkg, ignored -> {
                installDownloadManagerPatch(pkg);
                return Boolean.TRUE;
            });
        } catch (Throwable t) {
            logSafely(Log.WARN, "Package-ready patch skipped safely", t);
        }
    }

    private void installDownloadManagerPatch(String packageName) {
        try {
            Method enqueue = DownloadManager.class.getDeclaredMethod("enqueue", DownloadManager.Request.class);
            hook(enqueue).intercept(chain -> {
                try {
                    SharedPreferences prefs = remotePreferencesOrNull();
                    if (prefs == null || !prefs.getBoolean("enabled", true)) return chain.proceed();
                    Object arg = chain.getArg(0);
                    if (arg instanceof DownloadManager.Request request) {
                        applyRequestPatch(request, prefs);
                    }
                } catch (Throwable t) {
                    logSafely(Log.WARN, "Download callback failed; using original request", t);
                }
                return chain.proceed();
            });
            logSafely(Log.INFO, "DownloadManager patch ready for " + packageName, null);
        } catch (Throwable t) {
            logSafely(Log.WARN, "DownloadManager patch unavailable for " + packageName, t);
        }
    }

    private SharedPreferences remotePreferencesOrNull() {
        try {
            return getRemotePreferences(PREFS);
        } catch (Throwable t) {
            logSafely(Log.WARN, "Remote preferences unavailable; patch skipped", t);
            return null;
        }
    }

    private void applyRequestPatch(DownloadManager.Request request, SharedPreferences prefs) {
        try {
            // Only tighten policy. Leaving a toggle permissive preserves whatever the caller set.
            if (!prefs.getBoolean("allow_metered", true)) {
                request.setAllowedOverMetered(false);
            }
            if (!prefs.getBoolean("allow_roaming", true)) {
                request.setAllowedOverRoaming(false);
            }
            if (prefs.getBoolean("force_completion_notification", false)) {
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (prefs.getBoolean("require_charging", false)) {
                    request.setRequiresCharging(true);
                }
                if (prefs.getBoolean("require_device_idle", false)) {
                    request.setRequiresDeviceIdle(true);
                }
            }
        } catch (Throwable t) {
            logSafely(Log.WARN, "Request patch partially skipped", t);
        }
    }

    private void logSafely(int priority, String message, Throwable error) {
        try {
            if (error == null) log(priority, TAG, message);
            else log(priority, TAG, message, error);
        } catch (Throwable ignored) { }
    }

    @Override public boolean onHotReloading(@NonNull HotReloadingParam param) {
        return true;
    }

    @Override public void onHotReloaded(@NonNull HotReloadedParam param) {
        try {
            param.getOldHookHandles().forEach(handle -> {
                try { handle.unhook(); } catch (Throwable t) {
                    logSafely(Log.WARN, "Old YDownload hook could not be removed", t);
                }
            });
        } catch (Throwable t) {
            logSafely(Log.WARN, "YDownload hot-reload cleanup failed safely", t);
        } finally {
            installed.clear();
        }
    }
}
