package com.yagay.YNotify.ui;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
        String[] labels = {"通知", "横幅通知", "气泡通知", "Toast", "Dialog", "应用弹层", "Snackbar", "其他界面提示"};
        new AlertDialog.Builder(this)
                .setTitle("修正分类")
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
                .setNeutralButton(r.classificationLocked ? "恢复自动分类" : null, (dialog, which) -> {
                    b.btnCorrectClassification.setEnabled(false);
                    HistoryRepairEngine.clearManualLockAsync(this, eventId, () ->
                            HistoryRepairEngine.repairAsync(this, report -> {
                                b.btnCorrectClassification.setEnabled(true);
                                load();
                            }));
                })
                .setNegativeButton("取消", null)
                .show();
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
        b.btnCorrectClassification.setText(r.classificationLocked ? "修改手动分类" : "修正分类");
        StringBuilder m = new StringBuilder();
        line(m, "类型", r.eventType);
        line(m, "原始类型", r.originalEventType);
        line(m, "分类来源", r.classificationSource);
        if (r.classificationVersion > 0) line(m, "分类规则版本", String.valueOf(r.classificationVersion));
        if (r.classificationLocked) line(m, "分类锁定", "手动");
        if (r.mergedIntoId != null) line(m, "已合并到", String.valueOf(r.mergedIntoId));
        line(m, "来源", r.source);
        line(m, "出现", TimeFormat.full(r.postedAt));
        line(m, "最后更新", TimeFormat.full(r.updatedAt));
        if (r.removedAt != null) {
            line(m, "移除", TimeFormat.full(r.removedAt));
            line(m, "移除原因", removalReason(r.removalReason));
        }
        line(m, "类", r.className);
        line(m, "Accessibility Source 类", r.accessibilitySourceClass);
        if (r.accessibilityEventType != 0) line(m, "Accessibility EventType", String.valueOf(r.accessibilityEventType));
        if (r.accessibilityWindowId != -1) line(m, "Accessibility WindowId", String.valueOf(r.accessibilityWindowId));
        if (r.accessibilityContentChangeTypes != 0) line(m, "Accessibility ContentChange", String.valueOf(r.accessibilityContentChangeTypes));
        line(m, "Channel", r.channelId);
        line(m, "Channel 名称", r.channelName);
        line(m, "Channel 描述", r.channelDescription);
        if (r.channelImportance != 0) line(m, "Channel Importance", String.valueOf(r.channelImportance));
        line(m, "Notification ID", r.notificationId == 0 ? null : String.valueOf(r.notificationId));
        line(m, "Tag", r.notificationTag);
        line(m, "Key", r.notificationKey);
        line(m, "Group", r.groupKey);
        if (r.groupSummary) line(m, "Group Summary", "是");
        line(m, "Template", r.template);
        line(m, "Category", r.category);
        line(m, "通知子类型", r.notificationKind);
        if (r.importance != 0) line(m, "Ranking Importance", String.valueOf(r.importance));
        if (r.conversation) line(m, "Conversation", "是");
        if (r.rankingCanBubble) line(m, "Ranking canBubble", "是");
        if (r.rankingAmbient) line(m, "Ambient", "是");
        if (r.rankingSuspended) line(m, "Suspended", "是");
        line(m, "Flags", r.flags == 0 ? null : "0x" + Integer.toHexString(r.flags));
        if (r.progressMax > 0 || r.progressIndeterminate) line(m, "进度", r.progressIndeterminate ? "不确定" : r.progress + "/" + r.progressMax);
        if (r.ongoing) line(m, "持续通知", "是");
        if (r.foregroundService) line(m, "前台服务", "是");
        if (r.bubble) line(m, "气泡", "是");
        if (r.fullScreen) line(m, "全屏 Intent", "是");
        if (r.headsUp) line(m, "实际 Heads-up", "是");
        if (r.payloadSilent) line(m, "Payload 未请求声音/振动", "是");
        if (r.silent) line(m, "最终分类为静默", "是");
        if (r.revisionCount > 0) line(m, "版本数", String.valueOf(r.revisionCount));
        b.meta.setText(m.toString().trim());

        b.title.setText(n(r.title));
        b.fullText.setText(n(r.fullText != null ? r.fullText : r.text));
        b.revisionHistory.setText(formatRevisions(revisions));
        b.messages.setText(pretty(r.messagesJson));
        b.actions.setText(pretty(r.actionsJson));
        b.raw.setText(pretty(r.rawExtras));
    }

    private static String formatRevisions(List<NotificationRevision> revisions) {
        if (revisions == null || revisions.isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        for (NotificationRevision r : revisions) {
            if (out.length() > 0) out.append("\n\n");
            out.append("#").append(r.sequence)
                    .append("  ").append(TimeFormat.full(r.capturedAt));
            if (r.progressMax > 0) out.append("\n进度：").append(r.progress).append("/").append(r.progressMax);
            if (r.importance != 0) out.append("\nImportance：").append(r.importance);
            String body = r.fullText != null && !r.fullText.isBlank() ? r.fullText : r.text;
            if (r.title != null && !r.title.isBlank()) out.append("\n").append(r.title);
            if (body != null && !body.isBlank()) out.append("\n").append(body);
        }
        return out.toString();
    }

    private static String removalReason(int reason) {
        switch (reason) {
            case NotificationListenerService.REASON_CLICK: return "用户点击";
            case NotificationListenerService.REASON_CANCEL: return "用户划掉/取消";
            case NotificationListenerService.REASON_CANCEL_ALL: return "用户清除全部";
            case NotificationListenerService.REASON_APP_CANCEL: return "App 主动取消";
            case NotificationListenerService.REASON_APP_CANCEL_ALL: return "App 主动取消全部";
            case NotificationListenerService.REASON_TIMEOUT: return "通知超时";
            case NotificationListenerService.REASON_SNOOZED: return "通知被稍后提醒";
            case NotificationListenerService.REASON_CHANNEL_BANNED: return "通知 Channel 被禁用";
            case NotificationListenerService.REASON_PACKAGE_CHANGED: return "应用包状态变化";
            case NotificationListenerService.REASON_USER_STOPPED: return "用户/配置停止";
            case NotificationListenerService.REASON_ASSISTANT_CANCEL: return "通知助理取消";
            default: return reason == 0 ? "未知" : "系统原因 " + reason;
        }
    }

    private static void line(StringBuilder sb, String k, String v) {
        if (v != null && !v.isBlank()) sb.append(k).append(": ").append(v).append('\n');
    }
    private static String n(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String pretty(String s) {
        if (s == null || s.isBlank()) return "—";
        try {
            String t = s.trim();
            if (t.startsWith("{")) return new org.json.JSONObject(t).toString(2);
            if (t.startsWith("[")) return new org.json.JSONArray(t).toString(2);
        } catch (Throwable ignored) {}
        return s;
    }
}
