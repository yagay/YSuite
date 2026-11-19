package com.yagay.YFloat;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.yagay.yui.YViewLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User-managed list: add installed apps, remove and reorder shortcuts. */
public final class ShortcutAppsActivity extends AppCompatActivity {
    private final List<String> selected = new ArrayList<>();
    private LinearLayout entries;
    private int dp(int n) { return ScreenGeometry.dp(this, n); }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        selected.addAll(ShortcutAppStore.load(this));
        LinearLayout page = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_shortcuts_title),
                getString(R.string.yfloat_shortcuts_desc));
        Button add = new Button(this);
        add.setText(R.string.yfloat_shortcuts_add);
        add.setOnClickListener(v -> showPicker());
        page.addView(add);
        entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        page.addView(entries);
        setContentView(YViewLayout.scrollPage(this, page));
        redraw();
    }

    private void redraw() {
        entries.removeAllViews();
        PackageManager pm = getPackageManager();
        if (selected.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText(R.string.yfloat_shortcuts_empty);
            hint.setPadding(dp(12), dp(16), dp(12), dp(16));
            entries.addView(hint);
        }
        for (int i = 0; i < selected.size(); i++) {
            final int index = i;
            String pkg = selected.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(4), dp(8), dp(4));
            try {
                ImageView icon = new ImageView(this);
                icon.setImageDrawable(pm.getApplicationIcon(pkg));
                row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            } catch (Exception ignored) { }
            TextView name = new TextView(this);
            try {
                name.setText(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (Exception e) {
                name.setText(pkg + " (" + getString(R.string.yfloat_shortcuts_missing) + ")");
            }
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(dp(10), 0, dp(6), 0);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button up = smallButton("↑");
            up.setContentDescription(getString(R.string.yfloat_shortcuts_up));
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> move(index, -1));
            row.addView(up);
            Button down = smallButton("↓");
            down.setContentDescription(getString(R.string.yfloat_shortcuts_down));
            down.setEnabled(i < selected.size() - 1);
            down.setOnClickListener(v -> move(index, 1));
            row.addView(down);
            Button remove = smallButton("×");
            remove.setContentDescription(getString(R.string.yfloat_shortcuts_remove));
            remove.setOnClickListener(v -> {
                selected.remove(index);
                persist();
            });
            row.addView(remove);
            entries.addView(row);
        }
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(48)));
        return button;
    }

    private void move(int index, int delta) {
        int dest = index + delta;
        if (index < 0 || dest < 0 || dest >= selected.size()) return;
        java.util.Collections.swap(selected, index, dest);
        persist();
    }

    private void persist() {
        ShortcutAppStore.save(this, selected);
        redraw();
    }

    private void showPicker() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> installed = ShortcutAppStore.launchable(this);
        List<String> draft = new ArrayList<>(selected);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(R.string.yfloat_shortcuts_search);
        layout.addView(search);
        ScrollView scroll = new ScrollView(this);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, dp(460)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.yfloat_shortcuts_add)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    selected.clear();
                    selected.addAll(draft);
                    persist();
                })
                .setNegativeButton(android.R.string.cancel, null).create();
        Runnable refresh = () -> {
            choices.removeAllViews();
            String filter = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (ResolveInfo info : installed) {
                String pkg = info.activityInfo.packageName;
                String label = String.valueOf(info.loadLabel(pm));
                if (!filter.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(filter)
                        && !pkg.toLowerCase(Locale.ROOT).contains(filter)) continue;
                CheckBox box = new CheckBox(this);
                box.setText(label);
                box.setButtonTintList(null);
                box.setCompoundDrawablePadding(dp(8));
                try {
                    android.graphics.drawable.Drawable icon = info.loadIcon(pm);
                    icon.setBounds(0, 0, dp(32), dp(32));
                    box.setCompoundDrawables(icon, null, null, null);
                } catch (Exception ignored) { }
                box.setChecked(draft.contains(pkg));
                box.setPadding(dp(10), dp(4), dp(10), dp(4));
                box.setOnCheckedChangeListener((view, checked) -> {
                    if (checked && !draft.contains(pkg)) draft.add(pkg);
                    else if (!checked) draft.remove(pkg);
                });
                choices.addView(box);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        refresh.run();
        dialog.setOnDismissListener(d -> redraw());
        dialog.show();
    }
}
