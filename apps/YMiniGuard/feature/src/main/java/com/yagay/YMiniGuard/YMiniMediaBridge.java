package com.yagay.YMiniGuard;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
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
                Log.i(TAG, "LISTENER_READY");
            } catch (Throwable error) {
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

    /** Re-evaluate immediately when selected apps or the switch changes. */
    public static void refresh(Context context) {
        MAIN.post(YMiniMediaBridge::update);
    }

    private static boolean enabled(Context context) {
        if (context == null) return false;
        return context.getSharedPreferences(ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ConfigKeys.MASTER_ENABLED, true)
                && context.getSharedPreferences(ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getBoolean(ConfigKeys.MEDIA_BRIDGE_ENABLED, true);
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
        Set<String> selected = host.getSharedPreferences(
                ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                .getStringSet(ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES, null);
        if (selected == null || selected.isEmpty()) {
            stop();
            return;
        }
        try {
            List<MediaController> controllers = manager.getActiveSessions(
                    new ComponentName(host, host.getClass()));
            MediaController candidate = null;
            for (MediaController value : controllers) {
                String pkg = value.getPackageName();
                if (pkg == null || pkg.equals(host.getPackageName())
                        || !selected.contains(pkg) || nativeMediaPackages.contains(pkg))
                    continue;
                PlaybackState state = value.getPlaybackState();
                if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING)) {
                    candidate = value; // MediaSessionManager returns priority order.
                    break;
                }
            }
            if (candidate == null) {
                stop();
                return;
            }
            if (source == null || !candidate.getSessionToken()
                    .equals(source.getSessionToken())) {
                stop();
                source = candidate;
                source.registerCallback(CALLBACK, MAIN);
                card = new YMiniMediaCard(host, source);
                Log.i(TAG, "BRIDGE_SESSION package=" + candidate.getPackageName());
            }
            if (card != null) card.publish();
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
            Set<String> selected = context.getSharedPreferences(
                    ConfigKeys.LOCAL_PREFS, Context.MODE_PRIVATE)
                    .getStringSet(ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES, null);
            if (selected == null || !selected.contains(target)) return;
            PlaybackState state = source.getPlaybackState();
            if (state == null) return;
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

    private static void stop() {
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
        nativeMediaPackages.clear();
    }
}
