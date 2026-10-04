package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.slider.Slider;
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
        Spinner color = new Spinner(activity);
        color.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, colorLabels));
        color.setSelection(indexForColor(fs.circleBorderColor()));
        color.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v,
                                                 int position, long id) {
                int selected = COLOR_VALUES[Math.max(0, Math.min(COLOR_VALUES.length - 1, position))];
                if (fs.circleBorderColor() == selected) return;
                fs.setInt(FloatSettings.K_CIRCLE_BORDER_COLOR, selected);
                CircleActiveBorderOverlay.refreshStyle(activity);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        addSpinnerRow(activity, parent,
                activity.getString(R.string.yfloat_circle_border_color), color);

        LinearLayout block = YViewLayout.sliderBlock(activity);
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = YViewLayout.text(activity,
                activity.getString(R.string.yfloat_circle_border_width), 14, false);
        TextView value = YViewLayout.caption(activity, activity.getString(R.string.yfloat_dimension_dp, fs.circleBorderWidthDp()), 13);
        value.setGravity(Gravity.END);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(value, new LinearLayout.LayoutParams(-2, -2));
        block.addView(top);

        Slider width = new Slider(activity);
        width.setValueFrom(1f);
        width.setValueTo(8f);
        width.setStepSize(1f);
        width.setValue(fs.circleBorderWidthDp());
        width.setMinimumHeight(0);
        width.setPadding(0, 0, 0, 0);
        width.addOnChangeListener((slider, next, fromUser) -> {
            if (!fromUser) return;
            int dp = Math.round(next);
            value.setText(activity.getString(R.string.yfloat_dimension_dp, dp));
            fs.setInt(FloatSettings.K_CIRCLE_BORDER_WIDTH_DP, dp);
            CircleActiveBorderOverlay.refreshStyle(activity);
        });
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(-1, YViewLayout.dp(activity, 34));
        sliderLp.topMargin = YViewLayout.dp(activity, -1);
        block.addView(width, sliderLp);
        YViewLayout.addRow(parent, block);
    }

    private static void addSpinnerRow(SettingsActivity activity, LinearLayout parent,
                                      String label, Spinner spinner) {
        LinearLayout block = YViewLayout.settingBlock(activity);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = YViewLayout.text(activity, label, 14, false);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 0.82f));
        row.addView(spinner, new LinearLayout.LayoutParams(0, YViewLayout.dp(activity, 48), 1.18f));
        block.addView(row);
        YViewLayout.addRow(parent, block);
    }

    private static int indexForColor(int color) {
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i] == color) return i;
        }
        return 0;
    }

    private CircleBorderSettingsUi() { }
}
