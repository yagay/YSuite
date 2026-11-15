package com.yagay.YMiniGuard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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
        session.setActive(true);
    }

    void publish(boolean audioFallback) {
        PlaybackState state = source.getPlaybackState();
        if ((state == null && !audioFallback) || notificationManager == null) return;
        MediaMetadata sourceMetadata = source.getMetadata();
        String title = sourceMetadata == null ? null
                : sourceMetadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (title == null || title.isEmpty())
            title = sourceMetadata == null ? null
                    : sourceMetadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null || title.isEmpty()) title = appLabel();
        String artist = sourceMetadata == null ? null
                : sourceMetadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null || artist.isEmpty())
            artist = audioFallback ? "Background audio · metadata unavailable" : appLabel();
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
        Bitmap artwork = null;
        if (sourceMetadata != null) {
            if (sourceMetadata.containsKey(MediaMetadata.METADATA_KEY_DURATION))
                meta.putLong(MediaMetadata.METADATA_KEY_DURATION,
                        sourceMetadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            artwork = sourceMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (artwork == null)
                artwork = sourceMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (artwork != null && !artwork.isRecycled()) {
                if (artwork.getWidth() > 256 || artwork.getHeight() > 256)
                    artwork = Bitmap.createScaledBitmap(artwork, 256, 256, true);
                meta.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork);
            }
        }
        session.setMetadata(meta.build());
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
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true);
        PendingIntent activity = source.getSessionActivity();
        if (activity == null) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(
                    source.getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity = PendingIntent.getActivity(context, 300, launch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
        }
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
