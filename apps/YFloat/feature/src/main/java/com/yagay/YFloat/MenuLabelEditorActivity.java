package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewDialogs;
import com.yagay.yui.YViewSection;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Edits YFloat-only display names without changing package/activity identity. */
public final class MenuLabelEditorActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        rebuild();
    }

    private void rebuild() {
        LinearLayout root = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_menu_labels_title),
                getString(R.string.yfloat_menu_labels_desc));

        addCustomSection(root);
        addTargetSection(root, TargetMenuStore.MODE_SHARE,
                getString(R.string.yfloat_menu_share_title));
        addTargetSection(root, TargetMenuStore.MODE_PROCESS,
                getString(R.string.yfloat_menu_process_title));

        setContentView(YViewLayout.scrollPage(this, root));
    }

    private void addCustomSection(LinearLayout root) {
        List<CustomMenuActionStore.Item> items = CustomMenuActionStore.load(this);
        YViewSection section = YViewLayout.section(this,
                getString(R.string.yfloat_menu_text_actions_count, items.size()),
                getString(R.string.yfloat_menu_text_actions_desc));
        if (items.isEmpty()) {
            addEmpty(section.body, getString(R.string.yfloat_menu_no_custom_actions));
        } else {
            for (CustomMenuActionStore.Item item : items) {
                addRenameRow(section.body,
                        item.label,
                        CustomMenuActionStore.typeLabel(item.type),
                        () -> showRenameDialog(
                                item.label,
                                null,
                                name -> {
                                    if (CustomMenuActionStore.rename(this, item.id, name)) {
                                        Toast.makeText(this,
                                                R.string.yfloat_menu_name_changed,
                                                Toast.LENGTH_SHORT).show();
                                        rebuild();
                                    }
                                },
                                null));
            }
        }
        YViewLayout.addSection(root, section);
    }

    private void addTargetSection(LinearLayout root, String mode, String title) {
        List<TargetMenuStore.Item> system = discoverTargets(mode);
        YViewSection section = YViewLayout.section(this,
                getString(R.string.yfloat_menu_target_count, title, system.size()),
                getString(R.string.yfloat_menu_target_desc));

        if (system.isEmpty()) {
            addEmpty(section.body, getString(R.string.yfloat_menu_no_system_targets));
        } else {
            for (TargetMenuStore.Item item : system) {
                String display = TargetMenuStore.displayLabel(
                        this, mode, item.key(), item.label);
                String subtitle = display.equals(item.label)
                        ? item.packageName
                        : getString(R.string.yfloat_menu_system_name, item.label);
                addRenameRow(section.body, display, subtitle,
                        () -> showRenameDialog(
                                display,
                                item.label,
                                name -> {
                                    if (TargetMenuStore.rename(this, mode, item.key(), name)) {
                                        Toast.makeText(this,
                                                R.string.yfloat_menu_name_changed,
                                                Toast.LENGTH_SHORT).show();
                                        rebuild();
                                    }
                                },
                                () -> {
                                    if (TargetMenuStore.clearAlias(this, mode, item.key())) {
                                        Toast.makeText(this,
                                                R.string.yfloat_menu_system_name_restored,
                                                Toast.LENGTH_SHORT).show();
                                        rebuild();
                                    }
                                }));
            }
        }
        YViewLayout.addSection(root, section);
    }

    private List<TargetMenuStore.Item> discoverTargets(String mode) {
        Intent base = TargetMenuStore.MODE_PROCESS.equals(mode)
                ? new Intent(Intent.ACTION_PROCESS_TEXT)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, "YFloat")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                : new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "YFloat");

        List<ResolveInfo> resolved;
        try {
            resolved = getPackageManager().queryIntentActivities(
                    base, PackageManager.MATCH_DEFAULT_ONLY);
        } catch (Throwable t) {
            resolved = new ArrayList<>();
        }
        if (resolved == null) resolved = new ArrayList<>();

        ArrayList<TargetMenuStore.Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            if (ri == null || ri.activityInfo == null) continue;
            if (getPackageName().equals(ri.activityInfo.packageName)) continue;
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            String key = pkg + "|" + cls;
            if (!seen.add(key)) continue;
            CharSequence label;
            try { label = ri.loadLabel(getPackageManager()); }
            catch (Throwable ignored) { label = cls; }
            out.add(new TargetMenuStore.Item(
                    label == null ? cls : label.toString(), pkg, cls));
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void addRenameRow(LinearLayout parent,
                              String title,
                              String subtitle,
                              Runnable editAction) {
        LinearLayout row = YViewLayout.baseRow(this);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = YViewLayout.rowTitle(this, title);
        texts.addView(name);
        if (subtitle != null && !subtitle.isBlank()) {
            TextView sub = YViewLayout.rowSubtitle(this, subtitle);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton edit = YViewLayout.compactButton(this,
                getString(R.string.yfloat_menu_edit));
        edit.setOnClickListener(v -> {
            if (editAction != null) editAction.run();
        });
        row.addView(edit, new LinearLayout.LayoutParams(-2, -2));
        YViewLayout.addRow(parent, row);
    }

    private void addEmpty(LinearLayout parent, String message) {
        LinearLayout row = YViewLayout.baseRow(this);
        TextView empty = YViewLayout.rowSubtitle(this, message);
        row.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        YViewLayout.addRow(parent, row);
    }

    private void showRenameDialog(String current,
                                  String systemLabel,
                                  java.util.function.Consumer<String> onSave,
                                  Runnable onReset) {
        EditText input = new EditText(this);
        YViewLayout.styleInput(this, input);
        input.setSingleLine(true);
        input.setText(current == null ? "" : current);
        input.setSelectAllOnFocus(true);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(YViewLayout.dp(this, 20), YViewLayout.dp(this, 4),
                YViewLayout.dp(this, 20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        if (systemLabel != null && !systemLabel.isBlank()) {
            TextView system = YViewLayout.rowSubtitle(this,
                    getString(R.string.yfloat_menu_system_name, systemLabel));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = YViewLayout.dp(this, 6);
            box.addView(system, lp);
        }

        AlertDialog.Builder builder = YViewDialogs.builder(this)
                .setTitle(R.string.yfloat_menu_edit_name)
                .setView(box)
                .setNegativeButton(R.string.yfloat_menu_cancel, null)
                .setPositiveButton(R.string.yfloat_menu_save, (dialog, which) -> {
                    String name = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this,
                                R.string.yfloat_menu_name_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (onSave != null) onSave.accept(name);
                });
        if (onReset != null) {
            builder.setNeutralButton(R.string.yfloat_menu_restore_system_name,
                    (dialog, which) -> onReset.run());
        }
        builder.show();
    }
}
