package com.yagay.ysuite.feature.ydownload.xposed;

import android.app.DownloadManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class YDownloadModule extends XposedModule {
    private static final String TAG = "YDownloadXposed";
    private static final String PREFS = "ydownload_patch";
    private static final String KEY_CONFIG = "config";
    private static final Set<String> HARD_EXCLUDED = Set.of(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.google.android.gms",
            "com.android.vending"
    );
    private final ConcurrentHashMap<String, Boolean> installed =
            new ConcurrentHashMap<>();

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        try {
            if (!param.isFirstPackage()) return;
            String pkg = param.getPackageName();
            if (pkg == null || HARD_EXCLUDED.contains(pkg)
                    || pkg.startsWith("com.yagay.ysuite")) {
                return;
            }
            installed.computeIfAbsent(pkg, ignored -> {
                installDownloadManagerPatch(pkg);
                return Boolean.TRUE;
            });
        } catch (Throwable error) {
            logSafely(
                    Log.WARN,
                    "Package-ready patch skipped safely",
                    error);
        }
    }

    private void installDownloadManagerPatch(String packageName) {
        try {
            Method enqueue =
                    DownloadManager.class.getDeclaredMethod(
                            "enqueue",
                            DownloadManager.Request.class);
            hook(enqueue).intercept(chain -> {
                try {
                    JSONObject config = config();
                    if (!config.optBoolean("enabled", true)) {
                        return chain.proceed();
                    }
                    Object arg = chain.getArg(0);
                    if (arg instanceof DownloadManager.Request) {
                        applyRequestPatch(
                                (DownloadManager.Request) arg,
                                config);
                    }
                } catch (Throwable error) {
                    logSafely(
                            Log.WARN,
                            "Download callback failed open",
                            error);
                }
                return chain.proceed();
            });
            logSafely(
                    Log.INFO,
                    "DownloadManager patch ready for " + packageName,
                    null);
        } catch (Throwable error) {
            logSafely(
                    Log.WARN,
                    "DownloadManager patch unavailable for " + packageName,
                    error);
        }
    }

    private void applyRequestPatch(
            DownloadManager.Request request,
            JSONObject config) {
        try {
            if (!config.optBoolean("allowMetered", true)) {
                request.setAllowedOverMetered(false);
            }
            if (!config.optBoolean("allowRoaming", true)) {
                request.setAllowedOverRoaming(false);
            }
            if (config.optBoolean(
                    "forceCompletionNotification",
                    false)) {
                request.setNotificationVisibility(
                        DownloadManager.Request
                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (config.optBoolean("requireCharging", false)) {
                    request.setRequiresCharging(true);
                }
                if (config.optBoolean("requireDeviceIdle", false)) {
                    request.setRequiresDeviceIdle(true);
                }
            }
        } catch (Throwable error) {
            logSafely(
                    Log.WARN,
                    "Request patch partially skipped",
                    error);
        }
    }

    private JSONObject config() {
        try {
            SharedPreferences prefs =
                    getRemotePreferences(PREFS);
            return new JSONObject(
                    prefs.getString(KEY_CONFIG, "{}"));
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private void logSafely(
            int priority,
            String message,
            Throwable error) {
        try {
            if (error == null) {
                log(priority, TAG, message);
            } else {
                log(priority, TAG, message, error);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onHotReloading(
            HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(
            HotReloadedParam param) {
        try {
            param.getOldHookHandles().forEach(handle -> {
                try {
                    handle.unhook();
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {
        } finally {
            installed.clear();
        }
    }
}
