package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;

import java.util.function.Consumer;

/**
 * Explicit user-initiated one-shot Hook rescan. A settings-side class cannot
 * scan Google's obfuscated classes in YFloat's ClassLoader; it must restart
 * Google's scoped process, which runs PackageReady in the correct ClassLoader.
 */
final class GoogleHookManualScan {
    private GoogleHookManualScan() {}

    static void start(Context context, Consumer<String> finished) {
        Context app=context.getApplicationContext();
        FloatSettings settings=new FloatSettings(app);
        if(!settings.enhancedMode() || !settings.lsposedEnabled()) {
            respond(finished,app.getString(R.string.yfloat_google_manual_scan_unavailable));
            return;
        }
        LsposedStatusManager.Snapshot snapshot=LsposedStatusManager.snapshot();
        if(snapshot==null || !snapshot.serviceConnected || !snapshot.remoteConfigReady
                || !snapshot.googleScopeEnabled()) {
            respond(finished,app.getString(R.string.yfloat_google_manual_scan_unavailable));
            return;
        }
        if(!settings.canUseRoot()) {
            respond(finished,app.getString(R.string.yfloat_google_manual_scan_missing_root));
            return;
        }
        if(!GoogleHookProfileCache.beginManualScan(app)) {
            respond(finished,app.getString(R.string.yfloat_google_manual_scan_busy));
            return;
        }

        DiagnosticLog.i(app,"GOOGLE_HOOK_SCAN","manual_requested apk="
                +GoogleHookVersionTracker.observe(app));
        boolean started=HookReloadManager.reloadGoogleForCtsAsync(app,result->{
            if(!result.success) {
                GoogleHookProfileCache.manualFailure(app,result.detail);
                respond(finished,app.getString(R.string.yfloat_google_manual_scan_reload_failed,
                        result.detail.isBlank()?result.userMessage(app):result.detail));
                return;
            }
            GoogleHookProfileCache.hostStage(app,"launching");
            Intent launch=app.getPackageManager().getLaunchIntentForPackage(
                    GoogleCtsContract.GOOGLE_PACKAGE);
            if(launch==null) {
                GoogleHookProfileCache.manualFailure(app,
                        app.getString(R.string.yfloat_google_manual_scan_launch_failed));
                respond(finished,app.getString(R.string.yfloat_google_manual_scan_launch_failed));
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                app.startActivity(launch);
                GoogleHookProfileCache.hostStage(app,"waiting");
                respond(finished,app.getString(R.string.yfloat_google_manual_scan_started));
            } catch(Throwable t) {
                GoogleHookProfileCache.manualFailure(app,
                        app.getString(R.string.yfloat_google_manual_scan_launch_failed));
                DiagnosticLog.i(app,"GOOGLE_HOOK_SCAN","Google launch failed="
                        +t.getClass().getSimpleName());
                respond(finished,app.getString(R.string.yfloat_google_manual_scan_launch_failed));
            }
        });
        if(!started){
            GoogleHookProfileCache.cancelManualScan(app);
            respond(finished,app.getString(R.string.yfloat_google_manual_scan_busy));
        }
    }

    private static void respond(Consumer<String> callback,String message) {
        if(callback!=null)callback.accept(message);
    }
}
