package com.yagay.YNotify.ui;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.yagay.YNotify.R;
import com.yagay.yui.YView;
import com.yagay.YNotify.data.AppSummary;
import com.yagay.YNotify.databinding.ItemAppBinding;
import com.yagay.YNotify.util.AppInfoUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {
    private final List<AppSummary> all = new ArrayList<>();
    private final List<AppSummary> shown = new ArrayList<>();
    private String query = "";

    public void submit(List<AppSummary> data) {
        all.clear();
        if (data != null) all.addAll(data);
        apply();
    }

    public void setQuery(String q) { query = q == null ? "" : q; apply(); }

    private void apply() {
        shown.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (AppSummary s : all) {
            String label = s.appLabel == null ? "" : s.appLabel;
            String pkg = s.packageName == null ? "" : s.packageName;
            if (q.isEmpty() || label.toLowerCase(Locale.ROOT).contains(q) || pkg.toLowerCase(Locale.ROOT).contains(q)) shown.add(s);
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        AppSummary s = shown.get(position);
        Context c = h.itemView.getContext();
        // XML is the structural template; YAppearance controls live row metrics and typography.
        h.b.getRoot().setPadding(
                YView.screenHorizontal(c), YView.rowVerticalPadding(c),
                YView.screenHorizontal(c), YView.rowVerticalPadding(c));
        h.b.getRoot().setMinimumHeight(YView.rowHeight(c));
        YView.styleItemTitle(h.b.appName);
        YView.styleCaption(h.b.packageName);
        YView.styleCaption(h.b.lastTime);
        YView.styleSectionTitle(h.b.count);
        h.b.appIcon.setImageDrawable(AppInfoUtil.icon(c, s.packageName));
        h.b.appName.setText(s.appLabel == null || s.appLabel.isBlank()
                ? c.getString(R.string.ynotify_unknown_app)
                : s.appLabel);
        h.b.packageName.setText(s.packageName);
        h.b.count.setText(String.valueOf(s.eventCount));
        h.b.lastTime.setText(c.getString(R.string.ynotify_last_record, TimeFormat.full(s.lastTime)));
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(c, AppHistoryActivity.class);
            i.putExtra("package", s.packageName);
            i.putExtra("label", s.appLabel);
            c.startActivity(i);
        });
    }

    @Override public int getItemCount() { return shown.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemAppBinding b;
        Holder(ItemAppBinding binding) { super(binding.getRoot()); b = binding; }
    }
}
