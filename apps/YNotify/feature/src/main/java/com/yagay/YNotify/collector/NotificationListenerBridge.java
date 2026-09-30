package com.yagay.YNotify.collector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.yagay.YNotify.data.CapturePolicy;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.ListenerStateStore;
import com.yagay.YNotify.util.AppInfoUtil;
import com.yagay.YNotify.util.ContentHasher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reusable NotificationListener consumer.
 *
 * Standalone YNotify delegates its NotificationListenerService here. YSuite can bind one host
 * listener for the whole APK and forward the same callbacks here, allowing future features to
 * share the single Android notification-listener grant.
 */
public final class NotificationListenerBridge {
    private static final ExecutorService CAPTURE = Executors.newSingleThreadExecutor();
    private static volatile boolean connected;

    private NotificationListenerBridge() {}

    public static void onListenerConnected(NotificationListenerService host) {
        if (host == null) return;
        connected = true;
        long now = System.currentTimeMillis();
        long disconnectedAt = ListenerStateStore.markConnected(host);
        if (disconnectedAt > 0 && now - disconnectedAt >= 5_000L) {
            EventStore.recordGap(host, disconnectedAt, now, "NotificationListener disconnected");
        }
        EventStore.cleanup(host,
                host.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .getInt("retention_days", 90));
        try {
            StatusBarNotification[] active = host.getActiveNotifications();
            NotificationListenerService.RankingMap rankingMap = host.getCurrentRanking();
            if (active != null) {
                for (StatusBarNotification sbn : active) scheduleSave(host, sbn, rankingMap);
            }
        } catch (Throwable t) {
            ListenerStateStore.markError(host, "activeNotifications", host.getPackageName(), t);
        }
    }

    public static void onListenerDisconnected(NotificationListenerService host) {
        if (host == null) return;
        connected = false;
        ListenerStateStore.markDisconnected(host);
    }

    public static boolean isConnected() {
        return connected;
    }

    public static void onNotificationPosted(
            NotificationListenerService host,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap
    ) {
        if (host == null) return;
        ListenerStateStore.markEvent(host);
        if (sbn != null) {
            ListenerStateStore.markNotificationReceived(host, sbn.getPackageName(), sbn.getKey());
        }
        scheduleSave(host, sbn, rankingMap);
    }

    public static void onNotificationRemoved(
            NotificationListenerService host,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap,
            int reason
    ) {
        if (host == null) return;
        ListenerStateStore.markEvent(host);
        if (sbn == null) return;
        long when = System.currentTimeMillis();
        String key = sbn.getKey();
        CAPTURE.execute(() -> EventStore.markRemovedBlocking(host, key, when, reason));
    }

    public static void onNotificationRankingUpdate(
            NotificationListenerService host,
            NotificationListenerService.RankingMap rankingMap
    ) {
        if (host == null) return;
        ListenerStateStore.markEvent(host);
        CAPTURE.execute(() -> {
            try {
                StatusBarNotification[] active = host.getActiveNotifications();
                if (active != null) {
                    for (StatusBarNotification sbn : active) saveBlocking(host, sbn, rankingMap);
                }
            } catch (Throwable t) {
                ListenerStateStore.markError(host, "rankingUpdate", host.getPackageName(), t);
            }
        });
    }

    private static void scheduleSave(
            NotificationListenerService host,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap
    ) {
        if (host == null || sbn == null) return;
        CAPTURE.execute(() -> saveBlocking(host, sbn, rankingMap));
    }

    private static void saveBlocking(
            NotificationListenerService host,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap
    ) {
        if (host == null || sbn == null || host.getPackageName().equals(sbn.getPackageName())) return;
        if (CapturePolicy.isIgnored(host, sbn.getPackageName())) return;

        EventRecord record;
        try {
            record = NotificationParser.parse(host, sbn);
        } catch (Throwable parseError) {
            ListenerStateStore.markError(host, "parse", sbn.getPackageName(), parseError);
            record = basicRecord(host, sbn);
        }

        try {
            applyRanking(host, record, sbn, rankingMap);
            record.contentHash = ContentHasher.hash(record);
            if (CapturePolicy.isRedacted(host, record.packageName)) CapturePolicy.redact(record);
            EventStore.saveBlocking(host, record);
            ListenerStateStore.markNotificationSaved(host, record.packageName, record.notificationKey);
        } catch (Throwable saveError) {
            ListenerStateStore.markError(host, "save", sbn.getPackageName(), saveError);
        }
    }

    private static EventRecord basicRecord(Context context, StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle extras = n == null || n.extras == null ? Bundle.EMPTY : n.extras;
        EventRecord r = new EventRecord();
        r.eventType = EventTypes.NOTIFICATION;
        r.source = "notification_listener_fallback";
        r.packageName = sbn.getPackageName();
        r.appLabel = AppInfoUtil.label(context, r.packageName);
        r.notificationKey = sbn.getKey();
        r.notificationId = sbn.getId();
        r.notificationTag = sbn.getTag();
        r.postedAt = sbn.getPostTime();
        r.updatedAt = System.currentTimeMillis();
        r.eventKey = "notification:" + r.notificationKey + "@" + r.postedAt;
        try {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
            if (title == null) title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = extras.getCharSequence(Notification.EXTRA_TEXT);
            r.title = title == null ? null : title.toString();
            r.text = text == null ? null : text.toString();
            r.fullText = r.text;
        } catch (Throwable ignored) {}
        if (n != null) {
            try { r.channelId = n.getChannelId(); } catch (Throwable ignored) {}
            r.category = n.category;
            r.flags = n.flags;
            r.foregroundService = (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
            r.groupSummary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
            r.fullScreen = n.fullScreenIntent != null;
        }
        try { r.groupKey = sbn.getGroupKey(); } catch (Throwable ignored) {}
        r.ongoing = sbn.isOngoing();
        r.clearable = sbn.isClearable();
        NotificationParser.reclassify(r);
        return r;
    }

    private static void applyRanking(
            NotificationListenerService host,
            EventRecord record,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap
    ) {
        try {
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            NotificationListenerService.RankingMap map =
                    rankingMap != null ? rankingMap : host.getCurrentRanking();
            if (map != null && map.getRanking(sbn.getKey(), ranking)) {
                record.importance = ranking.getImportance();
                record.conversation = ranking.isConversation();
                record.rankingCanBubble = ranking.canBubble();
                record.rankingAmbient = ranking.isAmbient();
                record.rankingSuspended = ranking.isSuspended();

                NotificationChannel channel = ranking.getChannel();
                if (channel != null) {
                    record.channelId = channel.getId();
                    CharSequence name = channel.getName();
                    record.channelName = name == null ? null : name.toString();
                    record.channelDescription = channel.getDescription();
                    record.channelImportance = channel.getImportance();
                }

                record.silent = record.payloadSilent
                        || ranking.isAmbient()
                        || ranking.getImportance() <= NotificationManager.IMPORTANCE_LOW;
                NotificationParser.reclassify(record);
            }
        } catch (Throwable ignored) {}
    }
}
