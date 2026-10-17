package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

/**
 * App-side APK revision observer. It never writes into the Google process.
 * Android recreates that process when its APK is replaced; PackageReady
 * then resolves fresh Method objects instead of reusing prior mappings.
 */
final class GoogleHookVersionTracker {
    private static final String PREF = "yfloat_google_hook_versions_v1";
    private static final String KEY_IDENTITY = "last_apk_identity";

    static String observe(Context context) {
        if (context == null) return "unavailable";
        Context app = context.getApplicationContext();
        String current = currentIdentity(app);
        if ("unavailable".equals(current)) return current;
        SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String previous = prefs.getString(KEY_IDENTITY, "");
        if (!current.equals(previous)) {
            prefs.edit().putString(KEY_IDENTITY, current).apply();
            DiagnosticLog.i(app, "GOOGLE_HOOK_APK",
                    "identity=" + current
                            + " changed=" + GoogleHookVersionPolicy.changed(previous, current)
                            + " action=rebind_on_next_google_package_ready");
        }
        return current;
    }

    static GoogleHookVersionPolicy.Compatibility compatibility(Context context) {
        return GoogleHookVersionPolicy.compatibility(versionName(context));
    }

    private static String versionName(Context context) {
        if (context == null) return "";
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(GoogleCtsContract.GOOGLE_PACKAGE, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable unavailable) {
            return "";
        }
    }

    static String status(Context context) {
        String version = observe(context);
        LsposedStatusManager.Snapshot snapshot = LsposedStatusManager.snapshot();
        boolean scoped = snapshot != null && snapshot.googleScopeEnabled();
        boolean ready = snapshot != null && snapshot.remoteProviderEnabled();
        boolean loaded = snapshot != null && snapshot.googleTargetLoaded();
        String compatibilityStatus = switch (compatibility(context)) {
            case BASELINE_1758 -> context.getString(R.string.yfloat_google_1758_baseline);
            case KNOWN_BAD -> context.getString(R.string.yfloat_google_known_bad_version);
            case EXPERIMENTAL -> context.getString(R.string.yfloat_google_unverified_version);
        };
        return "Google APK: " + version + "\n" + compatibilityStatus
                + "\nLSPosed scope: " + (scoped ? "enabled" : "missing")
                + " · provider: " + (ready ? "enabled" : "unavailable")
                + " · process: " + (loaded ? "loaded" : "not reported")
                + "\nStructural Hook binding is checked in the next Google process."
                + " Loaded does not mean the selection methods have been verified.";
    }

    private static String currentIdentity(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(GoogleCtsContract.GOOGLE_PACKAGE, 0);
            return GoogleHookVersionPolicy.identity(info.versionName,
                    info.getLongVersionCode(), info.lastUpdateTime);
        } catch (Throwable unavailable) {
            return "unavailable";
        }
    }

    private GoogleHookVersionTracker() {}
}
