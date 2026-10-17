package com.yagay.YFloat;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.util.UUID;

/** YFloat-owned trigger for Google Circle to Search. MiCTS is referenced only for this layer. */
final class GoogleCtsTrigger {
    private static final int CTS_SHOW_FLAGS = 7;
    // Google 17.66 can finish cold-start DEX discovery AFTER 5 seconds.
    // A missing acknowledgement is diagnostic only, never grounds to hide CTS.
    private static final long HOOK_ACK_DIAGNOSTIC_MS = 12_000L;
    private static volatile String acknowledgedHookToken = "";

    static void onOwnedHookReady(Context context,String token,String detail) {
        if(context==null||token==null||token.isBlank()
                ||!WorkflowSessionManager.matchesExternal(token))return;
        acknowledgedHookToken=token;
        DiagnosticLog.i(context,"GOOGLE_CTS_HOOK_ACK",
                "strict intercept ready session="
                        +token.substring(0,Math.min(8,token.length()))
                        +" detail="+(detail==null?"":detail));
    }

    /**
     * An unverified callback during cold start is not a failed Hook.
     * Google runs PackageReady and semantic DEX discovery after launching.
     * Do not terminate the voice/Lens Activity before those hooks get a chance
     * to activate. Keep the regular token-scoped lifecycle and 120s watchdog.
     */
    private static void reportPendingHook(Context app,
            WorkflowSessionManager.Session workflow,String expectedToken) {
        if(!GoogleCtsHookAckPolicy.shouldReportPending(
                workflow.current()&&WorkflowSessionManager.matchesExternal(expectedToken),
                expectedToken,acknowledgedHookToken))return;
        DiagnosticLog.i(app,"GOOGLE_CTS_HOOK_ACK",
                "startup_pending session="
                        +expectedToken.substring(0,Math.min(8,expectedToken.length()))
                        +" not_force_closed=true"
                        +" googleLoaded="+LsposedStatusManager.snapshot().googleTargetLoaded());
    }

