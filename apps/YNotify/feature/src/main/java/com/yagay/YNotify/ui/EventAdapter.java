package com.yagay.YNotify.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.YNotify.R;
import com.yagay.yui.YView;
import com.yagay.YNotify.data.EventRecord;
import com.yagay.YNotify.data.EventTypes;
import com.yagay.YNotify.databinding.ItemEventBinding;
import com.yagay.YNotify.util.AppInfoUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.Holder> {
    private final List<EventRecord> items = new ArrayList<>();

    public void submit(List<EventRecord> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemEventBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        EventRecord r = items.get(position);
        Context c = h.itemView.getContext();
        // Bind XML templates to runtime YUI preferences; don't freeze rows at resource dp.
        h.b.getRoot().setRadius(YView.cardRadius(c));
        h.b.getRoot().setCardBackgroundColor(YView.surfaceContainer(c));
        if (h.b.getRoot().getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) h.b.getRoot().getLayoutParams();
            lp.leftMargin = YView.screenHorizontal(c);
            lp.rightMargin = YView.screenHorizontal(c);
            lp.topMargin = YView.rowVerticalPadding(c);
            lp.bottomMargin = YView.rowVerticalPadding(c);
            h.b.getRoot().setLayoutParams(lp);
        }
        if (h.b.getRoot().getChildCount() > 0) {
            View body = h.b.getRoot().getChildAt(0);
            int gap = YView.controlGap(c);
            body.setPadding(gap, gap, gap, gap);
        }
        YView.styleItemTitle(h.b.appName);
        YView.styleCaption(h.b.type);
        YView.styleStrongBody(h.b.title);
        YView.styleBody(h.b.text);
        YView.styleCaption(h.b.details);
        YView.styleCaption(h.b.time);
        h.b.appIcon.setImageDrawable(AppInfoUtil.icon(c, r.packageName));
        h.b.appName.setText(first(r.appLabel, r.packageName, c.getString(R.string.ynotify_unknown_app)));
        h.b.type.setText(typeLabel(c, r));

        String title = first(r.title, EventTypes.NOTIFICATION.equals(r.eventType) ? "" : r.className, "");
        h.b.title.setText(title);
        h.b.title.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);

        String body = body(c, r);
        h.b.text.setText(body);
        h.b.text.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);

        String details = details(c, r);
        h.b.details.setText(details);
        h.b.details.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);

        String removed = r.removedAt == null
                ? ""
                : c.getString(R.string.ynotify_event_removed_suffix, TimeFormat.shortTime(r.removedAt));
        String revisions = r.revisionCount > 1
                ? c.getString(R.string.ynotify_event_revision_suffix, r.revisionCount)
                : "";
        h.b.time.setText(c.getString(
                R.string.ynotify_event_time_source,
                TimeFormat.full(r.postedAt),
                r.source == null ? "" : r.source,
                revisions,
                removed));
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(c, EventDetailActivity.class);
            i.putExtra("id", r.id);
            c.startActivity(i);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    private static String body(Context c, EventRecord r) {
        Set<String> out = new LinkedHashSet<>();
        add(out, capturedText(c, r.text));
        add(out, capturedText(c, r.fullText));
        if (r.subText != null && !r.subText.isBlank()) {
            add(out, c.getString(R.string.ynotify_body_subtitle, r.subText.trim()));
        }
        if (r.summaryText != null && !r.summaryText.isBlank()) {
            add(out, c.getString(R.string.ynotify_body_summary, r.summaryText.trim()));
        }
        return String.join("\n", out);
    }

    private static String capturedText(Context c, String value) {
        if (EventTypes.CUSTOM_TOAST_MARKER.equals(value)) {
            return c.getString(R.string.ynotify_custom_toast);
        }
        return value;
    }

    private static void add(Set<String> out, String value) {
        if (value == null || value.isBlank()) return;
        String text = value.trim();
        for (String existing : new ArrayList<>(out)) {
            if (existing.equals(text) || existing.contains(text)) return;
            if (text.contains(existing)) out.remove(existing);
        }
        out.add(text);
    }

    private static String details(Context c, EventRecord r) {
        List<String> parts = new ArrayList<>();
        if (EventTypes.NOTIFICATION.equals(r.eventType)) {
            parts.add(c.getString(
                    R.string.ynotify_detail_surfaces,
                    String.join(", ", surfaces(c, r))));
            String channel = first(r.channelName, r.channelId, "");
            if (!channel.isEmpty()) parts.add(c.getString(R.string.ynotify_detail_channel, channel));
            if (r.category != null && !r.category.isBlank()) {
                parts.add(c.getString(R.string.ynotify_detail_category, r.category));
            }
            if (r.progressIndeterminate) {
                parts.add(c.getString(R.string.ynotify_detail_progress_running));
            } else if (r.progressMax > 0) {
                parts.add(c.getString(R.string.ynotify_detail_progress_value, r.progress, r.progressMax));
            }
            if (r.bubble && !r.bubbleShown) {
                parts.add(c.getString(R.string.ynotify_detail_bubble_not_shown));
            }
            if (r.fullScreen && !r.fullScreenShown) {
                parts.add(c.getString(R.string.ynotify_detail_fullscreen_not_shown));
            }
            if (r.foregroundService) parts.add(c.getString(R.string.ynotify_detail_foreground_service));
            if (r.ongoing) parts.add(c.getString(R.string.ynotify_detail_ongoing));
            if (r.silent) parts.add(c.getString(R.string.ynotify_detail_silent));
            if (r.conversation) parts.add(c.getString(R.string.ynotify_detail_conversation));
        } else if (r.className != null && !r.className.isBlank()) {
            parts.add(c.getString(R.string.ynotify_detail_class, r.className));
        }
        return String.join("\n", parts);
    }

    private static List<String> surfaces(Context c, EventRecord r) {
        List<String> parts = new ArrayList<>();
        parts.add(c.getString(R.string.ynotify_surface_notification_bar));
        if (r.headsUp) parts.add(c.getString(R.string.ynotify_surface_heads_up));
        if (r.bubbleShown) parts.add(c.getString(R.string.ynotify_surface_bubble));
        if (r.fullScreenShown) parts.add(c.getString(R.string.ynotify_surface_full_screen));
        return parts;
    }

    private static String typeLabel(Context c, EventRecord r) {
        if (EventTypes.NOTIFICATION.equals(r.eventType)) {
            String kind = r.notificationKind == null ? "standard" : r.notificationKind;
            String base;
            switch (kind) {
                case "call": base = c.getString(R.string.ynotify_kind_call); break;
                case "alarm": base = c.getString(R.string.ynotify_kind_alarm); break;
                case "media": base = c.getString(R.string.ynotify_kind_media); break;
                case "progress": base = c.getString(R.string.ynotify_kind_progress); break;
                case "foreground_service": base = c.getString(R.string.ynotify_kind_foreground_service); break;
                case "message": base = c.getString(R.string.ynotify_kind_message); break;
                case "system": base = c.getString(R.string.ynotify_kind_system); break;
                case "ongoing": base = c.getString(R.string.ynotify_kind_ongoing); break;
                case "silent": base = c.getString(R.string.ynotify_kind_silent); break;
                default: base = c.getString(R.string.ynotify_class_notification); break;
            }
            return c.getString(
                    R.string.ynotify_type_with_surfaces,
                    base,
                    String.join(", ", surfaces(c, r)));
        }
        switch (r.eventType) {
            case EventTypes.TOAST: return c.getString(R.string.ynotify_filter_toast);
            case EventTypes.DIALOG: return c.getString(R.string.ynotify_class_dialog);
            case EventTypes.SNACKBAR: return c.getString(R.string.ynotify_filter_snackbar);
            case EventTypes.POPUP: return c.getString(R.string.ynotify_filter_popup);
            case EventTypes.SYSTEM_UI: return c.getString(R.string.ynotify_type_system_ui);
            default: return c.getString(R.string.ynotify_class_other_ui);
        }
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.trim().isEmpty()) return value;
        return "";
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemEventBinding b;
        Holder(ItemEventBinding binding) { super(binding.getRoot()); b = binding; }
    }
}
