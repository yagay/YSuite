package com.yagay.yparam.ui;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    interface Listener { void onClick(AppEntry entry); }
    private final List<AppEntry> items = new ArrayList<>();
    private final Listener listener;

    AppAdapter(Listener listener) { this.listener = listener; }
    void submit(List<AppEntry> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(parent, 12);
        row.setPadding(p, p, p, p);
        ImageView icon = new ImageView(parent.getContext());
        row.addView(icon, new LinearLayout.LayoutParams(dp(parent, 48), dp(parent, 48)));
        LinearLayout textBox = new LinearLayout(parent.getContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(dp(parent, 12), 0, 0, 0);
        row.addView(textBox, tp);
        TextView title = new TextView(parent.getContext());
        title.setTextSize(16);
        title.setMaxLines(1);
        textBox.addView(title);
        TextView subtitle = new TextView(parent.getContext());
        subtitle.setTextSize(12);
        subtitle.setMaxLines(2);
        textBox.addView(subtitle);
        return new Holder(row, icon, title, subtitle);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        h.icon.setImageDrawable(e.icon);
        h.title.setText(e.name + (e.configured ? "  • 已修改" : ""));
        h.subtitle.setText(e.packageName + (e.system ? "  · 系统应用" : ""));
        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }
    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon; final TextView title; final TextView subtitle;
        Holder(android.view.View itemView, ImageView icon, TextView title, TextView subtitle) {
            super(itemView); this.icon = icon; this.title = title; this.subtitle = subtitle;
        }
    }
    private static int dp(ViewGroup v, int value) { return Math.round(value * v.getResources().getDisplayMetrics().density); }
}
