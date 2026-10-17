package com.yagay.YFloat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

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
    private static final String K_LAST_SCAN_TOAST = "last_scan_toast";
    private static final String K_MANUAL_PENDING = "manual_scan_pending";
    private static final String K_MANUAL_APK = "manual_scan_apk";
    private static final String K_MANUAL_FINGERPRINT = "manual_scan_fingerprint";
    private static final String K_MANUAL_STARTED = "manual_scan_started_elapsed";
    private static final String K_LAST_SCAN_RESULT = "last_scan_result";
    private static final String K_LAST_SCAN_DETAIL = "last_scan_detail";
    private static final String K_LAST_SCAN_COUNT = "last_scan_count";
    private static final String K_LAST_SCAN_APK = "last_scan_apk";
    private static final long MANUAL_SCAN_TIMEOUT_MS = 45_000L;
    private static final int MAX_SYMBOL_BYTES = 2048;

    static Bundle handle(Context context, String method, Bundle extras) {
        Bundle result = new Bundle();
        if (context == null || extras == null || !authorizedGoogleUid(context)) return result;
        String identity = extras.getString(GoogleCtsContract.EXTRA_HOOK_APK_IDENTITY, "");
        String fingerprint = extras.getString(GoogleCtsContract.EXTRA_HOOK_FINGERPRINT, "");
        if (identity.isBlank() || !identity.equals(installedIdentity(context))
                || !BuildConfig.GOOGLE_HOOK_FINGERPRINT.equals(fingerprint)) return result;
        SharedPreferences cache = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (GoogleCtsContract.METHOD_REPORT_HOOK_SCAN.equals(method)) {
            boolean success=extras.getBoolean(GoogleCtsContract.EXTRA_HOOK_SCAN_SUCCESS,false);
            int count=Math.max(0,Math.min(250,extras.getInt(
                    GoogleCtsContract.EXTRA_HOOK_SCAN_COUNT,0)));
            String raw=extras.getString(GoogleCtsContract.EXTRA_HOOK_SCAN_METHOD,"");
            String detail=sanitize(raw,75);
            boolean manual = manualPending(cache, identity, fingerprint)
                    && !manualTimedOut(cache);
            String noticeId=identity+"|"+fingerprint+"|"+success
                    +(manual ? "|manual" : "");
            DiagnosticLog.i(context,"GOOGLE_HOOK_SCAN","finished version="
                    +identity+" success="+success+" hooks="+count+" detail="+detail);
            cache.edit().putString(K_LAST_SCAN_APK,identity)
                    .putString(K_LAST_SCAN_RESULT,success?"success":"failed")
                    .putString(K_LAST_SCAN_DETAIL,detail)
                    .putInt(K_LAST_SCAN_COUNT,count).commit();
            // One result Toast per APK, matcher version, and result transition.
            // Cache hits do not reach this call, so app/Google process restart
            // does not repeatedly display notifications.
            if(!noticeId.equals(cache.getString(K_LAST_SCAN_TOAST,""))) {
                if(cache.edit().putString(K_LAST_SCAN_TOAST,noticeId).commit()) {
                    String version=identity.split("/",2)[0];
                    int titleRes=success?R.string.yfloat_google_scan_toast_success
                            :R.string.yfloat_google_scan_toast_failure;
                    String message=success
                            ? context.getString(titleRes,version,count,detail)
                            : context.getString(titleRes,version,detail);
                    notifyUser(context,message);
                }
            }
            if (manual) clearPending(cache);
            result.putBoolean("reported",true);
            return result;
        }
        if (GoogleCtsContract.METHOD_READ_HOOK_PROFILE.equals(method)) {
            result.putBoolean(GoogleCtsContract.EXTRA_HOOK_SCAN_MANUAL,
                    manualPending(cache, identity, fingerprint));
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

    /** Called only after a user requests a manual re-scan. No root operation here. */
    static boolean beginManualScan(Context context) {
        if(context==null)return false;
        Context app=context.getApplicationContext();
        String identity=installedIdentity(app);
        if(identity.isBlank())return false;
        SharedPreferences prefs=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        expirePending(app);
        if(manualPending(prefs,identity,BuildConfig.GOOGLE_HOOK_FINGERPRINT))
            return false;
        boolean started=prefs.edit()
                .putBoolean(K_MANUAL_PENDING,true)
                .putString(K_MANUAL_APK,identity)
                .putString(K_MANUAL_FINGERPRINT,BuildConfig.GOOGLE_HOOK_FINGERPRINT)
                .putLong(K_MANUAL_STARTED,SystemClock.elapsedRealtime())
                .remove(K_LAST_SCAN_TOAST)
                .remove(K_LAST_SCAN_RESULT).remove(K_LAST_SCAN_DETAIL).commit();
        if(started) new Handler(Looper.getMainLooper()).postDelayed(
                () -> expirePending(app),MANUAL_SCAN_TIMEOUT_MS);
        return started;
    }

    static void cancelManualScan(Context context) {
        if(context==null)return;
        clearPending(context.getApplicationContext().getSharedPreferences(
                PREF,Context.MODE_PRIVATE));
    }

    static boolean manualScanPending(Context context) {
        if(context==null)return false;
        Context app=context.getApplicationContext();
        expirePending(app);
        return manualPending(app.getSharedPreferences(PREF,Context.MODE_PRIVATE),
                installedIdentity(app),BuildConfig.GOOGLE_HOOK_FINGERPRINT);
    }

    private static boolean manualTimedOut(SharedPreferences p) {
        return GoogleHookVersionPolicy.scanTimedOut(
                p.getLong(K_MANUAL_STARTED,0L),
                SystemClock.elapsedRealtime(),MANUAL_SCAN_TIMEOUT_MS);
    }

    private static void clearPending(SharedPreferences p) {
        p.edit().remove(K_MANUAL_PENDING).remove(K_MANUAL_APK)
                .remove(K_MANUAL_FINGERPRINT).remove(K_MANUAL_STARTED).commit();
    }

    private static void expirePending(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(!p.getBoolean(K_MANUAL_PENDING,false) || !manualTimedOut(p))return;
        String apk=p.getString(K_MANUAL_APK,"");
        clearPending(p);
        // Scope enabled is not equivalent to the Google process actually
        // reporting its module load. Surface that distinction in diagnostics.
        LsposedStatusManager.Snapshot scope = LsposedStatusManager.snapshot();
        boolean loaded = scope != null && scope.googleTargetLoaded();
        String detail=context.getString(loaded
                ? R.string.yfloat_google_scan_timeout_detail
                : R.string.yfloat_google_scan_timeout_unloaded);
        p.edit().putString(K_LAST_SCAN_APK,apk)
                .putString(K_LAST_SCAN_RESULT,"timeout")
                .putString(K_LAST_SCAN_DETAIL,detail)
                .putInt(K_LAST_SCAN_COUNT,0).commit();
        DiagnosticLog.i(context,"GOOGLE_HOOK_SCAN",
                "manual scan timed out apk="+apk+" result_not_received"
                        +" googleHookReportedLoaded="+loaded
                        +" googleScopeEnabled="+(scope != null && scope.googleScopeEnabled()));
        String version=apk.split("/",2)[0];
        notifyUser(context,context.getString(R.string.yfloat_google_scan_toast_failure,
                version,detail));
    }

    public static String scanStatus(Context context) {
        if(context==null)return "";
        Context app=context.getApplicationContext();
        expirePending(app);
        SharedPreferences p=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String identity=installedIdentity(app);
        if(manualPending(p,identity,BuildConfig.GOOGLE_HOOK_FINGERPRINT))
            return app.getString(R.string.yfloat_google_manual_scan_waiting);
        if(!identity.equals(p.getString(K_LAST_SCAN_APK,"")))return "";
        String result=p.getString(K_LAST_SCAN_RESULT,"");
        if(result.isBlank())return "";
        return app.getString(R.string.yfloat_google_scan_status,
                result,p.getInt(K_LAST_SCAN_COUNT,0),
                p.getString(K_LAST_SCAN_DETAIL,""));
    }

    private static void notifyUser(Context context,String message) {
        Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { Toast.makeText(app,message,Toast.LENGTH_LONG).show(); }
            catch(Throwable t) {
                DiagnosticLog.i(app,"GOOGLE_HOOK_SCAN",
                        "Toast error="+t.getClass().getSimpleName());
            }
        });
    }

    private static boolean manualPending(SharedPreferences p,String apk,String fingerprint) {
        return p.getBoolean(K_MANUAL_PENDING,false)
                && apk.equals(p.getString(K_MANUAL_APK,""))
                && fingerprint.equals(p.getString(K_MANUAL_FINGERPRINT,""));
    }

    public static String status(Context context, String currentIdentity) {
        if (context == null) return "unavailable";
        Context app=context.getApplicationContext();
        expirePending(app);
        SharedPreferences p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if(manualPending(p, currentIdentity==null?"":currentIdentity,
                BuildConfig.GOOGLE_HOOK_FINGERPRINT)) return "manual scan pending";
        return currentIdentity != null
                && currentIdentity.equals(p.getString(K_APK, ""))
                && BuildConfig.GOOGLE_HOOK_FINGERPRINT.equals(
                        p.getString(K_FINGERPRINT, ""))
                && !p.getString(K_SYMBOLS, "").isBlank()
                ? "cached (method revalidation required)" : "not matched for installed APK";
    }

    private static String sanitize(String value,int max) {
        if(value==null)return "";
        String safe=value.replace('\n',' ').replace('\r',' ')
                .replace('\0',' ').trim();
        return safe.length()>max?safe.substring(0,max):safe;
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
