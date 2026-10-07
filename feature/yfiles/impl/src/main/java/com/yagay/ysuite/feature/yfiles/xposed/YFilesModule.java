package com.yagay.ysuite.feature.yfiles.xposed;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

public final class YFilesModule extends XposedModule {
    private static final String TAG = "YFilesXposed";
    private static final String PREFS = "yfiles_patch";
    private static final String KEY_CONFIG = "config";
    private static final Set<String> HARD_EXCLUDED = Set.of(
            "android",
            "com.android.systemui",
            "com.android.providers.media",
            "com.google.android.gms",
            "com.android.vending"
    );
    private final ConcurrentHashMap<String, Boolean> installed = new ConcurrentHashMap<>();

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
            if (pkg == null || pkg.startsWith("com.yagay.ysuite")) return;

            if (looksLikeDocumentsUi(pkg) || hasDocumentsUiSortModel(param.getClassLoader())) {
                installed.computeIfAbsent(pkg, ignored -> {
                    installDocumentsUiSortPatch(param.getClassLoader(), pkg);
                    return Boolean.TRUE;
                });
                return;
            }

            if (HARD_EXCLUDED.contains(pkg)) return;
            installed.computeIfAbsent(pkg, ignored -> {
                installPickerIntentPatch(pkg);
                return Boolean.TRUE;
            });
        } catch (Throwable error) {
            logSafely(Log.WARN, "Package-ready patch skipped safely", error);
        }
    }

    private boolean looksLikeDocumentsUi(String packageName) {
        String normalized = packageName.toLowerCase(Locale.ROOT);
        return "com.android.documentsui".equals(packageName)
                || "com.google.android.documentsui".equals(packageName)
                || normalized.contains("documentsui")
                || normalized.endsWith(".documentsui");
    }

    private boolean hasDocumentsUiSortModel(ClassLoader loader) {
        try {
            Class.forName("com.android.documentsui.sorting.SortModel", false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void installPickerIntentPatch(String packageName) {
        safeIntentHook(
                "Activity.startActivity(Intent)",
                method(Activity.class, "startActivity", Intent.class));
        safeIntentHook(
                "Activity.startActivity(Intent,Bundle)",
                method(Activity.class, "startActivity", Intent.class, Bundle.class));
        safeIntentHook(
                "Activity.startActivityForResult(Intent,int)",
                method(Activity.class, "startActivityForResult", Intent.class, int.class));
        safeIntentHook(
                "Activity.startActivityForResult(Intent,int,Bundle)",
                method(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class));
        logSafely(Log.INFO, "SAF picker request patch ready for " + packageName, null);
    }

    private void installDocumentsUiSortPatch(ClassLoader loader, String packageName) {
        try {
            Class<?> sortModel =
                    Class.forName("com.android.documentsui.sorting.SortModel", false, loader);
            Method method = sortModel.getDeclaredMethod("setDefaultDimension", int.class);
            int sortName = sortModel.getField("SORT_DIMENSION_ID_TITLE").getInt(null);
            int sortDate = sortModel.getField("SORT_DIMENSION_ID_DATE").getInt(null);
            int sortSize = sortModel.getField("SORT_DIMENSION_ID_SIZE").getInt(null);
            int sortType = sortModel.getField("SORT_DIMENSION_ID_FILE_TYPE").getInt(null);

            hook(method).intercept(chain -> {
                try {
                    JSONObject config = config();
                    if (!config.optBoolean("enabled", true)) return chain.proceed();
                    String selected = config.optString("defaultSort", "system");
                    int target = switch (selected) {
                        case "name" -> sortName;
                        case "date" -> sortDate;
                        case "size" -> sortSize;
                        case "type" -> sortType;
                        default -> 0;
                    };
                    if (target != 0) {
                        return chain.proceed(new Object[]{target});
                    }
                } catch (Throwable error) {
                    logSafely(Log.WARN, "DocumentsUI sort patch failed open", error);
                }
                return chain.proceed();
            });
            logSafely(Log.INFO, "DocumentsUI sort patch ready for " + packageName, null);
        } catch (Throwable error) {
            logSafely(Log.WARN, "DocumentsUI sort patch unavailable", error);
        }
    }

    private Method method(Class<?> owner, String name, Class<?>... types) {
        try {
            return owner.getDeclaredMethod(name, types);
        } catch (Throwable error) {
            return null;
        }
    }

    private void safeIntentHook(String label, Method method) {
        if (method == null) return;
        try {
            hook(method).intercept(chain -> {
                try {
                    Object arg = chain.getArg(0);
                    if (arg instanceof Intent) {
                        patchIntent((Intent) arg);
                    }
                } catch (Throwable error) {
                    logSafely(Log.WARN, label + " failed open", error);
                }
                return chain.proceed();
            });
        } catch (Throwable error) {
            logSafely(Log.WARN, "Skip hook " + label, error);
        }
    }

    private void patchIntent(Intent intent) {
        JSONObject config = config();
        if (!config.optBoolean("enabled", true)) return;

        String action = intent.getAction();
        boolean open = Intent.ACTION_OPEN_DOCUMENT.equals(action);
        boolean get = Intent.ACTION_GET_CONTENT.equals(action);
        boolean tree = Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action);
        boolean create = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        if (!open && !get && !tree && !create) return;

        if (config.optBoolean("localOnly", false)) {
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        }
        if ((open || get) && config.optBoolean("allowMultiple", false)) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        String initialUri = config.optString("initialUri", "");
        if ((open || tree || create)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !initialUri.isBlank()) {
            try {
                intent.putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse(initialUri));
            } catch (Throwable error) {
                logSafely(Log.WARN, "Invalid initial URI; keeping system default", error);
            }
        }
    }

    private JSONObject config() {
        try {
            SharedPreferences prefs = getRemotePreferences(PREFS);
            return new JSONObject(prefs.getString(KEY_CONFIG, "{}"));
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private void logSafely(int priority, String message, Throwable error) {
        try {
            if (error == null) log(priority, TAG, message);
            else log(priority, TAG, message, error);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
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
