package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ordered package names for the user's floating application shortcuts. */
final class ShortcutAppStore {
    private static final String PREFS = "yfloat_shortcut_apps";
    private static final String KEY = "ordered_packages";

    static List<String> load(Context context) {
        List<String> out = new ArrayList<>();
        if (context == null) return out;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                String pkg = array.optString(i, "");
                if (!pkg.isBlank() && seen.add(pkg)) out.add(pkg);
            }
        } catch (Exception e) {
            DiagnosticLog.i(context, "APP_SHORTCUTS", "load failed " + e.getClass().getSimpleName());
        }
        return out;
    }

    static void save(Context context, List<String> ordered) {
        JSONArray array = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (String pkg : ordered) {
            if (pkg != null && !pkg.isBlank() && seen.add(pkg)) array.put(pkg);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).apply();
    }

    static List<ResolveInfo> launchable(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results = pm.queryIntentActivities(query, 0);
        if (results == null) return Collections.emptyList();
        java.util.Map<String, ResolveInfo> unique = new java.util.LinkedHashMap<>();
        for (ResolveInfo info : results) {
            if (info == null || info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pm.getLaunchIntentForPackage(pkg) != null) unique.putIfAbsent(pkg, info);
        }
        ArrayList<ResolveInfo> out = new ArrayList<>(unique.values());
        out.sort((a,b) -> String.CASE_INSENSITIVE_ORDER.compare(
                String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
        return out;
    }

    static boolean launch(Context context, String pkg) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            DiagnosticLog.i(context, "APP_SHORTCUTS", "launched package=" + pkg);
            return true;
        } catch (Exception e) {
            DiagnosticLog.i(context, "APP_SHORTCUTS", "launch failed package=" + pkg
                    + " error=" + e.getClass().getSimpleName());
            return false;
        }
    }

    private ShortcutAppStore() {}
}
