package com.yagay.YMiniGuard;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.LruCache;
import java.util.List;

/**
 * Generic, source-attributed media presentation:
 * session metadata > actual media notification > current queue item > app label.
 * Only a genuine media notification is eligible: ordinary push notifications must
 * never become a video title, artwork, or a lockscreen privacy disclosure.
 */
final class YMiniMediaInfo {
    static final class Result {
        String title;
        String subtitle;
        Bitmap artwork;
        long duration = -1L;
        String textSource = "app";
        String imageSource = "none";
    }

    private static final LruCache<String, Bitmap> APP_ICONS = new LruCache<>(8);
    private static final int SIZE = 240;

    private YMiniMediaInfo() {}

    static Result collect(Context context, MediaController controller) {
        Result result = new Result();
        if (context == null || controller == null) return result;
        String pkg = controller.getPackageName();
        result.title = appLabel(context, pkg);
        result.subtitle = "Background playback";

        MediaMetadata metadata = controller.getMetadata();
        if (metadata != null) {
            String title = first(
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
            String subtitle = first(
                    metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                    metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
            MediaDescription desc = metadata.getDescription();
            if (desc != null) {
                title = first(title, desc.getTitle());
                subtitle = first(subtitle, desc.getSubtitle(), desc.getDescription());
            }
            if (title != null) {
                result.title = title;
                result.textSource = "session";
            }
            if (subtitle != null) result.subtitle = subtitle;
            if (metadata.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
                long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
                if (duration > 0L) result.duration = duration;
            }
            result.artwork = firstBitmap(
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ART),
                    metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART));
            if (result.artwork == null && desc != null)
                result.artwork = desc.getIconBitmap();
            if (result.artwork != null) result.imageSource = "session";
        }

        // Only extract the title/artwork from a notification explicitly marked
        // as media, never from chat, login, order, push or unrelated messages.
        if (context instanceof NotificationListenerService) {
            NotificationListenerService listener = (NotificationListenerService) context;
            try {
                StatusBarNotification[] active = listener.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) {
                        if (sbn == null || !pkg.equals(sbn.getPackageName())) continue;
                        Notification n = sbn.getNotification();
                        if (n == null || !Notification.CATEGORY_TRANSPORT.equals(n.category))
                            continue;
                        Bundle extras = n.extras;
                        if (extras != null) {
                            if ("app".equals(result.textSource)) {
                                String title = first(extras.getCharSequence(Notification.EXTRA_TITLE),
                                        extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
                                if (title != null) {
                                    result.title = title;
                                    result.textSource = "media_notification";
                                }
                            }
                            if ("Background playback".equals(result.subtitle)) {
                                String subtitle = first(extras.getCharSequence(Notification.EXTRA_TEXT),
                                        extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
                                if (subtitle != null) result.subtitle = subtitle;
                            }
                        }
                        if (result.artwork == null) {
                            Icon icon = n.getLargeIcon();
                            result.artwork = icon == null ? null : asBitmap(icon.loadDrawable(context));
                            if (result.artwork != null) result.imageSource = "media_notification";
                        }
                        break;
                    }
                }
            } catch (Throwable ignored) {
                // Media card continues with the last trusted source.
            }
        }

        // Queue title is safe only if the session identifies its active queue
        // item, or if there is exactly one queue entry. Never guess the active
        // episode from the first item of a multi-episode list.
        if ("app".equals(result.textSource) || result.artwork == null) {
            try {
                List<MediaSession.QueueItem> queue = controller.getQueue();
                PlaybackState state = controller.getPlaybackState();
                long id = state == null ? MediaSession.QueueItem.UNKNOWN_ID
                        : state.getActiveQueueItemId();
                MediaDescription selected = null;
                if (queue != null) {
                    for (MediaSession.QueueItem item : queue) {
                        if (item != null && item.getQueueId() == id
                                && id != MediaSession.QueueItem.UNKNOWN_ID) {
                            selected = item.getDescription();
                            break;
                        }
                    }
                    if (selected == null && queue.size() == 1 && queue.get(0) != null)
                        selected = queue.get(0).getDescription();
                }
                if (selected != null) {
                    if ("app".equals(result.textSource)) {
                        String title = first(selected.getTitle());
                        if (title != null) {
                            result.title = title;
                            result.textSource = "session_queue";
                        }
                        String subtitle = first(selected.getSubtitle(), selected.getDescription());
                        if (subtitle != null) result.subtitle = subtitle;
                    }
                    if (result.artwork == null) {
                        result.artwork = selected.getIconBitmap();
                        if (result.artwork != null) result.imageSource = "session_queue";
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (result.artwork == null) {
            result.artwork = appIcon(context, pkg);
            if (result.artwork != null) result.imageSource = "app_icon";
        }
        if (result.artwork != null && !result.artwork.isRecycled())
            result.artwork = scale(result.artwork);
        return result;
    }

    private static String first(CharSequence... options) {
        for (CharSequence candidate : options) {
            if (candidate != null) {
                String text = candidate.toString().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return null;
    }

    private static Bitmap firstBitmap(Bitmap... images) {
        for (Bitmap image : images)
            if (image != null && !image.isRecycled()) return image;
        return null;
    }

    private static String appLabel(Context context, String pkg) {
        try {
            return context.getPackageManager().getApplicationLabel(
                    context.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private static Bitmap appIcon(Context context, String pkg) {
        Bitmap cached = APP_ICONS.get(pkg);
        if (cached != null && !cached.isRecycled()) return cached;
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(pkg);
            Bitmap bitmap = asBitmap(drawable);
            if (bitmap != null) APP_ICONS.put(pkg, bitmap);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap asBitmap(Drawable drawable) {
        if (drawable == null) return null;
        try {
            Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable = drawable.mutate();
            drawable.setBounds(0, 0, SIZE, SIZE);
            drawable.draw(canvas);
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap scale(Bitmap bitmap) {
        if (bitmap.getWidth() <= SIZE && bitmap.getHeight() <= SIZE)
            return bitmap;
        float ratio = Math.min((float) SIZE / bitmap.getWidth(),
                (float) SIZE / bitmap.getHeight());
        return Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                Math.max(1, Math.round(bitmap.getHeight() * ratio)), true);
    }
}
