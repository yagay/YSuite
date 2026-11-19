package com.yagay.YFloat;

import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;

/**
 * YFloat-owned selection and interaction adjustments. All controls are YUI View widgets.
 * UI adjustments remain local to YFloat.
 * These preferences are never a second generic theme/appearance implementation.
 */
final class SettingsAdvancedSelectionPage {
    static LinearLayout build(SettingsActivity activity, FloatSettings settings) {
        SettingsPageUi ui = new SettingsPageUi(activity, settings);
        LinearLayout root = YViewLayout.pageRoot(activity,
                activity.getString(R.string.yfloat_advanced_selection_title),
                activity.getString(R.string.yfloat_advanced_selection_summary));

        YViewSection recognition = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_selection_detection_section),
                activity.getString(R.string.yfloat_selection_detection_hint));
        ui.seek(recognition.body, activity.getString(R.string.yfloat_image_recognition_min),
                FloatSettings.K_NODE_IMAGE_MIN_DP, 1, 64, settings.imageRecognitionMinDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_generic_recognition_min),
                FloatSettings.K_NODE_GENERIC_MIN_DP, 1, 96, settings.genericRecognitionMinDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_edge_wake_width),
                FloatSettings.K_EDGE_WAKE_WIDTH_DP, 1, 64, settings.edgeWakeWidthDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_text_handle_hit),
                FloatSettings.K_TEXT_HANDLE_HIT_DP, 8, 80, settings.textHandleHitDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_text_handle_snap),
                FloatSettings.K_TEXT_HANDLE_SNAP_DP, 16, 240, settings.textHandleSnapDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_crop_min_size),
                FloatSettings.K_CROP_MIN_DP, 4, 96, settings.cropMinDp(), " dp");
        ui.seek(recognition.body, activity.getString(R.string.yfloat_crop_drag_slop),
                FloatSettings.K_CROP_DRAG_SLOP_DP, 4, 96, settings.cropDragSlopDp(), " dp");
        YViewLayout.addSection(root, recognition);

        YViewSection reset = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_selection_reset_section), null);
        MaterialButton resetButton = YViewLayout.secondaryButton(activity,
                activity.getString(R.string.yfloat_selection_reset_button));
        resetButton.setOnClickListener(view -> {
            settings.resetAdvancedSelectionSettings(true);
            activity.recreate();
        });
        YViewLayout.addRow(reset.body, resetButton);
        YViewLayout.addSection(root, reset);
        return root;
    }

    private SettingsAdvancedSelectionPage() {}
}
