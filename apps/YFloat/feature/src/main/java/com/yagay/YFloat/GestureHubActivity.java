package com.yagay.YFloat;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

/** Groups gesture thresholds/feedback and action mapping under one top-level entry. */
public final class GestureHubActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = AppUi.pageRoot(this,
                getString(R.string.yfloat_gesture_hub_title),
                getString(R.string.yfloat_gesture_hub_desc));

        AppUi.Section gesture = AppUi.section(this,
                getString(R.string.yfloat_gesture_section), null);
        AppUi.addRow(gesture.body, AppUi.navRow(this,
                getString(R.string.yfloat_gesture_params),
                getString(R.string.yfloat_gesture_params_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_GESTURE))));
        AppUi.addRow(gesture.body, AppUi.navRow(this,
                getString(R.string.yfloat_gesture_mapping),
                getString(R.string.yfloat_gesture_mapping_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_ACTIONS))));
        AppUi.addSection(root, gesture);

        setContentView(AppUi.scrollPage(this, root));
    }
}
