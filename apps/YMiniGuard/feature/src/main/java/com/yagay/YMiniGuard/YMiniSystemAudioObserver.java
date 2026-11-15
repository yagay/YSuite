package com.yagay.YMiniGuard;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;

/**
 * Runs solely inside YMini's already-scoped system_server engine.
 *
 * No additional app, SystemUI, AudioService, or OEM method is Hooked.
 * AudioManager delivers privileged playback configurations to this system
 * context; only user-selected packages' active UID/usage are persisted
 * through YMini's existing system-UID-only status provider.
 */
final class YMiniSystemAudioObserver {
    private static final String TAG = "YMiniAudioUid";
    private static final long REFRESH_MS = 6_000L;
    private static final long REPUBLISH_MS = 8_000L;

    private final Handler handler;
    private final Context context;
    private final AudioManager audio;
    private final PackageManager packages;
    private boolean registered;
    private boolean stopped;
    private String lastPayload = "";
    private long lastPublishElapsed;
    private final AudioManager.AudioPlaybackCallback callback =
            new AudioManager.AudioPlaybackCallback() {
                @Override
                public void onPlaybackConfigChanged(
                        List<AudioPlaybackConfiguration> configs) {
                    publish(configs);
                }
            };
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    YMiniSystemAudioObserver(Handler handler, Context context) {
        this.handler = handler;
        this.context = context;
        this.audio = context == null ? null : context.getSystemService(AudioManager.class);
        this.packages = context == null ? null : context.getPackageManager();
    }

    void start() {
        if (stopped || audio == null || context == null || handler == null) {
            Log.w(TAG, "UID_AUDIO_OBSERVER_UNAVAILABLE missing system context/audio");
            return;
        }
        // AudioService can start after ActivityTaskManager during boot.
        // Register on the shared handler and retry without restarting LSPosed.
        handler.post(heartbeat);
    }

    void stop() {
        stopped = true;
        if (handler != null) handler.removeCallbacks(heartbeat);
        if (audio != null && registered) {
            try { audio.unregisterAudioPlaybackCallback(callback); }
            catch (Throwable error) {
                Log.w(TAG, "UID_AUDIO_OBSERVER_UNREGISTER_FAILED", error);
            }
        }
        registered = false;
    }

    private void refresh() {
        if (stopped || audio == null) return;
        if (!registered) {
            try {
                audio.registerAudioPlaybackCallback(callback, handler);
                registered = true;
                Log.i(TAG, "UID_AUDIO_OBSERVER_REGISTERED system_server");
            } catch (Throwable error) {
                Log.w(TAG, "UID_AUDIO_OBSERVER_REGISTER_FAILED", error);
                send("unavailable", "");
                return;
            }
        }
        try {
            publish(audio.getActivePlaybackConfigurations());
        } catch (Throwable error) {
            Log.w(TAG, "UID_AUDIO_OBSERVER_REFRESH_FAILED", error);
            send("unavailable", "");
        }
    }

    private void publish(List<AudioPlaybackConfiguration> configurations) {
        if (stopped || context == null || packages == null) return;
        if (!GuardConfig.enabled() || !GuardConfig.bool(ConfigKeys.MEDIA_BRIDGE_ENABLED)) {
            send("disabled", "");
            return;
        }
        Set<String> selected = new HashSet<>(
                GuardConfig.stringSet(ConfigKeys.FOREGROUND_PACKAGES));
        selected.addAll(GuardConfig.stringSet(ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES));
        if (selected.isEmpty()) {
            send("ready", "");
            return;
        }
        Map<Integer, String> activeUids = new TreeMap<>();
        boolean someUidUnknown = false;
        if (configurations != null) {
            for (AudioPlaybackConfiguration playback : configurations) {
                if (playback == null || !playback.isActive()) continue;
                int uid = clientUid(playback);
                if (uid < 10000) {
                    // Some OEM audio players may be anonymized; a negative
                    // per-app result is then not conclusive.
                    someUidUnknown = true;
                    continue;
                }
                String[] names;
                try { names = packages.getPackagesForUid(uid); }
                catch (Throwable ignored) { names = null; }
                if (names == null || names.length == 0) {
                    someUidUnknown = true;
                    continue;
                }
                if (names.length > 1) {
                    // Shared UIDs cannot identify which of those packages
                    // actually created the audio track. Never assign the
                    // entire shared UID to just one selected package.
                    someUidUnknown = true;
                    continue;
                }
                boolean selectedOwner = false;
                for (String name : names) {
                    if (selected.contains(name)) {
                        selectedOwner = true;
                        break;
                    }
                }
                if (!selectedOwner) continue;
                int usage = AudioAttributes.USAGE_UNKNOWN;
                try {
                    AudioAttributes attrs = playback.getAudioAttributes();
                    if (attrs != null) usage = attrs.getUsage();
                } catch (Throwable ignored) {}
                int contentType = AudioAttributes.CONTENT_TYPE_UNKNOWN;
                try {
                    AudioAttributes attrs = playback.getAudioAttributes();
                    if (attrs != null) contentType = attrs.getContentType();
                } catch (Throwable ignored) {}
                activeUids.put(uid, usage + ":" + contentType);
            }
        }
        StringBuilder entries = new StringBuilder();
        for (Map.Entry<Integer, String> e : activeUids.entrySet()) {
            if (entries.length() != 0) entries.append(',');
            entries.append(e.getKey()).append(':').append(e.getValue());
        }
        send(someUidUnknown ? "partial" : "ready", entries.toString());
    }

    /** Avoid referring to hidden SDK classes or requiring expanded LSPosed scopes. */
    private static int clientUid(AudioPlaybackConfiguration config) {
        try {
            Method method = AudioPlaybackConfiguration.class.getMethod("getClientUid");
            Object result = method.invoke(config);
            return result instanceof Number ? ((Number) result).intValue() : -1;
        } catch (Throwable error) {
            return -1;
        }
    }

    private void send(String state, String uidUsages) {
        if (context == null || stopped) return;
        String payload = state + "|" + uidUsages;
        long now = SystemClock.elapsedRealtime();
        if (payload.equals(lastPayload) && now - lastPublishElapsed < REPUBLISH_MS)
            return;
        lastPayload = payload;
        lastPublishElapsed = now;
        Bundle extras = new Bundle();
        extras.putString(EngineStatusProvider.KEY_AUDIO_STATE, state);
        extras.putString(EngineStatusProvider.KEY_AUDIO_UIDS, uidUsages);
        try {
            Bundle response = context.getContentResolver().call(
                    EngineStatusProvider.URI,
                    EngineStatusProvider.METHOD_AUDIO, null, extras);
            if (response == null || !response.getBoolean("ok")) {
                Log.w(TAG, "UID_AUDIO_OBSERVER_STATUS_WRITE_FAILED state=" + state);
            } else {
                Log.i(TAG, "UID_AUDIO_SNAPSHOT state=" + state
                        + " selectedActive=" + (uidUsages.isEmpty() ? 0
                        : uidUsages.split(",").length));
            }
        } catch (Throwable error) {
            Log.w(TAG, "UID_AUDIO_OBSERVER_PROVIDER_FAILED", error);
        }
    }
}
