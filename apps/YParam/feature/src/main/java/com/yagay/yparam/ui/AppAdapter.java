package com.yagay.yparam.ui;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.yparam.R;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewListRow;

import java.util.ArrayList;
import java.util.List;

final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    interface Listener { void onClick(AppEntry entry); }
    private final List<AppEntry> items = new ArrayList<>();
    private final Listener listener;

    AppAdapter(Listener listener) { this.listener = listener; }
    void submit(List<AppEntry> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(YViewLayout.listItem(parent.getContext(), true));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        AppEntry e = items.get(position);
        h.row.icon.setImageDrawable(e.icon);
        h.row.title.setText(e.name + (e.configured
                ? h.itemView.getContext().getString(R.string.yparam_modified_suffix)
                : ""));
        h.row.subtitle.setText(e.packageName + (e.system
                ? h.itemView.getContext().getString(R.string.yparam_system_suffix)
                : ""));
        h.itemView.setOnClickListener(v -> listener.onClick(e));
    }
    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final YViewListRow row;
        Holder(YViewListRow row) {
            super(row.root);
            this.row = row;
        }
    }
}
