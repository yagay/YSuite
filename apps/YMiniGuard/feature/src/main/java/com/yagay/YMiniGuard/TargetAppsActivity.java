package com.yagay.YMiniGuard;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewStatusTone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TargetAppsActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_FOREGROUND = "foreground";
    static final String MODE_BACKGROUND_PLAYBACK = "background_playback";
    static final String MODE_FORCE_SUPPORT = "force_support";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<AppItem> allApps = new ArrayList<>();
    private final ArrayList<AppItem> filteredApps = new ArrayList<>();
    private final HashSet<String> selected = new HashSet<>();

    private AppAdapter adapter;
    private ProgressBar progress;
    private EditText search;
    private TextView countView;
    private String mode;
    private String preferenceKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mode = getIntent() == null ? MODE_FOREGROUND : getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_FORCE_SUPPORT.equals(mode) && !MODE_BACKGROUND_PLAYBACK.equals(mode)) {
            mode = MODE_FOREGROUND;
        }

        preferenceKey = MODE_FORCE_SUPPORT.equals(mode)
                ? ConfigKeys.FORCE_SUPPORT_PACKAGES
                : MODE_BACKGROUND_PLAYBACK.equals(mode)
                ? ConfigKeys.BACKGROUND_PLAYBACK_PACKAGES
                : ConfigKeys.FOREGROUND_PACKAGES;

        selected.addAll(GuardApp.getStringSet(preferenceKey));

        try {
            buildUi();
            loadAppsAsync();
        } catch (Throwable t) {
            CrashStore.record(this, "TargetAppsActivity.onCreate", t);
            showFatal(t);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        String title = MODE_FORCE_SUPPORT.equals(mode)
                ? getString(R.string.ymg_target_force_title)
                : MODE_BACKGROUND_PLAYBACK.equals(mode)
                ? getString(R.string.ymg_target_background_title)
                : getString(R.string.ymg_target_foreground_title);
        String help = MODE_FORCE_SUPPORT.equals(mode)
                ? getString(R.string.ymg_target_force_help)
                : MODE_BACKGROUND_PLAYBACK.equals(mode)
                ? getString(R.string.ymg_target_background_help)
                : getString(R.string.ymg_target_foreground_help);

        LinearLayout root = YViewLayout.installFixed(this, title, help);

        search = YViewLayout.searchField(this, getString(R.string.ymg_search_apps));
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        countView = YViewLayout.statusLine(this, "", YViewStatusTone.Neutral);
        root.addView(countView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(progress);

        ListView list = new ListView(this);
        list.setDividerHeight(1);
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button close = YViewLayout.secondaryButton(this, getString(R.string.ymg_back));
        close.setOnClickListener(v -> finish());
        root.addView(close);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadAppsAsync() {
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            ArrayList<AppItem> loaded = new ArrayList<>();
            Throwable failure = null;
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> infos;
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    infos = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
                } else {
                    infos = pm.getInstalledApplications(0);
                }

                for (ApplicationInfo info : infos) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (info == null || info.packageName == null || getPackageName().equals(info.packageName)) {
                        continue;
                    }
                    if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

                    String label;
                    try {
                        CharSequence cs = info.loadLabel(pm);
                        label = cs == null ? info.packageName : cs.toString().trim();
                        if (label.isEmpty()) label = info.packageName;
                    } catch (Throwable ignored) {
                        label = info.packageName;
                    }
                    boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    loaded.add(new AppItem(label, info.packageName, system));
                }

                loaded.sort(Comparator
                        .comparing((AppItem item) -> item.label, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(item -> item.packageName));
            } catch (Throwable t) {
                failure = t;
            }

            Throwable finalFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                if (finalFailure != null) {
                    CrashStore.record(this, "TargetAppsActivity.loadApps", finalFailure);
                    Toast.makeText(
                            this,
                            getString(R.string.ymg_load_apps_failed, finalFailure.getClass().getSimpleName()),
                            Toast.LENGTH_LONG).show();
                }
                allApps.clear();
                allApps.addAll(loaded);
                applyFilter(search == null ? "" : search.getText().toString());
            });
        });
    }

    private void applyFilter(String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        filteredApps.clear();
        if (query.isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            for (AppItem item : allApps) {
                if (item.label.toLowerCase(Locale.ROOT).contains(query)
                        || item.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    filteredApps.add(item);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        refreshCount();
    }

    private void toggle(AppItem item, boolean enabled) {
        if (enabled) selected.add(item.packageName);
        else selected.remove(item.packageName);
        GuardApp.putStringSet(preferenceKey, selected);
        refreshCount();
    }

    private void refreshCount() {
        if (countView == null) return;
        YViewLayout.setStatus(
                countView,
                getString(R.string.ymg_selected_count, selected.size(), filteredApps.size()),
                YViewStatusTone.Neutral);
    }

    private void showFatal(Throwable t) {
        LinearLayout root = YViewLayout.installFixed(
                this,
                getString(R.string.ymg_app_list_failed_title),
                getString(
                        R.string.ymg_app_list_failed_detail,
                        t.getClass().getName(),
                        String.valueOf(t.getMessage())));
        Button close = YViewLayout.secondaryButton(this, getString(R.string.ymg_back));
        close.setOnClickListener(v -> finish());
        root.addView(close);
    }


    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int position) { return filteredApps.get(position); }
        @Override public long getItemId(int position) {
            return filteredApps.get(position).packageName.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView instanceof LinearLayout && convertView.getTag() instanceof RowHolder) {
                holder = (RowHolder) convertView.getTag();
            } else {
                holder = createRow();
                convertView = holder.root;
            }

            AppItem item = filteredApps.get(position);
            holder.title.setText(item.label);
            holder.subtitle.setText(item.packageName
                    + (item.system ? getString(R.string.ymg_system_app_suffix) : ""));
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(selected.contains(item.packageName));
            holder.check.setOnCheckedChangeListener((button, checked) -> toggle(item, checked));
            holder.root.setOnClickListener(v -> holder.check.setChecked(!holder.check.isChecked()));
            return convertView;
        }

        private RowHolder createRow() {
            LinearLayout row = YViewLayout.listRow(TargetAppsActivity.this);

            LinearLayout texts = new LinearLayout(TargetAppsActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);

            TextView title = YViewLayout.listTitle(TargetAppsActivity.this);
            texts.addView(title);

            TextView subtitle = YViewLayout.listSubtitle(TargetAppsActivity.this);
            texts.addView(subtitle);

            row.addView(texts, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            MaterialCheckBox check = new MaterialCheckBox(TargetAppsActivity.this);
            row.addView(check);

            RowHolder holder = new RowHolder(row, title, subtitle, check);
            row.setTag(holder);
            return holder;
        }
    }

    private static final class RowHolder {
        final LinearLayout root;
        final TextView title;
        final TextView subtitle;
        final MaterialCheckBox check;

        RowHolder(LinearLayout root, TextView title, TextView subtitle, MaterialCheckBox check) {
            this.root = root;
            this.title = title;
            this.subtitle = subtitle;
            this.check = check;
        }
    }

    private static final class AppItem {
        final String label;
        final String packageName;
        final boolean system;

        AppItem(String label, String packageName, boolean system) {
            this.label = label;
            this.packageName = packageName;
            this.system = system;
        }
    }
}
