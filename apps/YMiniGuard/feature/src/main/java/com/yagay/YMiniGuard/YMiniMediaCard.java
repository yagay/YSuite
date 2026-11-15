package com.yagay.YMiniGuard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.util.Log;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** One short-lived, real-source-backed MediaStyle card. Never fabricates progress. */
final class YMiniMediaCard {
    private static final int NOTIFICATION_ID = 835104;
    private static final String CHANNEL = "ymini_media_bridge";
    private final Context context;
    private final MediaController source;
    private final MediaSession session;
    private final NotificationManager notificationManager;
    private String lastInfoSource = "";
    private String infoStatus = "Metadata unchecked";
    private String launchStatus = "unresolved";

    YMiniMediaCard(Context context, MediaController source) {
        this.context = context;
        this.source = source;
        notificationManager = context.getSystemService(NotificationManager.class);
        session = new MediaSession(context, "YMiniMediaBridge");
        session.setCallback(new MediaSession.Callback() {
            private boolean allowed(long action) {
                PlaybackState state = source.getPlaybackState();
                return state != null && (state.getActions() & action) != 0;
            }
            @Override public void onPlay() {
                if (allowed(PlaybackState.ACTION_PLAY)) source.getTransportControls().play();
                else if (source.getPlaybackState() == null)
                    YMiniMediaBridge.dispatchMediaButton(source, KeyEvent.KEYCODE_MEDIA_PLAY);
            }
            @Override public void onPause() {
                if (allowed(PlaybackState.ACTION_PAUSE)) source.getTransportControls().pause();
                else if (source.getPlaybackState() == null)
                    YMiniMediaBridge.dispatchMediaButton(source, KeyEvent.KEYCODE_MEDIA_PAUSE);
            }
            @Override public void onSkipToPrevious() {
                if (allowed(PlaybackState.ACTION_SKIP_TO_PREVIOUS))
                    source.getTransportControls().skipToPrevious();
            }
            @Override public void onSkipToNext() {
                if (allowed(PlaybackState.ACTION_SKIP_TO_NEXT))
                    source.getTransportControls().skipToNext();
            }
            @Override public void onSeekTo(long position) {
                if (allowed(PlaybackState.ACTION_SEEK_TO))
                    source.getTransportControls().seekTo(position);
            }
        }, new Handler(Looper.getMainLooper()));
        // Android 13+ SystemUI opens the MediaSession's session activity,
        // not necessarily Notification.contentIntent. Install this *before*
        // making the mirror discoverable to avoid caching the host activity.
        updateSessionActivity();
        session.setActive(true);
    }

