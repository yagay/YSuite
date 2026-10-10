package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import android.widget.LinearLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;

/** Focused settings widgets for the Circle Select active-state border. */
final class CircleBorderSettingsUi {
    private static final int[] COLOR_VALUES = {
            0xFF4285F4, 0xFF34A853, 0xFF00B8D4, 0xFF9C6ADE,
            0xFFFF8A00, 0xFFEA4335, 0xFFFFFFFF
    };

    static void add(SettingsActivity activity, FloatSettings fs, LinearLayout parent) {
        SwitchMaterial enabled = YViewLayout.switchRow(activity,
                activity.getString(R.string.yfloat_circle_border_show),
                activity.getString(R.string.yfloat_circle_border_show_desc),
                fs.circleBorderEnabled(),
                (button, checked) -> {
                    fs.setBoolean(FloatSettings.K_CIRCLE_BORDER_ENABLED, checked);
                    CircleActiveBorderOverlay.refreshStyle(activity);
                });
        YViewLayout.addRow(parent, YViewLayout.switchContainer(enabled));

        String[] colorLabels = {
                activity.getString(R.string.yfloat_color_blue_default),
                activity.getString(R.string.yfloat_color_green),
                activity.getString(R.string.yfloat_color_cyan),
                activity.getString(R.string.yfloat_color_purple),
                activity.getString(R.string.yfloat_color_orange),
                activity.getString(R.string.yfloat_color_red),
                activity.getString(R.string.yfloat_color_white)
        };
        YViewLayout.addRow(parent, YViewLayout.spinnerSetting(
                activity,
                activity.getString(R.string.yfloat_circle_border_color),
                colorLabels,
                indexForColor(fs.circleBorderColor()),
                false,
                position -> {
                    int selected = COLOR_VALUES[Math.max(0, Math.min(COLOR_VALUES.length - 1, position))];
                    if (fs.circleBorderColor() == selected) return;
                    fs.setInt(FloatSettings.K_CIRCLE_BORDER_COLOR, selected);
                    CircleActiveBorderOverlay.refreshStyle(activity);
                }));

        YViewLayout.addRow(parent, YViewLayout.sliderSetting(
                activity,
                activity.getString(R.string.yfloat_circle_border_width),
                0,
                48,
                fs.circleBorderWidthDp(),
                value -> activity.getString(R.string.yfloat_dimension_dp, value),
                value -> {
                    fs.setInt(FloatSettings.K_CIRCLE_BORDER_WIDTH_DP, value);
                    CircleActiveBorderOverlay.refreshStyle(activity);
                }));
    }

    private static int indexForColor(int color) {
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == color) return i;
        }
        return 0;
    }

    private CircleBorderSettingsUi() { }
}
