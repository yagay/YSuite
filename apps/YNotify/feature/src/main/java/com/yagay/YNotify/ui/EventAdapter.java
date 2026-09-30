package com.yagay.YNotify.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        h.b.appIcon.setImageDrawable(AppInfoUtil.icon(c, r.packageName));
        h.b.appName.setText(first(r.appLabel, r.packageName, "未知应用"));
        h.b.type.setText(typeLabel(r));

        String title = first(r.title, EventTypes.NOTIFICATION.equals(r.eventType) ? "" : r.className, "");
        h.b.title.setText(title);
        h.b.title.setVisibility(title.isEmpty() ? View.GONE : View.VISIBLE);

        String body = body(r);
        h.b.text.setText(body);
        h.b.text.setVisibility(body.isEmpty() ? View.GONE : View.VISIBLE);

        String details = details(r);
        h.b.details.setText(details);
        h.b.details.setVisibility(details.isEmpty() ? View.GONE : View.VISIBLE);

        String removed = r.removedAt == null ? "" : " · 已移除 " + TimeFormat.shortTime(r.removedAt);
        String revisions = r.revisionCount > 1 ? " · " + r.revisionCount + " 个版本" : "";
        h.b.time.setText(TimeFormat.full(r.postedAt) + " · " + r.source + revisions + removed);
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(c, EventDetailActivity.class);
            i.putExtra("id", r.id);
            c.startActivity(i);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    private static String body(EventRecord r) {
        Set<String> out = new LinkedHashSet<>();
        add(out, r.text);
        add(out, r.fullText);
        if (r.subText != null && !r.subText.isBlank()) add(out, "副标题：" + r.subText.trim());
        if (r.summaryText != null && !r.summaryText.isBlank()) add(out, "摘要：" + r.summaryText.trim());
        return String.join("\n", out);
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

    private static String details(EventRecord r) {
        List<String> p = new ArrayList<>();
        if (EventTypes.NOTIFICATION.equals(r.eventType)) {
            p.add("展示：" + String.join(" · ", surfaces(r)));
            String channel = first(r.channelName, r.channelId, "");
            if (!channel.isEmpty()) p.add("Channel：" + channel);
            if (r.category != null && !r.category.isBlank()) p.add("Category：" + r.category);
            if (r.progressIndeterminate) p.add("进度：进行中");
            else if (r.progressMax > 0) p.add("进度：" + r.progress + "/" + r.progressMax);
            if (r.bubble && !r.bubbleShown) p.add("支持气泡 · 未观察到展开");
            if (r.fullScreen && !r.fullScreenShown) p.add("有全屏 Intent · 未观察到启动");
            if (r.foregroundService) p.add("前台服务");
            if (r.ongoing) p.add("持续通知");
            if (r.silent) p.add("静默");
            if (r.conversation) p.add("会话通知");
        } else if (r.className != null && !r.className.isBlank()) {
            p.add("类：" + r.className);
        }
        return String.join("\n", p);
    }

    private static List<String> surfaces(EventRecord r) {
        List<String> p = new ArrayList<>();
        p.add("通知栏");
        if (r.headsUp) p.add("横幅");
        if (r.bubbleShown) p.add("气泡");
        if (r.fullScreenShown) p.add("全屏");
        return p;
    }

    private static String typeLabel(EventRecord r) {
        if (EventTypes.NOTIFICATION.equals(r.eventType)) {
            String k = r.notificationKind == null ? "standard" : r.notificationKind;
            String base;
            switch (k) {
                case "call": base = "来电"; break;
                case "alarm": base = "闹钟"; break;
                case "media": base = "媒体"; break;
                case "progress": base = "进度"; break;
                case "foreground_service": base = "前台服务"; break;
                case "message": base = "消息"; break;
                case "system": base = "系统通知"; break;
                case "ongoing": base = "持续通知"; break;
                case "silent": base = "静默通知"; break;
                default: base = "通知"; break;
            }
            return base + " · " + String.join(" · ", surfaces(r));
        }
        switch (r.eventType) {
            case EventTypes.TOAST: return "Toast";
            case EventTypes.DIALOG: return "Dialog";
            case EventTypes.SNACKBAR: return "Snackbar";
            case EventTypes.POPUP: return "应用弹层";
            case EventTypes.SYSTEM_UI: return "SystemUI";
            default: return "界面提示";
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
