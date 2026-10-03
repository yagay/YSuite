package com.yagay.yparam.ui;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.yparam.R;
import com.yagay.yui.YViewLayout;

import java.util.ArrayList;
import java.util.List;

final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    interface Listener { void onClick(AppEntry entry); }
    private final List<AppEntry> items = new ArrayList<>();
    private final Listener listener;

    AppAdapter(Listener listener) { this.listener = listener; }
    void submit(List<AppEntry> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = YViewLayout.listRow(parent.getContext());
        ImageView icon = new ImageView(parent.getContext());
        int iconSize = YViewLayout.listIconSize(parent.getContext());
        row.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));
        LinearLayout textBox = new LinearLayout(parent.getContext());
        textBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(YViewLayout.listGap(parent.getContext()), 0, 0, 0);
        row.addView(textBox, tp);
        TextView title = YViewLayout.listTitle(parent.getContext());
        textBox.addView(title);
        TextView subtitle = YViewLayout.listSubtitle(parent.getContext());
        textBox.addView(subtitle);
        return new Holder(row, icon, title, subtitle);
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        h.icon.setImageDrawable(e.icon);
        h.title.setText(e.name + (e.configured
                ? h.itemView.getContext().getString(R.string.yparam_modified_suffix)
                : ""));
        h.subtitle.setText(e.packageName + (e.system
                ? h.itemView.getContext().getString(R.string.yparam_system_suffix)
                : ""));
        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }
    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon; final TextView title; final TextView subtitle;
        Holder(android.view.View itemView, ImageView icon, TextView title, TextView subtitle) {
            super(itemView); this.icon = icon; this.title = title; this.subtitle = subtitle;
        }
    }
}
