package com.yagay.YNotify.data;

import android.content.Context;

import com.yagay.YNotify.util.ContentHasher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EventStore {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final ConcurrentHashMap<String, Long> PENDING_HEADS_UP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PENDING_BUBBLE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> PENDING_FULL_SCREEN = new ConcurrentHashMap<>();
    private EventStore() {}

    public static void save(Context context, EventRecord record) {
        IO.execute(() -> saveBlocking(context.getApplicationContext(), record));
    }

    public static void saveBlocking(Context context, EventRecord record) {
        stampClassification(record);
        NotifyDatabase db = NotifyDatabase.get(context);
        EventDao dao = db.eventDao();

        if (EventTypes.NOTIFICATION.equals(record.eventType)) {
            saveNotification(db, record);
            return;
        }

        String body = record.fullText != null ? record.fullText : record.text;
        if (body != null && !body.isBlank()) {
            EventRecord crossSource = dao.recentEquivalentFromOtherSource(
                    record.packageName, record.eventType, body, record.source, record.postedAt - 1_000L);
            if (crossSource != null) {
                crossSource.updatedAt = Math.max(crossSource.updatedAt, record.updatedAt);
                if (!crossSource.source.contains(record.source)) {
                    crossSource.source = crossSource.source + "+" + record.source;
                }
                if (record.className != null && !record.className.isBlank()) {
                    crossSource.className = record.className;
                }
                dao.update(crossSource);
                syncFts(db, crossSource);
                return;
            }
        }

        record.id = dao.insert(record);
        syncFts(db, record);
    }

    private static void stampClassification(EventRecord record) {
        if (record.originalEventType == null || record.originalEventType.isBlank()) {
            record.originalEventType = record.eventType;
        }
        if (record.classificationVersion == 0) {
            record.classificationVersion = HistoryRepairEngine.CURRENT_VERSION;
            record.classificationSource = "live";
        }
    }

    private static void saveNotification(NotifyDatabase db, EventRecord incoming) {
        EventDao dao = db.eventDao();
        HistoryDao history = db.historyDao();

        applyPendingSurfaces(incoming);
        NotificationKindClassifier.apply(incoming);

        if (incoming.contentHash == null || incoming.contentHash.isBlank()) {
            incoming.contentHash = ContentHasher.hash(incoming);
        }

        NotificationInstance instance = incoming.notificationKey == null
                ? null
                : history.activeInstanceByNotificationKey(incoming.notificationKey);

        if (instance == null) {
            instance = new NotificationInstance();
            String baseKey = incoming.notificationKey == null || incoming.notificationKey.isBlank()
                    ? incoming.eventKey
                    : "notification:" + incoming.notificationKey + "@" + incoming.postedAt;
            instance.instanceKey = baseKey;
            instance.notificationKey = incoming.notificationKey;
            instance.packageName = incoming.packageName;
            instance.firstSeen = incoming.postedAt;
            instance.lastSeen = incoming.updatedAt;
            instance.channelId = incoming.channelId;
            instance.currentRevision = 0;
            instance.id = history.insertInstance(instance);
        } else {
            incoming.eventKey = instance.instanceKey;
            incoming.postedAt = instance.firstSeen;
            instance.lastSeen = Math.max(instance.lastSeen, incoming.updatedAt);
            instance.channelId = incoming.channelId;
        }

        NotificationRevision latest = history.latestRevision(instance.id);
        if (latest == null || !incoming.contentHash.equals(latest.contentHash)) {
            NotificationRevision revision = new NotificationRevision();
            revision.instanceId = instance.id;
            revision.sequence = instance.currentRevision + 1;
            revision.capturedAt = incoming.updatedAt;
            revision.contentHash = incoming.contentHash;
            revision.title = incoming.title;
            revision.text = incoming.text;
            revision.fullText = incoming.fullText;
            revision.messagesJson = incoming.messagesJson;
            revision.progress = incoming.progress;
            revision.progressMax = incoming.progressMax;
            revision.importance = incoming.importance;
            revision.source = incoming.source;
            long id = history.insertRevision(revision);
            if (id > 0) instance.currentRevision = revision.sequence;
        }
        history.updateInstance(instance);

        incoming.instanceId = instance.id;
        incoming.revisionCount = instance.currentRevision;

        EventRecord existing = dao.byEventKey(incoming.eventKey);
        if (existing == null) {
            incoming.id = dao.insert(incoming);
        } else {
            incoming.id = existing.id;
            incoming.postedAt = existing.postedAt;
            incoming.removedAt = null;
            incoming.removalReason = 0;
            incoming.headsUp = incoming.headsUp || existing.headsUp;
            incoming.bubble = incoming.bubble || existing.bubble;
            incoming.fullScreen = incoming.fullScreen || existing.fullScreen;
            incoming.bubbleShown = incoming.bubbleShown || existing.bubbleShown;
            incoming.fullScreenShown = incoming.fullScreenShown || existing.fullScreenShown;
            incoming.originalEventType = existing.originalEventType == null ? incoming.eventType : existing.originalEventType;
            incoming.classificationLocked = existing.classificationLocked;
            incoming.mergedIntoId = existing.mergedIntoId;
            if (existing.classificationLocked) {
                incoming.eventType = existing.eventType;
                incoming.classificationSource = existing.classificationSource;
                incoming.classificationVersion = existing.classificationVersion;
            }
            dao.update(incoming);
        }
        syncFts(db, incoming);
    }

    private static void applyPendingSurfaces(EventRecord incoming) {
        String key = incoming.notificationKey;
        if (key == null || key.isBlank()) return;
        long when = incoming.updatedAt > 0 ? incoming.updatedAt : System.currentTimeMillis();
        Long heads = PENDING_HEADS_UP.remove(key);
        if (heads != null && Math.abs(when - heads) <= 15_000L) incoming.headsUp = true;
        Long bubble = PENDING_BUBBLE.remove(key);
        if (bubble != null && Math.abs(when - bubble) <= 15_000L) incoming.bubbleShown = true;
        Long fullScreen = PENDING_FULL_SCREEN.remove(key);
        if (fullScreen != null && Math.abs(when - fullScreen) <= 15_000L) incoming.fullScreenShown = true;
    }

    public static void markRemoved(Context context, String notificationKey, long when, int reason) {
        IO.execute(() -> markRemovedBlocking(context.getApplicationContext(), notificationKey, when, reason));
    }

    public static void markRemovedBlocking(Context context, String notificationKey, long when, int reason) {
        if (notificationKey == null) return;
        PENDING_HEADS_UP.remove(notificationKey);
        PENDING_BUBBLE.remove(notificationKey);
        PENDING_FULL_SCREEN.remove(notificationKey);
        NotifyDatabase db = NotifyDatabase.get(context);
        EventDao dao = db.eventDao();
        EventRecord r = dao.latestByNotificationKey(notificationKey);
        if (r != null && r.removedAt == null) {
            r.removedAt = when;
            r.removalReason = reason;
            r.updatedAt = Math.max(r.updatedAt, when);
            dao.update(r);
            syncFts(db, r);
        }
        NotificationInstance instance = db.historyDao().activeInstanceByNotificationKey(notificationKey);
        if (instance != null) {
            instance.removedAt = when;
            instance.removalReason = reason;
            instance.lastSeen = Math.max(instance.lastSeen, when);
            db.historyDao().updateInstance(instance);
        }
    }

    public static void markHeadsUp(Context context, String notificationKey, long when) {
        markSurface(context, notificationKey, when, Surface.HEADS_UP);
    }

    public static void markBubble(Context context, String notificationKey, long when) {
        markSurface(context, notificationKey, when, Surface.BUBBLE);
    }

    public static void markFullScreen(Context context, String notificationKey, long when) {
        markSurface(context, notificationKey, when, Surface.FULL_SCREEN);
    }

    private static void markSurface(Context context, String notificationKey, long when, Surface surface) {
        if (notificationKey == null || notificationKey.isBlank()) return;
        IO.execute(() -> {
            NotifyDatabase db = NotifyDatabase.get(context);
            EventRecord r = db.eventDao().latestByNotificationKey(notificationKey);
            if (r != null) {
                if (surface == Surface.HEADS_UP) r.headsUp = true;
                else if (surface == Surface.BUBBLE) r.bubbleShown = true;
                else if (surface == Surface.FULL_SCREEN) r.fullScreenShown = true;
                r.updatedAt = Math.max(r.updatedAt, when);
                db.eventDao().update(r);
                syncFts(db, r);
            } else {
                ConcurrentHashMap<String, Long> pending = pendingMap(surface);
                pending.put(notificationKey, when);
                prunePending(pending);
            }
        });
    }

    private static ConcurrentHashMap<String, Long> pendingMap(Surface surface) {
        if (surface == Surface.BUBBLE) return PENDING_BUBBLE;
        if (surface == Surface.FULL_SCREEN) return PENDING_FULL_SCREEN;
        return PENDING_HEADS_UP;
    }

    private static void prunePending(ConcurrentHashMap<String, Long> map) {
        if (map.size() <= 200) return;
        long cutoff = System.currentTimeMillis() - 30_000L;
        map.entrySet().removeIf(e -> e.getValue() < cutoff);
    }

    private enum Surface { HEADS_UP, BUBBLE, FULL_SCREEN }

    public static void recordGap(Context context, long disconnectedAt, long reconnectedAt, String reason) {
        if (disconnectedAt <= 0 || reconnectedAt <= disconnectedAt) return;
        IO.execute(() -> {
            CaptureGap gap = new CaptureGap();
            gap.disconnectedAt = disconnectedAt;
            gap.reconnectedAt = reconnectedAt;
            gap.durationMs = reconnectedAt - disconnectedAt;
            gap.reason = reason;
            NotifyDatabase.get(context).historyDao().insertGap(gap);
        });
    }

    public static void cleanup(Context context, int days) {
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        IO.execute(() -> {
            NotifyDatabase db = NotifyDatabase.get(context);
            db.historyDao().deleteOldRevisions(cutoff);
            db.historyDao().deleteOldInstances(cutoff);
            db.eventDao().deleteOlderThan(cutoff);
            db.eventFtsDao().prune();
        });
    }

    public static void deletePackage(Context context, String packageName) {
        IO.execute(() -> {
            NotifyDatabase db = NotifyDatabase.get(context);
            db.historyDao().deleteRevisionsForPackage(packageName);
            db.historyDao().deleteInstancesForPackage(packageName);
            db.eventDao().deletePackage(packageName);
            db.eventFtsDao().prune();
        });
    }

    public static void clearAll(Context context) {
        IO.execute(() -> {
            NotifyDatabase db = NotifyDatabase.get(context);
            db.historyDao().deleteAllRevisions();
            db.historyDao().deleteAllInstances();
            db.historyDao().deleteAllGaps();
            db.eventDao().deleteAll();
            db.eventFtsDao().clear();
            PENDING_HEADS_UP.clear();
            PENDING_BUBBLE.clear();
            PENDING_FULL_SCREEN.clear();
        });
    }

    private static void syncFts(NotifyDatabase db, EventRecord r) {
        if (r.id <= 0 || r.id > Integer.MAX_VALUE) return;
        try { db.eventFtsDao().upsert(EventFts.from(r)); } catch (Throwable ignored) {}
    }

    public static ExecutorService io() { return IO; }
}
