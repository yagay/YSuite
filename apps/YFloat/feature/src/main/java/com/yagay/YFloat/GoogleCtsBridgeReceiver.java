package com.yagay.YFloat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.SystemClock;

/** Authenticated small-event channel for one YFloat-owned Google Circle-to-Search session. */
public final class GoogleCtsBridgeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null
                || !GoogleCtsContract.ACTION_BRIDGE.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(
                FloatSettings.PREF, Context.MODE_PRIVATE);
        String expected = prefs.getString(FloatSettings.K_GOOGLE_CTS_ACTIVE_SESSION, "");
        long validUntil = prefs.getLong(FloatSettings.K_GOOGLE_CTS_ACTIVE_UNTIL, 0L);
        String token = intent.getStringExtra(GoogleCtsContract.EXTRA_BRIDGE_SESSION);
        if (!GoogleCtsContract.isAuthorizedTrace(
                expected, validUntil, token, SystemClock.elapsedRealtime())) return;

        String event = safe(intent.getStringExtra(GoogleCtsContract.EXTRA_BRIDGE_EVENT), 80);
        String text = safe(intent.getStringExtra(GoogleCtsContract.EXTRA_BRIDGE_TEXT), 20_000);
        String detail = safe(intent.getStringExtra(GoogleCtsContract.EXTRA_BRIDGE_DETAIL), 8_000);

        Rect bounds = null;
        int left = intent.getIntExtra(GoogleCtsContract.EXTRA_LEFT, Integer.MIN_VALUE);
        int top = intent.getIntExtra(GoogleCtsContract.EXTRA_TOP, Integer.MIN_VALUE);
        int right = intent.getIntExtra(GoogleCtsContract.EXTRA_RIGHT, Integer.MIN_VALUE);
        int bottom = intent.getIntExtra(GoogleCtsContract.EXTRA_BOTTOM, Integer.MIN_VALUE);
        if (left != Integer.MIN_VALUE && top != Integer.MIN_VALUE
                && right > left && bottom > top) {
            bounds = new Rect(left, top, right, bottom);
        }

        // Bound a variable-length drawing payload to 128 text rows.
        int[] rows = intent.getIntArrayExtra(GoogleCtsContract.EXTRA_TEXT_ROWS);
        if(rows!=null&&(rows.length<4||rows.length>512||rows.length%4!=0))
            rows=null;

        if (GoogleCtsContract.EVENT_SELECTION.equals(event)
                || GoogleCtsContract.EVENT_REGION_SELECTION.equals(event))
            GoogleCtsSessionCoordinator.selecting(token);
        switch (event) {
            case GoogleCtsContract.EVENT_HOOK_READY ->
                    GoogleCtsSessionCoordinator.hookReady(context,token,detail);
            case GoogleCtsContract.EVENT_FRAME_READY ->
                    GoogleCtsBridgeProvider.consumeSharedFrame(context, token);
            case GoogleCtsContract.EVENT_SELECTION -> {
                // OCR data must not depend on the Google selection overlay
                // being visible or a screenshot SharedMemory frame arriving
                // first. The authenticated exact-token bridge owns the data.
                if(GoogleLensImageInputProbe.isOcrEngineRequest(token)
                        &&!text.isBlank())
                    GoogleLensImageInputProbe.onNativeText(
                            context.getApplicationContext(),token,text,bounds);
                GoogleV2UiEngine.onText(context, token, text, bounds, detail, rows);
            }
            case GoogleCtsContract.EVENT_TEXT_BOUNDS ->
                    GoogleV2UiEngine.onTextBounds(context, token, bounds, rows);
            case GoogleCtsContract.EVENT_TEXT_GESTURE_START ->
                    GoogleV2UiEngine.onTextGesture(context, token, true);
            case GoogleCtsContract.EVENT_TEXT_GESTURE_END ->
                    GoogleV2UiEngine.onTextGesture(context, token, false);
            case GoogleCtsContract.EVENT_REGION_SELECTION ->
                    GoogleV2UiEngine.onRegion(context, token, bounds, detail);
            case GoogleCtsContract.EVENT_REGION_GESTURE_START ->
                    GoogleV2UiEngine.onRegionGesture(context, token, true);
            case GoogleCtsContract.EVENT_REGION_GESTURE_END ->
                    GoogleV2UiEngine.onRegionGesture(context, token, false);
            case GoogleCtsContract.EVENT_COMMIT,
                 GoogleCtsContract.EVENT_QUERY_RESULT ->
                    GoogleV2UiEngine.onCommit(context, token, detail);
            case GoogleCtsContract.EVENT_TEXT_MENU_COMMIT -> {
                if(GoogleLensImageInputProbe.isOcrEngineRequest(token)
                        &&!text.isBlank())
                    GoogleLensImageInputProbe.onNativeText(
                            context.getApplicationContext(),token,text,bounds);
                GoogleV2UiEngine.onText(context, token, text, bounds, detail);
            }
            case GoogleCtsContract.EVENT_VISUAL_EXIT ->
                    GoogleV2UiEngine.onNavigationExit(context, token, "hook:"+detail);
            case GoogleCtsContract.EVENT_END ->
                    GoogleV2UiEngine.onEnd(context, token, detail);
            default -> DiagnosticLog.i(context, "GOOGLE_V2",
                    "unknown event=" + event + " session=" + shortToken(token));
        }
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        String out = value.replace("\u0000", "?");
        return out.length() <= max ? out : out.substring(0, max);
    }

    private static String shortToken(String token) {
        if (token == null || token.isBlank()) return "none";
        return token.substring(0, Math.min(8, token.length()));
    }
}
