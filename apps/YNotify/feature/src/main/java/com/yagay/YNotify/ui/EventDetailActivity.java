package com.yagay.YNotify.ui;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yagay.YNotify.R;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventStore;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.data.HistoryRepairEngine;
import com.yagay.YNotify.data.NotificationRevision;
import com.yagay.YNotify.data.NotifyDatabase;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewPage;

import java.util.Collections;
import java.util.List;

public class EventDetailActivity extends AppCompatActivity {
    private TextView app;
    private TextView meta;
    private TextView title;
    private TextView fullText;
    private TextView revisionHistory;
    private TextView messages;
    private TextView actions;
    private TextView raw;
    private Button correctClassification;
    private long eventId = -1L;
    private EventRecord current;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        eventId = getIntent().getLongExtra("id", -1);
        if (eventId < 0) { finish(); return; }
        buildUi();
        correctClassification.setOnClickListener(v -> showClassificationDialog());
        load();
    }

    private void buildUi() {
        YViewPage page = YViewLayout.installPage(
                this,
                getString(R.string.ynotify_event_detail),
                null);
        page.toolbar.setNavigationIcon(R.drawable.ic_back);
        page.toolbar.setNavigationContentDescription(R.string.ynotify_back);
        page.toolbar.setNavigationOnClickListener(v -> finish());

        LinearLayout body = YViewLayout.contentColumn(this, true);
        ScrollView scroll = YViewLayout.scrollPage(this, body);
        page.content.addView(scroll, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        app = YViewLayout.text(this, "", 16, true);
        body.addView(app);

        meta = YViewLayout.caption(this, "", 13);
        meta.setTextIsSelectable(true);
        meta.setPadding(0, YView.controlGap(this), 0, 0);
        body.addView(meta);

        correctClassification = YViewLayout.secondaryButton(
                this,
                getString(R.string.ynotify_correct_classification));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = YView.sectionGap(this);
        body.addView(correctClassification, actionParams);

        title = addDetailSection(body, R.string.ynotify_detail_title, false);
        fullText = addDetailSection(body, R.string.ynotify_detail_full_text, false);
        revisionHistory = addDetailSection(body, R.string.ynotify_revision_history, true);
        messages = addDetailSection(body, R.string.ynotify_message_structure, true);
        actions = addDetailSection(body, R.string.ynotify_action_buttons, true);
        raw = addDetailSection(body, R.string.ynotify_raw_extras, true);
    }

    private TextView addDetailSection(LinearLayout body, int titleRes, boolean monospace) {
        YViewLayout.sectionHeader(body, getString(titleRes), null);
        TextView value = monospace
                ? YViewLayout.caption(this, "", 13)
                : YViewLayout.text(this, "", 14, false);
        if (monospace) {
            value.setTypeface(Typeface.MONOSPACE);
        }
        value.setTextIsSelectable(true);
        body.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
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
                    correctClassification.setEnabled(false);
                    HistoryRepairEngine.manualClassifyAsync(this, eventId, type, headsUp, bubble, () -> {
                        correctClassification.setEnabled(true);
                        load();
                    });
                })
                .setNegativeButton(R.string.ynotify_cancel, null);
        if (r.classificationLocked) {
            builder.setNeutralButton(R.string.ynotify_restore_auto_classification, (dialog, which) -> {
                correctClassification.setEnabled(false);
                HistoryRepairEngine.clearManualLockAsync(this, eventId, () ->
                        HistoryRepairEngine.repairAsync(this, report -> {
                            correctClassification.setEnabled(true);
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
        app.setText(r.appLabel + "\n" + r.packageName);
        correctClassification.setText(r.classificationLocked
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
        line(m, R.string.ynotify_meta_notification_kind, displayNotificationKind(r.notificationKind));
        if (r.importance != 0) line(m, R.string.ynotify_meta_ranking_importance, String.valueOf(r.importance));
        if (r.conversation) line(m, R.string.ynotify_meta_conversation, getString(R.string.ynotify_yes));
        if (r.rankingCanBubble) line(m, R.string.ynotify_meta_ranking_can_bubble, getString(R.string.ynotify_yes));
        if (r.rankingAmbient) line(m, R.string.ynotify_meta_ambient, getString(R.string.ynotify_yes));
        if (r.rankingSuspended) line(m, R.string.ynotify_meta_suspended, getString(R.string.ynotify_yes));
        line(m, R.string.ynotify_meta_flags, r.flags == 0 ? null : "0x" + Integer.toHexString(r.flags));
        if (r.progressMax > 0 || r.progressIndeterminate) {
            if (r.progressIndeterminate) {
                line(m, R.string.ynotify_meta_progress, getString(R.string.ynotify_progress_indeterminate));
            } else {
                m.append(getString(R.string.ynotify_detail_progress_value, r.progress, r.progressMax)).append('\n');
            }
        }
        if (r.ongoing) line(m, R.string.ynotify_meta_ongoing, getString(R.string.ynotify_yes));
        if (r.foregroundService) line(m, R.string.ynotify_meta_foreground_service, getString(R.string.ynotify_yes));
        if (r.bubble) line(m, R.string.ynotify_meta_bubble, getString(R.string.ynotify_yes));
        if (r.fullScreen) line(m, R.string.ynotify_meta_full_screen, getString(R.string.ynotify_yes));
        if (r.headsUp) line(m, R.string.ynotify_meta_heads_up, getString(R.string.ynotify_yes));
        if (r.payloadSilent) line(m, R.string.ynotify_meta_payload_silent, getString(R.string.ynotify_yes));
        if (r.silent) line(m, R.string.ynotify_meta_silent, getString(R.string.ynotify_yes));
        if (r.revisionCount > 0) line(m, R.string.ynotify_meta_revision_count, String.valueOf(r.revisionCount));
        meta.setText(m.toString().trim());

        title.setText(n(r.title));
        fullText.setText(n(r.fullText != null ? r.fullText : r.text));
        revisionHistory.setText(formatRevisions(revisions));
        messages.setText(pretty(r.messagesJson));
        actions.setText(pretty(r.actionsJson));
        raw.setText(pretty(r.rawExtras));
    }

    private String displayEventType(String value) {
        if (value == null || value.isBlank()) return value;
        if (EventTypes.NOTIFICATION.equals(value)) return getString(R.string.ynotify_class_notification);
        if (EventTypes.TOAST.equals(value)) return getString(R.string.ynotify_filter_toast);
        if (EventTypes.DIALOG.equals(value)) return getString(R.string.ynotify_class_dialog);
        if (EventTypes.POPUP.equals(value)) return getString(R.string.ynotify_filter_popup);
        if (EventTypes.SNACKBAR.equals(value)) return getString(R.string.ynotify_filter_snackbar);
        if (EventTypes.SYSTEM_UI.equals(value)) return getString(R.string.ynotify_type_system_ui);
        if (EventTypes.OTHER_UI.equals(value)) return getString(R.string.ynotify_class_other_ui);
        return value;
    }

    private String displayNotificationKind(String value) {
        if (value == null || value.isBlank()) return value;
        switch (value) {
            case "call": return getString(R.string.ynotify_kind_call);
            case "alarm": return getString(R.string.ynotify_kind_alarm);
            case "media": return getString(R.string.ynotify_kind_media);
            case "progress": return getString(R.string.ynotify_kind_progress);
            case "foreground_service": return getString(R.string.ynotify_kind_foreground_service);
            case "message": return getString(R.string.ynotify_kind_message);
            case "system": return getString(R.string.ynotify_kind_system);
            case "ongoing": return getString(R.string.ynotify_kind_ongoing);
            case "silent": return getString(R.string.ynotify_kind_silent);
            case "standard": return getString(R.string.ynotify_class_notification);
            default: return value;
        }
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
            if (body != null && !body.isBlank()) out.append("\n").append(n(body));
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
        if (EventTypes.CUSTOM_TOAST_MARKER.equals(value)) {
            return getString(R.string.ynotify_custom_toast);
        }
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
