package com.yagay.yfiles.xposed;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import com.yagay.suite.api.RuntimeOwnerGate;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedModule;

/**
 * Patch-first SAF integration. DocumentsUI remains the real picker and URI-grant owner.
 *
 * Two deliberately small patches are used:
 * 1) enrich caller-side SAF Intents before the original Android call continues;
 * 2) optionally override DocumentsUI's default SortModel dimension. This follows the AOSP/
 *    LineageOS call path and does not replace sorting, providers, URI grants or picker activities.
 */
public final class YFilesModule extends XposedModule {
    private static final String TAG = "YFilesXposed";
    private static final String PREFS = "yfiles_patch";
    private static final String DOCUMENTS_UI_AOSP = "com.android.documentsui";
    private static final String DOCUMENTS_UI_GOOGLE = "com.google.android.documentsui";
    private static final Set<String> HARD_EXCLUDED = Set.of(
            "android",
            "com.android.systemui",
            "com.android.providers.media",
            "com.google.android.gms",
            "com.android.vending"
    );
    private final ConcurrentHashMap<String, Boolean> installed = new ConcurrentHashMap<>();

    @Override public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Loaded in " + param.getProcessName());
    }

    @Override public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!param.isFirstPackage()) return;
        if (!RuntimeOwnerGate.shouldRun("yfiles", getModuleApplicationInfo())) return;
        String pkg = param.getPackageName();
        if (pkg == null || pkg.startsWith("com.yagay.yfiles")) return;

        if (DOCUMENTS_UI_AOSP.equals(pkg) || DOCUMENTS_UI_GOOGLE.equals(pkg)) {
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
    }

    private void installPickerIntentPatch(String packageName) {
        safeIntentHook("Activity.startActivity(Intent)", method(Activity.class, "startActivity", Intent.class));
        safeIntentHook("Activity.startActivity(Intent,Bundle)", method(Activity.class, "startActivity", Intent.class, Bundle.class));
        safeIntentHook("Activity.startActivityForResult(Intent,int)", method(Activity.class, "startActivityForResult", Intent.class, int.class));
        safeIntentHook("Activity.startActivityForResult(Intent,int,Bundle)", method(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class));
        log(Log.INFO, TAG, "SAF picker request patch ready for " + packageName);
    }

    private void installDocumentsUiSortPatch(ClassLoader classLoader, String packageName) {
        try {
            Class<?> sortModel = Class.forName("com.android.documentsui.sorting.SortModel", false, classLoader);
            Method setDefaultDimension = sortModel.getDeclaredMethod("setDefaultDimension", int.class);
            int sortName = sortModel.getField("SORT_DIMENSION_ID_TITLE").getInt(null);
            int sortDate = sortModel.getField("SORT_DIMENSION_ID_DATE").getInt(null);
            int sortSize = sortModel.getField("SORT_DIMENSION_ID_SIZE").getInt(null);
            int sortType = sortModel.getField("SORT_DIMENSION_ID_FILE_TYPE").getInt(null);

            hook(setDefaultDimension).intercept(chain -> {
                SharedPreferences prefs = getRemotePreferences(PREFS);
                if (!prefs.getBoolean("enabled", true)) return chain.proceed();
                String selected = prefs.getString("default_sort", "system");
                int target = switch (selected == null ? "system" : selected) {
                    case "name" -> sortName;
                    case "date" -> sortDate;
                    case "size" -> sortSize;
                    case "type" -> sortType;
                    default -> 0;
                };
                if (target == 0) return chain.proceed();
                return chain.proceed(new Object[]{target});
            });
            log(Log.INFO, TAG, "DocumentsUI default-sort patch ready for " + packageName);
        } catch (Throwable t) {
            // DocumentsUI is a Mainline/OEM component and may change. Missing classes/methods are
            // intentionally non-fatal: the original picker remains fully functional.
            log(Log.WARN, TAG, "DocumentsUI sort patch unavailable for " + packageName, t);
        }
    }

    private Method method(Class<?> owner, String name, Class<?>... types) {
        try { return owner.getDeclaredMethod(name, types); }
        catch (Throwable t) { return null; }
    }

    private void safeIntentHook(String name, Method method) {
        if (method == null) return;
        try {
            hook(method).intercept(chain -> {
                Object arg = chain.getArg(0);
                if (arg instanceof Intent intent) patchIntent(intent);
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Skip hook " + name, t);
        }
    }

    private void patchIntent(Intent intent) {
        SharedPreferences prefs = getRemotePreferences(PREFS);
        if (!prefs.getBoolean("enabled", true)) return;
        String action = intent.getAction();
        boolean open = Intent.ACTION_OPEN_DOCUMENT.equals(action) || Intent.ACTION_GET_CONTENT.equals(action);
        boolean tree = Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action);
        boolean create = Intent.ACTION_CREATE_DOCUMENT.equals(action);
        if (!open && !tree && !create) return;

        if (prefs.getBoolean("local_only", false)) {
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        }
        if (open && prefs.getBoolean("allow_multiple", false)) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        String initialUri = prefs.getString("initial_uri", null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null && !initialUri.isBlank()) {
            try { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(initialUri)); }
            catch (Throwable t) { log(Log.WARN, TAG, "Invalid initial URI", t); }
        }
    }

    @Override public boolean onHotReloading(@NonNull HotReloadingParam param) { return true; }

    @Override public void onHotReloaded(@NonNull HotReloadedParam param) {
        param.getOldHookHandles().forEach(HookHandle::unhook);
        installed.clear();
    }
}
