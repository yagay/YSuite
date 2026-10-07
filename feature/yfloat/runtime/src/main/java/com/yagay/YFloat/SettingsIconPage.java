package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/** Floating icon appearance/position settings page. */
final class SettingsIconPage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = YViewLayout.pageRoot(activity,
                activity.getString(R.string.yfloat_float_icon_settings),
                activity.getString(R.string.yfloat_icon_page_desc));

        YViewSection appearance = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_appearance_section), null);
        ui.styleSpinner(appearance.body);
        ui.seek(appearance.body, activity.getString(R.string.yfloat_opacity), FloatSettings.K_ALPHA,
                10, 100, Math.round(fs.alpha() * 100), "%");
        ui.seek(appearance.body, activity.getString(R.string.yfloat_icon_size), FloatSettings.K_SIZE,
                24, 96, fs.sizeDp(), " dp");

        LinearLayout iconButtons = YViewLayout.buttonRow(activity);
        MaterialButton customIcon = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_choose_custom_icon));
        customIcon.setOnClickListener(v -> {
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            in.setType("image/*");
            in.addCategory(Intent.CATEGORY_OPENABLE);
            activity.startActivityForResult(in, SettingsActivity.REQUEST_CUSTOM_ICON);
        });
        MaterialButton slideIcon = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_choose_slideshow_icons));
        slideIcon.setOnClickListener(v -> {
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            in.setType("image/*");
            in.addCategory(Intent.CATEGORY_OPENABLE);
            in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            activity.startActivityForResult(in, SettingsActivity.REQUEST_SLIDE_ICONS);
        });
        YViewLayout.addAction(iconButtons, customIcon);
        YViewLayout.addAction(iconButtons, slideIcon);
        YViewLayout.addRow(appearance.body, iconButtons);
        ui.seek(appearance.body, activity.getString(R.string.yfloat_slideshow_interval),
                FloatSettings.K_SLIDE_INTERVAL, 500, 10000, fs.slideIntervalMs(), " ms");
        YViewLayout.addSection(root, appearance);

        YViewSection position = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_position_display_section), null);
        ui.seek(position.body, activity.getString(R.string.yfloat_edge_visible_ratio),
                FloatSettings.K_SHOW_PERCENT, 10, 100, fs.showPercentage(), "%");
        ui.check(position.body,
                activity.getString(R.string.yfloat_show_both_sides),
                activity.getString(R.string.yfloat_show_both_sides_desc),
                FloatSettings.K_BOTH_SIDE, fs.bothSide());
        ui.check(position.body,
                activity.getString(R.string.yfloat_auto_snap),
                activity.getString(R.string.yfloat_auto_snap_desc),
                FloatSettings.K_SNAP, fs.snap());
        ui.fullscreenModeCheck(position.body);
        ui.check(position.body,
                activity.getString(R.string.yfloat_edge_recall),
                activity.getString(R.string.yfloat_edge_recall_desc),
                FloatSettings.K_HIDE_MAIN_SWIPE, fs.edgeSwipeRecallEnabled());
        ui.check(position.body,
                activity.getString(R.string.yfloat_show_on_lock_screen), null,
                FloatSettings.K_SHOW_ON_LOCK, fs.showOnLock());
        ui.check(position.body,
                activity.getString(R.string.yfloat_click_under_icon),
                activity.getString(R.string.yfloat_click_under_icon_desc),
                FloatSettings.K_CLICK_UNDER, fs.clickScreenUnderIcon());
        YViewLayout.addSection(root, position);

        TextView note = YViewLayout.caption(activity,
                activity.getString(R.string.yfloat_position_save_note), 12);
        note.setPadding(YViewLayout.dp(activity, 4), 0, YViewLayout.dp(activity, 4), YViewLayout.dp(activity, 4));
        root.addView(note);
        return root;
    }

    private SettingsIconPage() {}
}
