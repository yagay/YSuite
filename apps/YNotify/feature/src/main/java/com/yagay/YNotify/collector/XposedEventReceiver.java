package com.yagay.YNotify.collector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.yagay.YNotify.data.CapturePolicy;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.util.AppInfoUtil;
import com.yagay.YNotify.util.DiagLog;
import com.yagay.YNotify.util.HookAuth;

import java.util.concurrent.ConcurrentHashMap;

public class XposedEventReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.yagay.YNotify.XPOSED_EVENT";
    public static final String KIND_UI = "ui";
    public static final String KIND_HEADS_UP = "heads_up";
    public static final String KIND_BUBBLE = "bubble";
    public static final String KIND_FULL_SCREEN = "full_screen";

    private static final ConcurrentHashMap<String, Long> RECENT_NONCES = new ConcurrentHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;

        String pkg = safe(intent.getStringExtra("package"));
        String kind = safe(intent.getStringExtra("kind"));
        String type = safe(intent.getStringExtra("type"));
        String text = clamp(safe(intent.getStringExtra("text")), 16_384);
        String className = clamp(safe(intent.getStringExtra("class")), 512);
        String notificationKey = clamp(safe(intent.getStringExtra("notification_key")), 2048);
        String nonce = safe(intent.getStringExtra("nonce"));
        String signature = safe(intent.getStringExtra("signature"));
        long time = intent.getLongExtra("time", 0L);

        if (kind.isEmpty()) kind = KIND_UI;
        long now = System.currentTimeMillis();
        if (time <= 0 || Math.abs(now - time) > 60_000L || nonce.isEmpty()) {
            DiagLog.w(context, "XposedReceiver", "rejected stale/malformed event pkg=" + pkg + " kind=" + kind);
            return;
        }
        if (!validPackage(pkg)) {
            DiagLog.w(context, "XposedReceiver", "rejected invalid package");
            return;
        }

        String secret = HookAuth.localSecret(context);
        if (!HookAuth.verify(secret, signature, pkg, kind, type, text, className, notificationKey, time, nonce)) {
            DiagLog.w(context, "XposedReceiver", "HMAC verification failed pkg=" + pkg + " kind=" + kind);
            return;
        }

        Long replay = RECENT_NONCES.putIfAbsent(nonce, now);
        if (replay != null) {
            DiagLog.w(context, "XposedReceiver", "replay nonce rejected pkg=" + pkg);
            return;
        }
        if (RECENT_NONCES.size() > 500) {
            RECENT_NONCES.entrySet().removeIf(e -> now - e.getValue() > 120_000L);
        }

        if (KIND_HEADS_UP.equals(kind)) {
            if (!notificationKey.isEmpty()) {
                DiagLog.i(context, "XposedReceiver", "heads-up pkg=" + pkg + " key=" + notificationKey);
                EventStore.markHeadsUp(context, notificationKey, time);
            }
            return;
        }
        if (KIND_BUBBLE.equals(kind)) {
            if (!notificationKey.isEmpty()) {
                DiagLog.i(context, "XposedReceiver", "bubble pkg=" + pkg + " key=" + notificationKey);
                EventStore.markBubble(context, notificationKey, time);
            }
            return;
        }
        if (KIND_FULL_SCREEN.equals(kind)) {
            if (!notificationKey.isEmpty()) {
                DiagLog.i(context, "XposedReceiver", "full-screen pkg=" + pkg + " key=" + notificationKey);
                EventStore.markFullScreen(context, notificationKey, time);
            }
            return;
        }

        if (!allowedType(type) || text.isEmpty() || context.getPackageName().equals(pkg)
                || CapturePolicy.isIgnored(context, pkg)) return;

        EventRecord r = new EventRecord();
        r.eventType = type;
        r.source = "lsposed";
        r.packageName = pkg;
        r.appLabel = AppInfoUtil.label(context, pkg);
        r.title = intent.getStringExtra("title");
        r.text = text;
        r.fullText = text;
        r.className = className;
        r.postedAt = time;
        r.updatedAt = now;
        r.eventKey = "xposed:" + pkg + ":" + type + ":" + time + ":" + Integer.toHexString(text.hashCode());
        if (CapturePolicy.isRedacted(context, pkg)) CapturePolicy.redact(r);
        DiagLog.i(context, "XposedReceiver", "accepted type=" + type + " pkg=" + pkg + " class=" + className);
        EventStore.save(context, r);
    }

    private static boolean allowedType(String type) {
        return EventTypes.TOAST.equals(type)
                || EventTypes.DIALOG.equals(type)
                || EventTypes.SNACKBAR.equals(type)
                || EventTypes.POPUP.equals(type)
                || EventTypes.SYSTEM_UI.equals(type)
                || EventTypes.OTHER_UI.equals(type);
    }

    private static boolean validPackage(String pkg) {
        return !pkg.isEmpty() && pkg.length() <= 255 && pkg.matches("[A-Za-z0-9_.$]+");
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String clamp(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
}
