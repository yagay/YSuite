package com.yagay.YMiniGuard;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.app.NotificationManager;
import android.media.AudioManager;
import android.os.SystemClock;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The host's single notification listener observes real MediaSessions for
 * user-selected background-playback packages. YMini never guesses media titles,
 * duration, position or playback state and never shadows a native media card.
 */
public final class YMiniMediaBridge {
    private static final String TAG = "YMiniMedia";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static NotificationListenerService host;
    private static MediaSessionManager manager;
    private static MediaController source;
    private static YMiniMediaCard card;
    private static boolean registered;
    private static boolean fallbackPlayback;
    private static String lastDiagnostic = "Listener not connected";
    private static long lastRebindElapsed;
    private static final Runnable FALLBACK_REFRESH = () -> update();
    private static final Set<String> nativeMediaPackages = new HashSet<>();

    private static final MediaController.Callback CALLBACK = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            MAIN.post(YMiniMediaBridge::update);
        }
        @Override public void onMetadataChanged(android.media.MediaMetadata metadata) {
            MAIN.post(YMiniMediaBridge::update);
        }
        @Override public void onSessionDestroyed() {
            MAIN.post(YMiniMediaBridge::update);
        }
    };
    private static final MediaSessionManager.OnActiveSessionsChangedListener
            SESSIONS = sessions -> MAIN.post(YMiniMediaBridge::update);

    private YMiniMediaBridge() {}

    public static void onListenerConnected(NotificationListenerService listener) {
        MAIN.post(() -> {
            if (listener == null) return;
            if (host != listener) detach();
            host = listener;
            manager = listener.getSystemService(MediaSessionManager.class);
            if (manager == null) return;
            try {
                if (!registered) {
                    manager.addOnActiveSessionsChangedListener(SESSIONS,
                            new ComponentName(listener, listener.getClass()), MAIN);
                    registered = true;
                }
                scanNative();
                update();
                Log.i(TAG, "LISTENER_READY component=" +
                        listener.getClass().getName() + " bridgeEnabled=" + enabled(listener));
            } catch (Throwable error) {
                lastDiagnostic = "Listener registration failed: " + error.getClass().getSimpleName();
                Log.w(TAG, "Notification listener access required for media bridge", error);
                stop();
            }
        });
    }

    public static void onListenerDisconnected(NotificationListenerService listener) {
        MAIN.post(() -> {
            if (host == listener) detach();
        });
    }

    public static void onNotificationPosted(NotificationListenerService listener,
            StatusBarNotification sbn, NotificationListenerService.RankingMap ranking) {
        if (sbn == null || listener.getPackageName().equals(sbn.getPackageName())) return;
        MAIN.post(() -> {
            if (host == listener) {
                scanNative();
                update();
            }
        });
    }

    public static void onNotificationRemoved(NotificationListenerService listener,
            StatusBarNotification sbn, NotificationListenerService.RankingMap ranking,
            int reason) {
        if (sbn == null || listener.getPackageName().equals(sbn.getPackageName())) return;
        MAIN.post(() -> {
            if (host == listener) {
                scanNative();
                update();
            }
        });
    }

    /** Re-evaluate when user changes selected apps or returns to the settings screen. */
    public static void refresh(Context context) {
        MAIN.post(() -> {
            if (host == null && context != null) requestRebind(context);
            update();
        });
    }

    private static ComponentName listenerComponent(Context context) {
        if ("com.yagay.YSuite".equals(context.getPackageName()))
            return new ComponentName(context.getPackageName(),
                    "com.yagay.YSuite.notification.SuiteNotificationListenerService");
        return new ComponentName(context, YMiniMediaListenerService.class);
    }

    private static boolean hasAccess(Context context) {
        try {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            return nm != null && nm.isNotificationListenerAccessGranted(
                    listenerComponent(context));
        } catch (Throwable error) {
            return false;
        }
    }

    private static void requestRebind(Context context) {
        if (!hasAccess(context)) {
            lastDiagnostic = "No notification listener permission";
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastRebindElapsed < 10_000L) return;
        lastRebindElapsed = now;
        try {
            NotificationListenerService.requestRebind(listenerComponent(context));
            lastDiagnostic = "Permission granted; requesting listener rebind";
            Log.i(TAG, "LISTENER_REBIND_REQUESTED");
        } catch (Throwable error) {
            lastDiagnostic = "Listener rebind failed: " + error.getClass().getSimpleName();
            Log.w(TAG, "LISTENER_REBIND_FAILED", error);
        }
    }

    /** User-readable fault isolation; no root logs required to check notification access. */
    public static String status(Context context) {
        if (context == null) return "Context unavailable";
        if (!enabled(context)) return "Media bridge disabled";
        if (!hasAccess(context))
            return "Notification access denied: enable " + context.getPackageName()
                    + " in Notification access settings";
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null && !nm.areNotificationsEnabled())
            return "App notifications disabled: allow notifications for " + context.getPackageName();
        if (host == null) return "Notification access granted, listener not connected";
        if (!registered) return "Listener connected, media observer not registered";
        if (source != null) return "Media card: " + source.getPackageName()
                + (fallbackPlayback ? " [audio fallback]" : " [MediaSession]")
                + (card == null ? "" : " · " + card.infoStatus());
        return "Listener connected; " + lastDiagnostic;
    }

    private static boolean enabled(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ConfigKeys.MASTER_ENABLED, true)
                && context.getSharedPreferences(ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ConfigKeys.MEDIA_BRIDGE_ENABLED, true);
    }

    private static Set<String> selectedPackages(Context context) {
        Set<String> selected = new HashSet<>();
        if (context == null) return selected;
        Set<String> background = context.getSharedPreferences(
                ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getStringSet(ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES, null);
        Set<String> foreground = context.getSharedPreferences(
                ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getStringSet(ConfigKeys.FOREGROUND_PACKAGES, null);
        if (background != null) selected.addAll(background);
        if (foreground != null) selected.addAll(foreground);
        return selected;
    }

    private static void scanNative() {
        nativeMediaPackages.clear();
        if (host == null) return;
        try {
            StatusBarNotification[] notifications = host.getActiveNotifications();
            if (notifications == null) return;
            for (StatusBarNotification notification : notifications) {
                if (notification == null || host.getPackageName()
                        .equals(notification.getPackageName())) continue;
                Notification value = notification.getNotification();
                if (value != null && value.extras != null
                        && value.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                    nativeMediaPackages.add(notification.getPackageName());
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Native media card discovery failed", error);
        }
    }

    private static void update() {
        if (host == null || manager == null || !enabled(host)) {
            stop();
            return;
        }
        Set<String> selected = selectedPackages(host);
        if (selected.isEmpty()) {
            stop();
            return;
        }
        try {
            List<MediaController> controllers = manager.getActiveSessions(
                    new ComponentName(host, host.getClass()));
            MediaController candidate = null;
            MediaController unreported = null;
            Set<String> unreportedPackages = new HashSet<>();
            for (MediaController value : controllers) {
                String pkg = value.getPackageName();
                if (pkg == null || pkg.equals(host.getPackageName())
                        || !selected.contains(pkg) || nativeMediaPackages.contains(pkg))
                    continue;
                PlaybackState state = value.getPlaybackState();
                if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING)) {
                    candidate = value; // Prefer the device's genuine PlaybackState.
                    break;
                }
                if (state == null || state.getState() == PlaybackState.STATE_NONE) {
                    // OOS17/RedFruit advertises two active sessions with state=null.
                    // Never guess which app is playing if multiple eligible packages
                    // have null-state sessions; require unambiguous selected owner.
                    unreportedPackages.add(pkg);
                    if (unreported == null) unreported = value;
                }
            }
            boolean legacy = false;
            if (candidate == null && unreported != null
                    && unreportedPackages.size() == 1) {
                AudioManager audio = host.getSystemService(AudioManager.class);
                // This is a *global* signal; the unique session owner above is
                // essential to avoid attributing another app's playback.
                if (MediaFallbackPolicy.shouldActivate(unreportedPackages.size(),
                        audio != null && audio.isMusicActive())) {
                    candidate = unreported;
                    legacy = true;
                }
            }
            if (candidate == null) {
                lastDiagnostic = unreportedPackages.isEmpty()
                        ? "No selected app with active MediaSession"
                        : "MediaSession has no state; audio inactive or ambiguous";
                stop();
                // A session may be created before its AudioTrack starts.
                // The OEM's state=null session emits no PlaybackState callback,
                // so a bounded poll is necessary even when music is still idle.
                if (!unreportedPackages.isEmpty())
                    MAIN.postDelayed(FALLBACK_REFRESH, 3500L);
                return;
            }
            if (source == null || !candidate.getSessionToken()
                    .equals(source.getSessionToken())) {
                stop();
                source = candidate;
                source.registerCallback(CALLBACK, MAIN);
                card = new YMiniMediaCard(host, source);
                Log.i(TAG, "BRIDGE_SESSION package=" + candidate.getPackageName()
                        + " mode=" + (legacy ? "audio-fallback" : "native-state"));
            }
            fallbackPlayback = legacy;
            lastDiagnostic = legacy ? "Fallback audio active" : "Real MediaSession active";
            if (card != null) card.publish(legacy);
            MAIN.removeCallbacks(FALLBACK_REFRESH);
            if (legacy) MAIN.postDelayed(FALLBACK_REFRESH, 3500L);
        } catch (SecurityException denied) {
            Log.w(TAG, "MEDIA_ACCESS_DENIED: enable YSuite notification access", denied);
            stop();
        } catch (Throwable error) {
            Log.w(TAG, "Media bridge refresh failed", error);
            stop();
        }
    }

    static void command(Context context, String target, int code) {
        MAIN.post(() -> {
            if (source == null || !enabled(context)
                    || target == null || !target.equals(source.getPackageName())) return;
            Set<String> selected = selectedPackages(context);
            if (!selected.contains(target)) return;
            PlaybackState state = source.getPlaybackState();
            if (state == null || state.getState() == PlaybackState.STATE_NONE) {
                // No advertised PlaybackState: use the original session's
                // media-button receiver instead of unverified transport calls.
                int keyCode = code == 1 ? android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                        : code == 2 ? android.view.KeyEvent.KEYCODE_MEDIA_PAUSE : 0;
                if (keyCode == 0) return;
                boolean delivered = dispatchMediaButton(source, keyCode);
                Log.i(TAG, "LEGACY_MEDIA_BUTTON pkg=" + target
                        + " command=" + code + " delivered=" + delivered);
                MAIN.removeCallbacks(FALLBACK_REFRESH);
                MAIN.postDelayed(FALLBACK_REFRESH, 750L);
                return;
            }
            long required = code == 1 ? PlaybackState.ACTION_PLAY
                    : code == 2 ? PlaybackState.ACTION_PAUSE
                    : code == 3 ? PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    : code == 4 ? PlaybackState.ACTION_SKIP_TO_NEXT : 0L;
            if (required == 0 || (state.getActions() & required) == 0) return;
            try {
                MediaController.TransportControls controls = source.getTransportControls();
                switch (code) {
                    case 1: controls.play(); break;
                    case 2: controls.pause(); break;
                    case 3: controls.skipToPrevious(); break;
                    case 4: controls.skipToNext(); break;
                    default: break;
                }
            } catch (Throwable error) {
                Log.w(TAG, "Source media command failed", error);
            }
        });
    }

    static boolean dispatchMediaButton(MediaController controller, int keyCode) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            boolean down = controller.dispatchMediaButtonEvent(
                    new android.view.KeyEvent(now, now,
                            android.view.KeyEvent.ACTION_DOWN, keyCode, 0));
            boolean up = controller.dispatchMediaButtonEvent(
                    new android.view.KeyEvent(now, now,
                            android.view.KeyEvent.ACTION_UP, keyCode, 0));
            return down && up;
        } catch (Throwable error) {
            Log.w(TAG, "Legacy media button dispatch failed", error);
            return false;
        }
    }

    private static void stop() {
        MAIN.removeCallbacks(FALLBACK_REFRESH);
        fallbackPlayback = false;
        if (source != null) {
            try { source.unregisterCallback(CALLBACK); } catch (Throwable ignored) {}
        }
        if (card != null) card.close();
        source = null;
        card = null;
    }

    private static void detach() {
        stop();
        if (manager != null && registered) {
            try { manager.removeOnActiveSessionsChangedListener(SESSIONS); }
            catch (Throwable ignored) {}
        }
        registered = false;
        manager = null;
        host = null;
        lastDiagnostic = "Listener disconnected";
        nativeMediaPackages.clear();
    }
}