    static boolean trigger(Context c) {
        if (c == null) return false;
        Context app = c.getApplicationContext();
        if (WorkflowSessionManager.googleCtsInFlight()) {
            WorkflowSessionManager.Session previous = WorkflowSessionManager.current();
            DiagnosticLog.i(app, "GOOGLE_CTS_TRIGGER",
                    "explicit retrigger: release previous CTS state="
                            + (previous == null ? "none" : previous.phase())
                            + " session="
                            + (previous == null || previous.externalKey().isBlank()
                                    ? "none" : previous.externalKey().substring(
                                            0, Math.min(8, previous.externalKey().length()))));
            // A new user gesture is an explicit retry, not proof the old Google
            // activity is healthy. Clear YFloat/remote leases before re-arming.
            if(previous!=null)
                GoogleV2UiEngine.abandon(app,previous.externalKey(),"user_retry");
            GoogleCtsBridgeController.onNativeRelease(app, "user_retry");
        }

        FloatSettings fs = new FloatSettings(app);
        // An APK replacement restarts the Google process. The next PackageReady
        // installs freshly resolved hooks; this records the new APK identity.
        GoogleHookVersionTracker.observe(app);
        if (!fs.enhancedMode() || !fs.lsposedEnabled()) {
            Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a72460296503), Toast.LENGTH_SHORT).show();
            return false;
        }
        LsposedStatusManager.Snapshot status = LsposedStatusManager.snapshot();
        if (!status.serviceConnected || !status.remoteConfigReady
                || !status.googleScopeEnabled()) {
            Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_ec24d8acdb75), Toast.LENGTH_LONG).show();
            return false;
        }
        if (HookReloadManager.googleNeedsReload(app, status)) {
            DiagnosticLog.i(app, "GOOGLE_CTS_TRIGGER",
                    "hook fingerprint changed; hot reload before CTS");
            boolean started = HookReloadManager.reloadGoogleForCtsAsync(app, result -> {
                DiagnosticLog.i(app, "HOOK_RELOAD",
                        "google auto success=" + result.success
                                + " detail=" + result.detail);
                if (!result.success) {
                    Toast.makeText(app, result.userMessage(app), Toast.LENGTH_LONG).show();
                    return;
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> trigger(app), 280L);
            });
            if (!started) {
                Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_df9545bb902d), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_3f751dc7d85c),
                        Toast.LENGTH_SHORT).show();
            }
            // The gesture is handled; never fall back to native YFloat circle during reload.
            return true;
        }

        Bundle args = new Bundle();
        String token = UUID.randomUUID().toString();
        acknowledgedHookToken="";
        WorkflowSessionManager.Session workflow = WorkflowSessionManager.beginExternal(
                app, WorkflowSessionManager.Type.GOOGLE_CTS, token, "google_cts_trigger");
        WorkflowSessionManager.transition(app, workflow,
                WorkflowSessionManager.Phase.CAPTURING, "google_cts_trigger");
        // A pending launcher navigation event from the *previous* Google
        // session must never revoke the token we are about to arm.
        LensAccessibilityService.onNewGoogleCtsWorkflow(workflow);
        long nowElapsed = SystemClock.elapsedRealtime();
        fs.armGoogleCtsSession(token, nowElapsed + GoogleCtsContract.TRACE_SESSION_TTL_MS);
        long fallbackUntil = nowElapsed + LsposedRuntimeConfig.GOOGLE_CTS_FALLBACK_WINDOW_MS;
        boolean remoteArmed = LsposedStatusManager.armGoogleCtsSessionRemote(
                token, nowElapsed, fallbackUntil);
        DiagnosticLog.i(app, "GOOGLE_CTS_ARM",
                "remote=" + remoteArmed + " session=" + token.substring(0, 8)
                        + " fallbackMs=" + LsposedRuntimeConfig.GOOGLE_CTS_FALLBACK_WINDOW_MS);
        if (!remoteArmed) {
            fs.clearGoogleCtsSession();
            WorkflowSessionManager.fail(app, workflow, "google_remote_arm_failed");
            DiagnosticLog.i(app, "GOOGLE_CTS_ARM", "missing remote ownership lease; local fallback");
            return false;
        }
        args.putLong(GoogleCtsContract.K_INVOCATION_TIME, nowElapsed);
        args.putInt(GoogleCtsContract.K_OMNI_ENTRY_POINT,
                LsposedRuntimeConfig.GOOGLE_CTS_EXPECTED_OMNI_ENTRY_POINT);
        args.putBoolean(GoogleCtsContract.K_TRIGGER, true);
        args.putString(GoogleCtsContract.K_SESSION_TOKEN, token);

        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Object rawBinder = HiddenApiBypass.invoke(
                    serviceManager, null, "getService", "voiceinteraction");
            if (!(rawBinder instanceof IBinder binder)) {
                throw new IllegalStateException("voiceinteraction binder unavailable");
            }
            Class<?> stub = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService$Stub");
            Object vims = HiddenApiBypass.invoke(stub, null, "asInterface", binder);
            Class<?> iface = Class.forName(
                    "com.android.internal.app.IVoiceInteractionManagerService");

            Object result;
            try {
                result = HiddenApiBypass.invoke(iface, vims, "showSessionFromSession",
                        null, args, CTS_SHOW_FLAGS, "yfloat");
            } catch (NoSuchMethodException older) {
                result = HiddenApiBypass.invoke(iface, vims, "showSessionFromSession",
                        null, args, CTS_SHOW_FLAGS);
            }
            boolean ok = Boolean.TRUE.equals(result);
            DiagnosticLog.i(app, "GOOGLE_CTS_TRIGGER",
                    "result=" + ok + " session=" + token.substring(0, 8)
                            + " entryPoint="
                            + LsposedRuntimeConfig.GOOGLE_CTS_EXPECTED_OMNI_ENTRY_POINT
                            + " flags=" + CTS_SHOW_FLAGS);
            if (!ok) {
                fs.clearGoogleCtsSession();
                LsposedStatusManager.clearGoogleCtsSessionRemote(token);
                WorkflowSessionManager.fail(app, workflow, "google_cts_start_failed");
                Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_63d7415102df), Toast.LENGTH_SHORT).show();
            } else {
                WorkflowSessionManager.transition(app, workflow,
                        WorkflowSessionManager.Phase.SELECTING, "google_cts_visible");
                new Handler(Looper.getMainLooper()).postDelayed(
                        ()->reportPendingHook(app,workflow,token),
                        HOOK_ACK_DIAGNOSTIC_MS);
                // Google can accept showSessionFromSession while its LSPosed
                // callbacks are missing. No bridge event means no State is ever
                // created, so the normal State TTL cannot release SELECTING.
                // Bound the top-level workflow by the same Google CTS lease.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!workflow.current() || !WorkflowSessionManager.googleCtsInFlight())
                        return;
                    DiagnosticLog.i(app, "GOOGLE_CTS_TRIGGER",
                            "session watchdog expired; no terminal CTS event session="
                                    + token.substring(0, 8));
                    GoogleCtsBridgeController.onNativeRelease(app, "session_watchdog");
                }, GoogleCtsContract.TRACE_SESSION_TTL_MS);
            }
            return ok;
        } catch (Throwable t) {
            fs.clearGoogleCtsSession();
            LsposedStatusManager.clearGoogleCtsSessionRemote(token);
            WorkflowSessionManager.fail(app, workflow, "google_cts_exception");
            DiagnosticLog.i(app, "GOOGLE_CTS_TRIGGER",
                    "failed=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()));
            Toast.makeText(app, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_def9b880703d) + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private GoogleCtsTrigger() {}
}