    void publish(boolean audioFallback) {
        PlaybackState state = source.getPlaybackState();
        if ((state == null && !audioFallback) || notificationManager == null) return;
        YMiniMediaInfo.Result info = YMiniMediaInfo.collect(context, source);
        String title = info.title;
        String artist = info.subtitle;
        Bitmap artwork = info.artwork;
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
        if (info.duration > 0)
            meta.putLong(MediaMetadata.METADATA_KEY_DURATION, info.duration);
        if (artwork != null && !artwork.isRecycled()) {
            // Metadata is mirrored for Android SystemUI. When the real player
            // has no artwork, show its installed app icon instead of an empty
            // media tile. This is a visual fallback, not a claimed cover image.
            meta.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, artwork);
            meta.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork);
        }
        session.setMetadata(meta.build());
        infoStatus = "title=" + info.textSource + ", image=" + info.imageSource
                + ", duration=" + (info.duration > 0 ? "available" : "unknown");
        if (!infoStatus.equals(lastInfoSource)) {
            lastInfoSource = infoStatus;
            Log.i("YMiniMedia", "METADATA_SOURCES package=" + source.getPackageName()
                    + " " + infoStatus);
        }
        long available = state == null ? 0 : state.getActions();
        long forwarded = available & (PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SKIP_TO_NEXT);
        if (audioFallback) {
            // The source did not report a position/duration. Do not display
            // a fabricated seek bar. The play/pause command is a media button
            // sent to the *original* session's registered receiver.
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .setActions(PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY)
                    .build());
        } else {
            session.setPlaybackState(new PlaybackState.Builder()
                    .setState(state.getState(), state.getPosition(),
                            state.getPlaybackSpeed(), state.getLastPositionUpdateTime())
                    .setActions(forwarded)
                    .setBufferedPosition(state.getBufferedPosition())
                    .build());
        }

        notificationManager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "YMini media", NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(sourceAppSmallIcon())
                .setContentTitle(title)
                .setContentText(artist)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);
        // Both notification and Android 13+ media controls must have the
        // same target-app Activity PendingIntent. Never fall back to YMini.
        PendingIntent activity = updateSessionActivity();
        if (activity != null) builder.setContentIntent(activity);
        if (artwork != null) builder.setLargeIcon(artwork);

        List<Integer> compact = new ArrayList<>();
        if (!audioFallback && (available & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0)
            add(builder, compact, 3, android.R.drawable.ic_media_previous, "Previous");
        if (audioFallback)
            add(builder, compact, 2, android.R.drawable.ic_media_pause, "Pause");
        else if (state.getState() == PlaybackState.STATE_PLAYING
                && (available & PlaybackState.ACTION_PAUSE) != 0)
            add(builder, compact, 2, android.R.drawable.ic_media_pause, "Pause");
        else if ((available & PlaybackState.ACTION_PLAY) != 0)
            add(builder, compact, 1, android.R.drawable.ic_media_play, "Play");
        if (!audioFallback && (available & PlaybackState.ACTION_SKIP_TO_NEXT) != 0)
            add(builder, compact, 4, android.R.drawable.ic_media_next, "Next");

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(session.getSessionToken());
        if (!compact.isEmpty()) {
            int[] indices = new int[Math.min(3, compact.size())];
            for (int i = 0; i < indices.length; i++) indices[i] = compact.get(i);
            style.setShowActionsInCompactView(indices);
        }
        builder.setStyle(style);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void add(Notification.Builder builder, List<Integer> indices,
            int command, int icon, String label) {
        Intent intent = new Intent(context, YMiniMediaActionReceiver.class)
                .setAction("com.yagay.YMiniGuard.MEDIA_ACTION")
                .putExtra("target", source.getPackageName())
                .putExtra("command", command);
        PendingIntent pending = PendingIntent.getBroadcast(context, 700 + command,
                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        indices.add(indices.size());
        builder.addAction(new Notification.Action.Builder(icon, label, pending).build());
    }

    String infoStatus() {
        return infoStatus + ", click=" + launchStatus;
    }

    /**
     * Restore a real target app task from the media notification and its
     * MediaSession. No target-app hooks or LSPosed scope changes are needed.
     *
     * Reject wrong-owner or non-Activity intents (for example a service
     * PendingIntent), otherwise the media card might launch a different app.
     */
    private PendingIntent updateSessionActivity() {
        String target = source.getPackageName();
        PendingIntent activity = null;
        String route = "unavailable";
        try {
            PendingIntent sourceActivity = source.getSessionActivity();
            if (sourceActivity != null
                    && target.equals(sourceActivity.getCreatorPackage())
                    && sourceActivity.isActivity()) {
                activity = sourceActivity;
                route = "source-session:" + target;
            }
        } catch (Throwable error) {
            Log.w("YMiniMedia", "SOURCE_ACTIVITY_CHECK_FAILED target=" + target, error);
        }

        if (activity == null) {
            try {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(target);
                if (launch != null && launch.getComponent() != null
                        && target.equals(launch.getComponent().getPackageName())) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    // Use a different PendingIntent identity for each source
                    // package. Reusing requestCode=300 could redirect a cached
                    // tile to a previous app after a media-session switch.
                    activity = PendingIntent.getActivity(context, target.hashCode(),
                            launch, PendingIntent.FLAG_UPDATE_CURRENT
                                    | PendingIntent.FLAG_IMMUTABLE);
                    route = "launcher:" + target;
                }
            } catch (Throwable error) {
                Log.w("YMiniMedia", "APP_LAUNCH_INTENT_FAILED target=" + target, error);
            }
        }

        if (!route.equals(launchStatus)) {
            launchStatus = route;
            Log.i("YMiniMedia", "MEDIA_CARD_CLICK_ROUTE route=" + route);
        }
        try {
            session.setSessionActivity(activity);
        } catch (Throwable error) {
            Log.w("YMiniMedia", "SET_SESSION_ACTIVITY_FAILED target=" + target, error);
        }
        return activity;
    }

    private Icon sourceAppSmallIcon() {
        try {
            android.content.pm.ApplicationInfo app = context.getPackageManager()
                    .getApplicationInfo(source.getPackageName(), 0);
            if (app.icon != 0)
                return Icon.createWithResource(source.getPackageName(), app.icon);
        } catch (Throwable ignored) {}
        return Icon.createWithResource("android", android.R.drawable.ic_media_play);
    }

    private String appLabel() {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(
                            source.getPackageName(), 0)).toString();
        } catch (Throwable ignored) {
            return source.getPackageName();
        }
    }

    void close() {
        try { session.setActive(false); session.release(); } catch (Throwable ignored) {}
        if (notificationManager != null) {
            try { notificationManager.cancel(NOTIFICATION_ID); } catch (Throwable ignored) {}
        }
    }
}
