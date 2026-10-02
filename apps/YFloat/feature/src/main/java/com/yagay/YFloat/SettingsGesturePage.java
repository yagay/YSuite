package com.yagay.YFloat;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Gesture timing, distance and trail settings page. */
final class SettingsGesturePage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = AppUi.pageRoot(activity,
                activity.getString(R.string.yfloat_gesture_settings),
                activity.getString(R.string.yfloat_gesture_page_desc));

        AppUi.Section timing = AppUi.section(activity,
                activity.getString(R.string.yfloat_tap_hold_section), null);
        ui.seek(timing.body, activity.getString(R.string.yfloat_long_press_time),
                FloatSettings.K_LONG_PRESS, 150, 1000, fs.longPressMs(), " ms");
        ui.seek(timing.body, activity.getString(R.string.yfloat_double_tap_interval),
                FloatSettings.K_DOUBLE_TAP, 150, 600, fs.doubleTapMs(), " ms");
        ui.seek(timing.body, activity.getString(R.string.yfloat_tap_max_duration),
                FloatSettings.K_TAP_MAX_MS, 80, 400, fs.tapMaxMs(), " ms");
        ui.seek(timing.body, activity.getString(R.string.yfloat_gesture_start_distance),
                FloatSettings.K_GESTURE_START_DISTANCE, 10, 80, fs.gestureStartDistance(), " dp");
        AppUi.addSection(root, timing);

        AppUi.Section distance = AppUi.section(activity,
                activity.getString(R.string.yfloat_swipe_distance_section), null);
        ui.seek(distance.body, activity.getString(R.string.yfloat_down_swipe_split),
                FloatSettings.K_DOWN_SHORT_DISTANCE, 50, 600, fs.downShortDistance(), " dp");
        ui.seek(distance.body, activity.getString(R.string.yfloat_side_swipe_split),
                FloatSettings.K_SIDE_SHORT_DISTANCE, 50, 700, fs.sideShortDistance(), " dp");
        AppUi.addSection(root, distance);

        AppUi.Section feedback = AppUi.section(activity,
                activity.getString(R.string.yfloat_feedback_trail_section), null);
        ui.check(feedback.body, activity.getString(R.string.yfloat_vibration_feedback), null,
                FloatSettings.K_VIBRATE, fs.vibrate());
        ui.check(feedback.body, activity.getString(R.string.yfloat_show_gesture_trail), null,
                FloatSettings.K_TRACK, fs.track());
        ui.seek(feedback.body, activity.getString(R.string.yfloat_trail_opacity),
                FloatSettings.K_LINE_ALPHA, 10, 100, fs.lineAlpha(), "%");
        ui.seek(feedback.body, activity.getString(R.string.yfloat_trail_width),
                FloatSettings.K_LINE_WIDTH, 1, 24, fs.lineWidthDp(), " dp");
        ui.check(feedback.body, activity.getString(R.string.yfloat_trail_gradient), null,
                FloatSettings.K_LINE_GRADIENT, fs.lineGradient());
        ui.lineStyleSpinner(feedback.body);

        LinearLayout colorsBlock = AppUi.settingBlock(activity);
        colorsBlock.addView(AppUi.text(activity,
                activity.getString(R.string.yfloat_trail_colors), 14, false));
        EditText lineColors = new EditText(activity);
        AppUi.styleInput(activity, lineColors);
        lineColors.setHint(R.string.yfloat_trail_colors_hint);
        lineColors.setText(fs.lineColors());
        lineColors.setSingleLine(true);
        lineColors.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fs.setLineColors(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LinearLayout.LayoutParams colorLp = new LinearLayout.LayoutParams(-1, -2);
        colorLp.topMargin = AppUi.dp(activity, 7);
        colorsBlock.addView(lineColors, colorLp);
        AppUi.addRow(feedback.body, colorsBlock);
        AppUi.addSection(root, feedback);
        return root;
    }

    private SettingsGesturePage() {}
}
