package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

/** Groups gesture thresholds/feedback and action mapping under one top-level entry. */
public final class GestureHubActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_gesture_hub_title),
                getString(R.string.yfloat_gesture_hub_desc));

        YViewSection gesture = YViewLayout.section(this,
                getString(R.string.yfloat_gesture_section), null);
        YViewLayout.addRow(gesture.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_gesture_params),
                getString(R.string.yfloat_gesture_params_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_GESTURE))));
        YViewLayout.addRow(gesture.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_gesture_mapping),
                getString(R.string.yfloat_gesture_mapping_desc),
                () -> startActivity(SettingsActivity.intent(this, SettingsActivity.SECTION_ACTIONS))));
        YViewLayout.addSection(root, gesture);

        setContentView(YViewLayout.scrollPage(this, root));
    }
}
