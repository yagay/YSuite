package com.yagay.YNotify.data;

import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class HistoryRepairEngine {
    public static final int CURRENT_VERSION = 3;
    private static final long UI_EXACT_WINDOW_MS = 5_000L;
    private static final long UI_SIMILAR_WINDOW_MS = 3_000L;
    private static final long SYSTEM_UI_SIMILAR_WINDOW_MS = 2_500L;
    private static final long STANDARD_UPDATE_GAP_MS = 10 * 60_000L;
    private static final long LONG_UPDATE_GAP_MS = 24 * 60 * 60_000L;
    private static final String PREFS = "history_repair";
    private static final String KEY_AUTO_V3 = "auto_v3_done";

    private HistoryRepairEngine() {}

    public interface Callback {
        void onComplete(RepairReport report);
    }

    public static final class RepairReport {
        public int scanned;
        public int eventTypeChanged;
        public int systemUiClassified;
        public int notificationKindChanged;
        public int mergedUi;
        public int crossPackageHeadsUp;
        public int mergedNotifications;
        public int recheckedMerged;
        public int ambiguousUi;
        public int protectedManual;
        public String error;

        public boolean succeeded() { return error == null || error.isEmpty(); }

        public String summary() {
            if (!succeeded()) return com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_76e6723dc54e) + error;
            return com.yagay.suite.api.YLocale.text(com.yagay.YNotify.R.string.ynotify_generated_ee918ab7c27d) + scanned + " 条 · 类型纠正 " + eventTypeChanged
                    + " 条 · SystemUI " + systemUiClassified
                    + " 条 · 通知子类型纠正 " + notificationKindChanged
                    + " 条 · 横幅关联 " + mergedUi
                    + " 条 · 跨应用横幅 " + crossPackageHeadsUp
                    + " 条 · 重复通知合并 " + mergedNotifications + " 条"
                    + (ambiguousUi > 0 ? " · 无充分证据 " + ambiguousUi + " 条" : "")
                    + (recheckedMerged > 0 ? " · 重新检查旧合并 " + recheckedMerged + " 条" : "")
                    + (protectedManual > 0 ? " · 保留手动分类 " + protectedManual + " 条" : "");
        }
    }

    public static void repairAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EventStore.io().execute(() -> {
            RepairReport report = repairBlocking(app);
            if (callback != null) new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(report));
        });
    }

    public static void runOnceAfterUpgrade(Context context) {
        Context app = context.getApplicationContext();
        if (app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_V3, false)) return;
        repairAsync(app, report -> {
            if (report.succeeded()) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_V3, true).apply();
            }
        });
    }

    public static RepairReport repairBlocking(Context context) {
        RepairReport report = new RepairReport();
        try {
            NotifyDatabase db = NotifyDatabase.get(context);
            db.runInTransaction(() -> {
                EventDao dao = db.eventDao();
                List<EventRecord> all = dao.loadAllForRepair();
                report.scanned = all.size();

                for (EventRecord r : all) {
                    if (r.originalEventType == null || r.originalEventType.isBlank()) {
                        r.originalEventType = r.eventType;
                        dao.update(r);
                    }
                    if (r.classificationLocked) report.protectedManual++;
                }

                resetOldAutomaticRepairs(dao, all, report);
                reclassifyEventTypes(dao, all, report);
                mergeDuplicateNotifications(dao, all, report);
                linkAccessibilityBanners(dao, all, report);
                reclassifyNotificationKinds(dao, all, report);

                db.eventFtsDao().backfillMissing();
                db.eventFtsDao().prune();
            });
        } catch (Throwable t) {
            report.error = t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
        return report;
    }

    public static void manualClassifyAsync(Context context, long id, String type, boolean headsUp, Runnable done) {
        manualClassifyAsync(context, id, type, headsUp, false, done);
    }

    public static void manualClassifyAsync(Context context, long id, String type, boolean headsUp, boolean bubble, Runnable done) {
        Context app = context.getApplicationContext();
        EventStore.io().execute(() -> {
            try {
                NotifyDatabase db = NotifyDatabase.get(app);
                EventRecord r = db.eventDao().byId(id);
                if (r != null) {
                    if (r.originalEventType == null || r.originalEventType.isBlank()) r.originalEventType = r.eventType;
                    boolean notification = EventTypes.NOTIFICATION.equals(type);
                    r.eventType = type;
                    r.headsUp = notification && headsUp;
                    r.bubble = notification && bubble;
                    r.classificationVersion = CURRENT_VERSION;
                    r.classificationSource = "manual";
                    r.classificationLocked = true;
                    r.mergedIntoId = null;
                    if (notification) r.notificationKind = classifyNotification(r);
                    db.eventDao().update(r);
                    if (r.id > 0 && r.id <= Integer.MAX_VALUE) db.eventFtsDao().upsert(EventFts.from(r));
                }
            } catch (Throwable ignored) {}
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        });
    }

    public static void clearManualLockAsync(Context context, long id, Runnable done) {
        Context app = context.getApplicationContext();
        EventStore.io().execute(() -> {
            try {
                NotifyDatabase db = NotifyDatabase.get(app);
                EventRecord r = db.eventDao().byId(id);
                if (r != null) {
                    r.classificationLocked = false;
                    r.classificationVersion = 0;
                    r.classificationSource = "manual_reset";
                    db.eventDao().update(r);
                }
            } catch (Throwable ignored) {}
            if (done != null) new Handler(Looper.getMainLooper()).post(done);
        });
    }

    private static void resetOldAutomaticRepairs(EventDao dao, List<EventRecord> all, RepairReport report) {
        for (EventRecord r : all) {
            if (r.classificationLocked) continue;
            boolean priorAutomatic = r.classificationVersion > 0
                    || containsAny(r.classificationSource, "repair:", "rules:v", "recheck:v");
            if (r.mergedIntoId != null && priorAutomatic) {
                r.mergedIntoId = null;
                report.recheckedMerged++;
            }
            if (priorAutomatic && r.originalEventType != null && !r.originalEventType.isBlank()) {
                r.eventType = r.originalEventType;
            }
            if (priorAutomatic || r.classificationVersion < CURRENT_VERSION) {
                r.classificationVersion = 0;
                r.classificationSource = "recheck:v3";
                dao.update(r);
            }
        }
    }

    private static void reclassifyEventTypes(EventDao dao, List<EventRecord> all, RepairReport report) {
        for (EventRecord r : all) {
            if (r.mergedIntoId != null || r.classificationLocked) continue;
            String inferred = inferEventType(r);
            if (inferred == null || inferred.isBlank()) continue;
            if (!inferred.equals(r.eventType)) {
                r.eventType = inferred;
                report.eventTypeChanged++;
            }
            if (EventTypes.SYSTEM_UI.equals(inferred)) report.systemUiClassified++;
            if (EventTypes.OTHER_UI.equals(inferred) && isAccessibility(r) && !isSystemUiRecord(r)) {
                report.ambiguousUi++;
            }
            r.classificationVersion = CURRENT_VERSION;
            r.classificationSource = appendSource(r.classificationSource, "rules:v3:event");
            dao.update(r);
        }
    }

    static String inferEventType(EventRecord r) {
        if (hasNotificationEvidence(r)) return EventTypes.NOTIFICATION;

        String c = safeLower(r.className) + " " + safeLower(r.accessibilitySourceClass);
        if (c.contains("snackbar")) return EventTypes.SNACKBAR;
        if (c.contains("transientnotification") || c.contains("toast")) return EventTypes.TOAST;
        if (c.contains("popupwindow") || c.contains("popup")) return EventTypes.POPUP;
        if (c.contains("alertdialog") || c.contains("dialog")) return EventTypes.DIALOG;

        if (isSystemUiRecord(r)) return EventTypes.SYSTEM_UI;

        String source = safeLower(r.source);
        if (source.contains("xposed") || source.contains("hook")) {
            if (isSpecificUiType(r.originalEventType)) return r.originalEventType;
            if (isSpecificUiType(r.eventType)) return r.eventType;
        }

        if (isAccessibility(r)) {
            if (r.accessibilityEventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) return EventTypes.OTHER_UI;
            return EventTypes.OTHER_UI;
        }

        if (isKnownUiType(r.originalEventType)) return r.originalEventType;
        if (isKnownUiType(r.eventType)) return r.eventType;
        return EventTypes.OTHER_UI;
    }

    static boolean hasNotificationEvidence(EventRecord r) {
        if (r == null) return false;
        String source = safeLower(r.source);
        if (source.contains("notification_listener")) return true;
        if (r.notificationKey != null && !r.notificationKey.isBlank()) return true;
        if (r.channelId != null && !r.channelId.isBlank()) return true;
        if (r.instanceId != null) return true;
        String extras = r.rawExtras;
        return extras != null && (extras.contains("android.title")
                || extras.contains("android.text")
                || extras.contains("android.template")
                || extras.contains("android.messages"));
    }

    private static boolean isSystemUiRecord(EventRecord r) {
        return "com.android.systemui".equals(r.packageName)
                || safeLower(r.source).contains("systemui");
    }

    private static boolean isAccessibility(EventRecord r) {
        return safeLower(r.source).contains("accessibility");
    }

    private static boolean isSpecificUiType(String type) {
        return EventTypes.TOAST.equals(type)
                || EventTypes.DIALOG.equals(type)
                || EventTypes.SNACKBAR.equals(type)
                || EventTypes.POPUP.equals(type);
    }

    private static boolean isKnownUiType(String type) {
        return isSpecificUiType(type)
                || EventTypes.SYSTEM_UI.equals(type)
                || EventTypes.OTHER_UI.equals(type);
    }

    private static void mergeDuplicateNotifications(EventDao dao, List<EventRecord> all, RepairReport report) {
        Map<String, List<EventRecord>> groups = new HashMap<>();
        for (EventRecord r : all) {
            if (r.mergedIntoId != null || r.classificationLocked) continue;
            if (!EventTypes.NOTIFICATION.equals(r.eventType)) continue;
            if (r.notificationKey == null || r.notificationKey.isBlank()) continue;
            groups.computeIfAbsent(r.notificationKey, k -> new ArrayList<>()).add(r);
        }

        for (List<EventRecord> group : groups.values()) {
            if (group.size() < 2) continue;
            group.sort(Comparator.comparingLong((EventRecord r) -> r.postedAt).thenComparingLong(r -> r.id));
            EventRecord canonical = null;
            for (EventRecord next : group) {
                if (canonical == null) {
                    canonical = next;
                    continue;
                }
                if (!sameNotificationLifecycle(canonical, next)) {
                    canonical = next;
                    continue;
                }

                mergeLatestInto(canonical, next);
                canonical.classificationVersion = CURRENT_VERSION;
                canonical.classificationSource = appendSource(canonical.classificationSource, "repair:v3:notification-update");
                dao.update(canonical);

                next.mergedIntoId = canonical.id;
                next.classificationVersion = CURRENT_VERSION;
                next.classificationSource = appendSource(next.classificationSource, "repair:v3:merged-update");
                dao.update(next);
                report.mergedNotifications++;
            }
        }
    }

    private static boolean sameNotificationLifecycle(EventRecord current, EventRecord next) {
        if (current.removedAt != null && next.postedAt > current.removedAt + 2_000L) return false;
        long lastSeen = Math.max(current.postedAt, current.updatedAt);
        long gap = Math.max(0L, next.postedAt - lastSeen);
        boolean longLived = current.ongoing || next.ongoing
                || current.foregroundService || next.foregroundService
                || isProgress(current) || isProgress(next);
        return gap <= (longLived ? LONG_UPDATE_GAP_MS : STANDARD_UPDATE_GAP_MS);
    }

    private static void mergeLatestInto(EventRecord target, EventRecord latest) {
        target.updatedAt = Math.max(target.updatedAt, latest.updatedAt);
        if (latest.removedAt != null) {
            target.removedAt = latest.removedAt;
            target.removalReason = latest.removalReason;
        }
        target.title = prefer(latest.title, target.title);
        target.text = prefer(latest.text, target.text);
        target.fullText = prefer(latest.fullText, target.fullText);
        target.subText = prefer(latest.subText, target.subText);
        target.summaryText = prefer(latest.summaryText, target.summaryText);
        target.rawExtras = prefer(latest.rawExtras, target.rawExtras);
        target.messagesJson = prefer(latest.messagesJson, target.messagesJson);
        target.actionsJson = prefer(latest.actionsJson, target.actionsJson);
        target.notificationId = latest.notificationId;
        target.notificationTag = prefer(latest.notificationTag, target.notificationTag);
        target.channelId = prefer(latest.channelId, target.channelId);
        target.channelName = prefer(latest.channelName, target.channelName);
        target.channelDescription = prefer(latest.channelDescription, target.channelDescription);
        target.channelImportance = latest.channelImportance != 0 ? latest.channelImportance : target.channelImportance;
        target.groupKey = prefer(latest.groupKey, target.groupKey);
        target.groupSummary = latest.groupSummary;
        target.category = prefer(latest.category, target.category);
        target.notificationKind = prefer(latest.notificationKind, target.notificationKind);
        target.template = prefer(latest.template, target.template);
        target.importance = latest.importance != 0 ? latest.importance : target.importance;
        target.conversation = latest.conversation;
        target.rankingCanBubble = latest.rankingCanBubble;
        target.rankingAmbient = latest.rankingAmbient;
        target.rankingSuspended = latest.rankingSuspended;
        target.flags = latest.flags;
        target.ongoing = latest.ongoing;
        target.foregroundService = latest.foregroundService;
        target.clearable = latest.clearable;
        target.bubble = latest.bubble;
        target.fullScreen = latest.fullScreen;
        target.payloadSilent = latest.payloadSilent;
        target.silent = latest.silent;
        target.headsUp = target.headsUp || latest.headsUp;
        target.progress = latest.progress;
        target.progressMax = latest.progressMax;
        target.progressIndeterminate = latest.progressIndeterminate;
        target.className = prefer(latest.className, target.className);
        target.accessibilitySourceClass = prefer(latest.accessibilitySourceClass, target.accessibilitySourceClass);
        if (latest.accessibilityEventType != 0) target.accessibilityEventType = latest.accessibilityEventType;
        if (latest.accessibilityWindowId != -1) target.accessibilityWindowId = latest.accessibilityWindowId;
        if (latest.accessibilityContentChangeTypes != 0) target.accessibilityContentChangeTypes = latest.accessibilityContentChangeTypes;
        target.contentHash = prefer(latest.contentHash, target.contentHash);
        target.revisionCount = Math.max(target.revisionCount, 1) + Math.max(latest.revisionCount, 1);
    }

    private static void linkAccessibilityBanners(EventDao dao, List<EventRecord> all, RepairReport report) {
        Map<String, List<EventRecord>> notificationsByPackage = new HashMap<>();
        Map<Long, List<EventRecord>> notificationBuckets = new HashMap<>();
        for (EventRecord r : all) {
            if (r.mergedIntoId != null || !EventTypes.NOTIFICATION.equals(r.eventType)) continue;
            notificationsByPackage.computeIfAbsent(r.packageName, k -> new ArrayList<>()).add(r);
            addToBucket(notificationBuckets, r.postedAt, r);
            if (Math.abs(r.updatedAt - r.postedAt) > 1_000L) addToBucket(notificationBuckets, r.updatedAt, r);
        }

        for (EventRecord ui : all) {
            if (ui.mergedIntoId != null || ui.classificationLocked) continue;
            if (!isKnownUiType(ui.eventType)) continue;
            if (!isAccessibility(ui)) continue;

            boolean systemUi = isSystemUiRecord(ui) || EventTypes.SYSTEM_UI.equals(ui.eventType);
            String uiText = normalizedBody(ui);
            if (uiText.length() < (systemUi ? 3 : 2)) continue;

            List<EventRecord> candidates = systemUi
                    ? candidatesAround(notificationBuckets, ui.postedAt, UI_EXACT_WINDOW_MS)
                    : notificationsByPackage.get(ui.packageName);
            if (candidates == null || candidates.isEmpty()) continue;

            EventRecord best = null;
            double bestScore = -1d;
            double secondScore = -1d;
            long bestDt = Long.MAX_VALUE;

            for (EventRecord n : candidates) {
                if (n.classificationLocked) continue;
                if (systemUi && "com.android.systemui".equals(n.packageName)) continue;
                long dt = Math.min(Math.abs(ui.postedAt - n.postedAt), Math.abs(ui.postedAt - n.updatedAt));
                if (dt > UI_EXACT_WINDOW_MS) continue;

                double similarity = bestTextSimilarity(ui, n);
                boolean exactish = similarity >= 0.995d;
                boolean samePackage = ui.packageName.equals(n.packageName);
                boolean confident;
                if (systemUi && !samePackage) {
                    confident = (exactish && dt <= UI_EXACT_WINDOW_MS)
                            || (similarity >= 0.92d && dt <= SYSTEM_UI_SIMILAR_WINDOW_MS);
                } else {
                    confident = (exactish && dt <= UI_EXACT_WINDOW_MS)
                            || (similarity >= 0.82d && dt <= UI_SIMILAR_WINDOW_MS);
                }
                if (!confident) continue;

                double score = similarity - Math.min(dt, UI_EXACT_WINDOW_MS) / (double) UI_EXACT_WINDOW_MS * 0.04d;
                if (score > bestScore) {
                    secondScore = bestScore;
                    bestScore = score;
                    best = n;
                    bestDt = dt;
                } else if (score > secondScore) {
                    secondScore = score;
                }
            }

            if (best == null) continue;
            if (systemUi && secondScore >= 0d && bestScore - secondScore < 0.015d && bestDt > 750L) continue;

            best.headsUp = true;
            best.classificationVersion = CURRENT_VERSION;
            best.classificationSource = appendSource(best.classificationSource,
                    systemUi ? "repair:v3:systemui-headsup" : "repair:v3:accessibility-banner");
            dao.update(best);

            ui.mergedIntoId = best.id;
            ui.classificationVersion = CURRENT_VERSION;
            ui.classificationSource = appendSource(ui.classificationSource,
                    systemUi ? "repair:v3:merged-systemui" : "repair:v3:merged-banner");
            dao.update(ui);
            report.mergedUi++;
            if (!ui.packageName.equals(best.packageName)) report.crossPackageHeadsUp++;
        }
    }

    private static void addToBucket(Map<Long, List<EventRecord>> buckets, long when, EventRecord record) {
        buckets.computeIfAbsent(when / 1_000L, k -> new ArrayList<>()).add(record);
    }

    private static List<EventRecord> candidatesAround(Map<Long, List<EventRecord>> buckets, long when, long windowMs) {
        long from = (when - windowMs) / 1_000L;
        long to = (when + windowMs) / 1_000L;
        List<EventRecord> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long bucket = from; bucket <= to; bucket++) {
            List<EventRecord> values = buckets.get(bucket);
            if (values == null) continue;
            for (EventRecord r : values) {
                if (seen.add(r.id)) out.add(r);
            }
        }
        return out;
    }

    static double bestTextSimilarity(EventRecord ui, EventRecord notification) {
        List<String> left = textVariants(ui);
        List<String> right = textVariants(notification);
        double best = 0d;
        for (String a : left) {
            if (a.length() < 2) continue;
            for (String b : right) {
                if (b.length() < 2) continue;
                if (a.equals(b) || a.contains(b) || b.contains(a)) return 1d;
                best = Math.max(best, dice(a, b));
            }
        }
        return best;
    }

    private static List<String> textVariants(EventRecord r) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addVariant(values, r.title);
        addVariant(values, r.text);
        addVariant(values, r.fullText);
        addVariant(values, r.subText);
        addVariant(values, r.summaryText);
        addVariant(values, combinedBody(r));
        return new ArrayList<>(values);
    }

    private static void addVariant(Set<String> values, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank()) values.add(normalized);
    }

    private static void reclassifyNotificationKinds(EventDao dao, List<EventRecord> all, RepairReport report) {
        for (EventRecord r : all) {
            if (r.mergedIntoId != null || r.classificationLocked) continue;
            if (!EventTypes.NOTIFICATION.equals(r.eventType)) continue;
            String before = r.notificationKind;
            String after = classifyNotification(r);
            boolean changed = before == null ? after != null : !before.equals(after);
            if (changed) {
                r.notificationKind = after;
                report.notificationKindChanged++;
            }
            r.classificationVersion = CURRENT_VERSION;
            r.classificationSource = appendSource(r.classificationSource,
                    changed ? "repair:v3:notification-kind" : "rules:v3:notification");
            dao.update(r);
        }
    }

    private static String classifyNotification(EventRecord r) {
        if (r.fullScreen) return "full_screen";
        if (r.bubble) return "bubble";
        if (Notification.CATEGORY_CALL.equals(r.category)) return "call";
        if (Notification.CATEGORY_ALARM.equals(r.category)) return "alarm";
        if (Notification.CATEGORY_TRANSPORT.equals(r.category) || containsMediaSession(r.rawExtras)) return "media";
        if (isProgress(r)) return "progress";
        if (r.foregroundService) return "foreground_service";
        if (Notification.CATEGORY_MESSAGE.equals(r.category) || r.conversation) return "message";
        if (Notification.CATEGORY_SYSTEM.equals(r.category)
                || Notification.CATEGORY_STATUS.equals(r.category)
                || Notification.CATEGORY_SERVICE.equals(r.category)) return "system";
        if (r.ongoing) return "ongoing";
        if (r.silent) return "silent";
        return "standard";
    }

    private static boolean isProgress(EventRecord r) {
        return r.progressMax > 0 || r.progressIndeterminate || "progress".equals(r.notificationKind);
    }

    private static boolean containsMediaSession(String extrasJson) {
        return extrasJson != null && extrasJson.contains(Notification.EXTRA_MEDIA_SESSION);
    }

    private static String combinedBody(EventRecord r) {
        StringBuilder b = new StringBuilder();
        addText(b, r.title);
        addText(b, r.fullText);
        if (r.fullText == null || r.fullText.isBlank()) addText(b, r.text);
        return b.toString();
    }

    private static String normalizedBody(EventRecord r) {
        return normalize(combinedBody(r));
    }

    private static void addText(StringBuilder b, String s) {
        if (s == null || s.isBlank()) return;
        if (b.length() > 0) b.append(' ');
        b.append(s);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
    }

    static double dice(String a, String b) {
        if (a.equals(b)) return 1d;
        if (a.length() < 2 || b.length() < 2) return 0d;
        Set<String> aa = bigrams(a);
        Set<String> bb = bigrams(b);
        int intersection = 0;
        for (String x : aa) if (bb.contains(x)) intersection++;
        return (2d * intersection) / (aa.size() + bb.size());
    }

    private static Set<String> bigrams(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < s.length() - 1; i++) out.add(s.substring(i, i + 2));
        return out;
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String appendSource(String existing, String value) {
        if (existing == null || existing.isBlank()) return value;
        if (existing.contains(value)) return existing;
        return existing + "+" + value;
    }

    private static String prefer(String newer, String older) {
        return newer == null || newer.isBlank() ? older : newer;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
