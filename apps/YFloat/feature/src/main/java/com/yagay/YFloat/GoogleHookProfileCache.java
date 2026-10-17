package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Bundle;

/**
 * Host-only persistent cache of Google Lens symbol matches.
 *
 * Hooked Google processes cannot edit libxposed Remote Preferences. This
 * Provider handler accepts only the Google app UID, checks the *installed*
 * APK identity and current YFloat Hook source fingerprint, then stores a
 * small validated symbol descriptor in the YFloat/YSuite host preferences.
 *
 * These are *matched symbols*, never proof the Hook was actually invoked.
 */
public final class GoogleHookProfileCache {
    private static final String PREF = "yfloat_google_abi_profile_v1";
    private static final String K_APK = "apk";
    private static final String K_FINGERPRINT = "hook_fingerprint";
    private static final String K_SYMBOLS = "matched_symbols";
    private static final int MAX_SYMBOL_BYTES = 2048;

    static Bundle handle(Context context, String method, Bundle extras) {
        Bundle result = new Bundle();
        if (context == null || extras == null || !authorizedGoogleUid(context)) return result;
        String identity = extras.getString(GoogleCtsContract.EXTRA_HOOK_APK_IDENTITY, "");
        String fingerprint = extras.getString(GoogleCtsContract.EXTRA_HOOK_FINGERPRINT, "");
        if (identity.isBlank() || !identity.equals(installedIdentity(context))
                || !BuildConfig.GOOGLE_HOOK_FINGERPRINT.equals(fingerprint)) return result;
        SharedPreferences cache = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (GoogleCtsContract.METHOD_READ_HOOK_PROFILE.equals(method)) {
            if (identity.equals(cache.getString(K_APK, ""))
                    && fingerprint.equals(cache.getString(K_FINGERPRINT, ""))) {
                result.putString(GoogleCtsContract.EXTRA_HOOK_SYMBOLS,
                        cache.getString(K_SYMBOLS, ""));
            }
        } else if (GoogleCtsContract.METHOD_WRITE_HOOK_PROFILE.equals(method)) {
            String symbols = extras.getString(GoogleCtsContract.EXTRA_HOOK_SYMBOLS, "");
            if (symbols.length() > 0 && symbols.length() <= MAX_SYMBOL_BYTES
                    && symbols.indexOf('\n') < 0 && symbols.indexOf('\r') < 0) {
                boolean saved = cache.edit()
                        .putString(K_APK, identity)
                        .putString(K_FINGERPRINT, fingerprint)
                        .putString(K_SYMBOLS, symbols).commit();
                result.putBoolean("saved", saved);
                if (saved) DiagnosticLog.i(context, "GOOGLE_HOOK_CACHE",
                        "symbols saved for APK=" + identity);
            }
        }
        return result;
    }

    public static String status(Context context, String currentIdentity) {
        if (context == null) return "unavailable";
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return currentIdentity != null
                && currentIdentity.equals(p.getString(K_APK, ""))
                && BuildConfig.GOOGLE_HOOK_FINGERPRINT.equals(
                        p.getString(K_FINGERPRINT, ""))
                && !p.getString(K_SYMBOLS, "").isBlank()
                ? "cached (method revalidation required)" : "not matched for installed APK";
    }

    private static String installedIdentity(Context context) {
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(
                    GoogleCtsContract.GOOGLE_PACKAGE,0);
            return GoogleHookVersionPolicy.identity(info.versionName,
                    info.getLongVersionCode(),info.lastUpdateTime);
        } catch (Throwable ignored) { return ""; }
    }

    private static boolean authorizedGoogleUid(Context context) {
        try {
            String[] names=context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
            if (names != null) for(String name:names)
                if (GoogleCtsContract.GOOGLE_PACKAGE.equals(name)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    private GoogleHookProfileCache() {}
}
