package com.yagay.YNotify.ui;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.YNotify.R;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.HistoryRepairEngine;
import com.yagay.YNotify.data.NotificationRevision;
import com.yagay.YNotify.data.NotifyDatabase;
import com.yagay.YNotify.databinding.ActivityEventDetailBinding;

import java.util.Collections;
import java.util.List;

public class EventDetailActivity extends AppCompatActivity {
    private ActivityEventDetailBinding b;
    private long eventId = -1L;
    private EventRecord current;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        b = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        b.toolbar.setNavigationOnClickListener(v -> finish());
        eventId = getIntent().getLongExtra("id", -1);
        if (eventId < 0) { finish(); return; }
        b.btnCorrectClassification.setOnClickListener(v -> showClassificationDialog());
        load();
    }

    private void load() {
        EventStore.io().execute(() -> {
            NotifyDatabase db = NotifyDatabase.get(this);
            EventRecord r = db.eventDao().byId(eventId);
            List<NotificationRevision> revisions = r != null && r.instanceId != null
                    ? db.historyDao().revisions(r.instanceId)
                    : Collections.emptyList();
            runOnUiThread(() -> {
                if (r != null && !isFinishing()) {
                    current = r;
                    bind(r, revisions);
                }
            });
        });
    }

    private void showClassificationDialog() {
        EventRecord r = current;
        if (r == null) return;
        String[] labels = {
                getString(R.string.ynotify_class_notification),
                getString(R.string.ynotify_class_heads_up),
                getString(R.string.ynotify_class_bubble),
                getString(R.string.ynotify_filter_toast),
                getString(R.string.ynotify_class_dialog),
                getString(R.string.ynotify_filter_popup),
                getString(R.string.ynotify_filter_snackbar),
                getString(R.string.ynotify_class_other_ui)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.ynotify_correct_classification)
                .setSingleChoiceItems(labels, selectedIndex(r), (dialog, which) -> {
                    String type;
                    boolean headsUp = false;
                    boolean bubble = false;
                    switch (which) {
                        case 0: type = EventTypes.NOTIFICATION; break;
                        case 1: type = EventTypes.NOTIFICATION; headsUp = true; break;
                        case 2: type = EventTypes.NOTIFICATION; bubble = true; break;
                        case 3: type = EventTypes.TOAST; break;
                        case 4: type = EventTypes.DIALOG; break;
                        case 5: type = EventTypes.POPUP; break;
                        case 6: type = EventTypes.SNACKBAR; break;
                        default: type = EventTypes.OTHER_UI; break;
                    }
                    dialog.dismiss();
                    b.btnCorrectClassification.setEnabled(false);
                    HistoryRepairEngine.manualClassifyAsync(this, eventId, type, headsUp, bubble, () -> {
                        b.btnCorrectClassification.setEnabled(true);
                        load();
                    });
                })
                .setNegativeButton(R.string.ynotify_cancel, null);
        if (r.classificationLocked) {
            builder.setNeutralButton(R.string.ynotify_restore_auto_classification, (dialog, which) -> {
                b.btnCorrectClassification.setEnabled(false);
                HistoryRepairEngine.clearManualLockAsync(this, eventId, () ->
                        HistoryRepairEngine.repairAsync(this, report -> {
                            b.btnCorrectClassification.setEnabled(true);
                            load();
                        }));
            });
        }
        builder.show();
    }

    private static int selectedIndex(EventRecord r) {
        if (EventTypes.NOTIFICATION.equals(r.eventType)) {
            if (r.bubble || "bubble".equals(r.notificationKind)) return 2;
            if (r.headsUp) return 1;
            return 0;
        }
        if (EventTypes.TOAST.equals(r.eventType)) return 3;
        if (EventTypes.DIALOG.equals(r.eventType)) return 4;
        if (EventTypes.POPUP.equals(r.eventType)) return 5;
        if (EventTypes.SNACKBAR.equals(r.eventType)) return 6;
        return 7;
    }

    private void bind(EventRecord r, List<NotificationRevision> revisions) {
        b.app.setText(r.appLabel + "\n" + r.packageName);
        b.btnCorrectClassification.setText(r.classificationLocked
                ? R.string.ynotify_modify_manual_classification
                : R.string.ynotify_correct_classification);
        StringBuilder m = new StringBuilder();
        line(m, R.string.ynotify_meta_type, displayEventType(r.eventType));
        line(m, R.string.ynotify_meta_original_type, displayEventType(r.originalEventType));
        line(m, R.string.ynotify_meta_classification_source, r.classificationSource);
        if (r.classificationVersion > 0) line(m, R.string.ynotify_meta_classification_version, String.valueOf(r.classificationVersion));
        if (r.classificationLocked) line(m, R.string.ynotify_meta_classification_lock, getString(R.string.ynotify_manual));
        if (r.mergedIntoId != null) line(m, R.string.ynotify_meta_merged_into, String.valueOf(r.mergedIntoId));
        line(m, R.string.ynotify_meta_source, r.source);
        line(m, R.string.ynotify_meta_posted, TimeFormat.full(r.postedAt));
        line(m, R.string.ynotify_meta_updated, TimeFormat.full(r.updatedAt));
        if (r.removedAt != null) {
            line(m, R.string.ynotify_meta_removed, TimeFormat.full(r.removedAt));
            line(m, R.string.ynotify_meta_removal_reason, removalReason(r.removalReason));
        }
        line(m, R.string.ynotify_meta_class, r.className);
        line(m, R.string.ynotify_meta_accessibility_source_class, r.accessibilitySourceClass);
        if (r.accessibilityEventType != 0) line(m, R.string.ynotify_meta_accessibility_event_type, String.valueOf(r.accessibilityEventType));
        if (r.accessibilityWindowId != -1) line(m, R.string.ynotify_meta_accessibility_window_id, String.valueOf(r.accessibilityWindowId));
        if (r.accessibilityContentChangeTypes != 0) line(m, R.string.ynotify_meta_accessibility_content_change, String.valueOf(r.accessibilityContentChangeTypes));
        line(m, R.string.ynotify_meta_channel, r.channelId);
        line(m, R.string.ynotify_meta_channel_name, r.channelName);
        line(m, R.string.ynotify_meta_channel_description, r.channelDescription);
        if (r.channelImportance != 0) line(m, R.string.ynotify_meta_channel_importance, String.valueOf(r.channelImportance));
        line(m, R.string.ynotify_meta_notification_id, r.notificationId == 0 ? null : String.valueOf(r.notificationId));
        line(m, R.string.ynotify_meta_tag, r.notificationTag);
        line(m, R.string.ynotify_meta_key, r.notificationKey);
        line(m, R.string.ynotify_meta_group, r.groupKey);
        if (r.groupSummary) line(m, R.string.ynotify_meta_group_summary, getString(R.string.ynotify_yes));
        line(m, R.string.ynotify_meta_template, r.template);
        line(m, R.string.ynotify_meta_category, r.category);
        line(m, R.string.ynotify_meta_notification_kind, r.notificationKind);
        if (r.importance != 0) line(m, R.string.ynotify_meta_ranking_importance, String.valueOf(r.importance));
        if (r.conversation) line(m, R.string.ynotify_meta_conversation, getString(R.string.ynotify_yes));
        if (r.rankingCanBubble) line(m, R.string.ynotify_meta_ranking_can_bubble, getString(R.string.ynotify_yes));
        if (r.rankingAmbient) line(m, R.string.ynotify_meta_ambient, getString(R.string.ynotify_yes));
        if (r.rankingSuspended) line(m, R.string.ynotify_meta_suspended, getString(R.string.ynotify_yes));
        line(m, R.string.ynotify_meta_flags, r.flags == 0 ? null : "0x" + Integer.toHexString(r.flags));
        if (r.progressMax > 0 || r.progressIndeterminate) {
            line(m, R.string.ynotify_meta_progress,
                    r.progressIndeterminate
                            ? getString(R.string.ynotify_progress_indeterminate)
                            : r.progress + " " + getString(R.string.ynotify_meta_merged_into).toLowerCase() + " " + r.progressMax);
        }
        if (r.ongoing) line(m, R.string.ynotify_meta_ongoing, getString(R.string.ynotify_yes));
        if (r.foregroundService) line(m, R.string.ynotify_meta_foreground_service, getString(R.string.ynotify_yes));
        if (r.bubble) line(m, R.string.ynotify_meta_bubble, getString(R.string.ynotify_yes));
        if (r.fullScreen) line(m, R.string.ynotify_meta_full_screen, getString(R.string.ynotify_yes));
        if (r.headsUp) line(m, R.string.ynotify_meta_heads_up, getString(R.string.ynotify_yes));
        if (r.payloadSilent) line(m, R.string.ynotify_meta_payload_silent, getString(R.string.ynotify_yes));
        if (r.silent) line(m, R.string.ynotify_meta_silent, getString(R.string.ynotify_yes));
        if (r.revisionCount > 0) line(m, R.string.ynotify_meta_revision_count, String.valueOf(r.revisionCount));
        b.meta.setText(m.toString().trim());

        b.title.setText(n(r.title));
        b.fullText.setText(n(r.fullText != null ? r.fullText : r.text));
        b.revisionHistory.setText(formatRevisions(revisions));
        b.messages.setText(pretty(r.messagesJson));
        b.actions.setText(pretty(r.actionsJson));
        b.raw.setText(pretty(r.rawExtras));
    }

    private String displayEventType(String value) {
        if (value == null || value.isBlank()) return value;
        if (EventTypes.NOTIFICATION.equals(value)) return getString(R.string.ynotify_class_notification);
        if (EventTypes.TOAST.equals(value)) return getString(R.string.ynotify_filter_toast);
        if (EventTypes.DIALOG.equals(value)) return getString(R.string.ynotify_class_dialog);
        if (EventTypes.POPUP.equals(value)) return getString(R.string.ynotify_filter_popup);
        if (EventTypes.SNACKBAR.equals(value)) return getString(R.string.ynotify_filter_snackbar);
        if (EventTypes.OTHER_UI.equals(value)) return getString(R.string.ynotify_class_other_ui);
        return value;
    }

    private String formatRevisions(List<NotificationRevision> revisions) {
        if (revisions == null || revisions.isEmpty()) return getString(R.string.ynotify_not_available);
        StringBuilder out = new StringBuilder();
        for (NotificationRevision r : revisions) {
            if (out.length() > 0) out.append("\n\n");
            out.append(getString(R.string.ynotify_revision_header, r.sequence, TimeFormat.full(r.capturedAt)));
            if (r.progressMax > 0) out.append("\n").append(getString(R.string.ynotify_revision_progress, r.progress, r.progressMax));
            if (r.importance != 0) out.append("\n").append(getString(R.string.ynotify_revision_importance, r.importance));
            String body = r.fullText != null && !r.fullText.isBlank() ? r.fullText : r.text;
            if (r.title != null && !r.title.isBlank()) out.append("\n").append(r.title);
            if (body != null && !body.isBlank()) out.append("\n").append(body);
        }
        return out.toString();
    }

    private String removalReason(int reason) {
        switch (reason) {
            case NotificationListenerService.REASON_CLICK: return getString(R.string.ynotify_reason_click);
            case NotificationListenerService.REASON_CANCEL: return getString(R.string.ynotify_reason_cancel);
            case NotificationListenerService.REASON_CANCEL_ALL: return getString(R.string.ynotify_reason_cancel_all);
            case NotificationListenerService.REASON_APP_CANCEL: return getString(R.string.ynotify_reason_app_cancel);
            case NotificationListenerService.REASON_APP_CANCEL_ALL: return getString(R.string.ynotify_reason_app_cancel_all);
            case NotificationListenerService.REASON_TIMEOUT: return getString(R.string.ynotify_reason_timeout);
            case NotificationListenerService.REASON_SNOOZED: return getString(R.string.ynotify_reason_snoozed);
            case NotificationListenerService.REASON_CHANNEL_BANNED: return getString(R.string.ynotify_reason_channel_banned);
            case NotificationListenerService.REASON_PACKAGE_CHANGED: return getString(R.string.ynotify_reason_package_changed);
            case NotificationListenerService.REASON_USER_STOPPED: return getString(R.string.ynotify_reason_user_stopped);
            case NotificationListenerService.REASON_ASSISTANT_CANCEL: return getString(R.string.ynotify_reason_assistant_cancel);
            default: return reason == 0
                    ? getString(R.string.ynotify_reason_unknown)
                    : getString(R.string.ynotify_reason_system, reason);
        }
    }

    private void line(StringBuilder sb, int labelRes, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(getString(labelRes)).append(": ").append(value).append('\n');
        }
    }

    private String n(String value) {
        return value == null || value.isBlank() ? getString(R.string.ynotify_not_available) : value;
    }

    private String pretty(String value) {
        if (value == null || value.isBlank()) return getString(R.string.ynotify_not_available);
        try {
            String trimmed = value.trim();
            if (trimmed.startsWith("{")) return new org.json.JSONObject(trimmed).toString(2);
            if (trimmed.startsWith("[")) return new org.json.JSONArray(trimmed).toString(2);
        } catch (Throwable ignored) {}
        return value;
    }
}
