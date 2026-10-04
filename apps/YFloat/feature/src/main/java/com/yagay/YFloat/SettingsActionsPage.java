package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.widget.LinearLayout;

/** Gesture to action binding settings page. */
final class SettingsActionsPage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = YViewLayout.pageRoot(activity,
                activity.getString(R.string.yfloat_actions_page_title),
                activity.getString(R.string.yfloat_actions_page_desc));

        YViewSection tap = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_actions_tap_section), null);
        ui.actionSpinner(tap.body, activity.getString(R.string.yfloat_action_single_tap),
                FloatSettings.K_ACTION_CLICK,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_CLICK));
        ui.actionSpinner(tap.body, activity.getString(R.string.yfloat_action_double_tap),
                FloatSettings.K_ACTION_DOUBLE,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_DOUBLE));
        ui.actionSpinner(tap.body, activity.getString(R.string.yfloat_action_long_press),
                FloatSettings.K_ACTION_LONG,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_LONG));
        ui.actionSpinner(tap.body, activity.getString(R.string.yfloat_action_region_recognition),
                FloatSettings.K_ACTION_RECOGNIZE,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_RECOGNIZE));
        YViewLayout.addSection(root, tap);

        YViewSection swipe = YViewLayout.section(activity,
                activity.getString(R.string.yfloat_actions_swipe_section), null);
        ui.actionSpinner(swipe.body, activity.getString(R.string.yfloat_action_swipe_up),
                FloatSettings.K_ACTION_UP,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_UP));
        ui.actionSpinner(swipe.body, activity.getString(R.string.yfloat_action_swipe_down_short),
                FloatSettings.K_ACTION_DOWN_SHORT,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_DOWN_SHORT));
        ui.actionSpinner(swipe.body, activity.getString(R.string.yfloat_action_swipe_down_long),
                FloatSettings.K_ACTION_DOWN_LONG,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_DOWN_LONG));
        ui.actionSpinner(swipe.body, activity.getString(R.string.yfloat_action_swipe_side_short),
                FloatSettings.K_ACTION_SIDE_SHORT,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_SIDE_SHORT));
        ui.actionSpinner(swipe.body, activity.getString(R.string.yfloat_action_swipe_side_long),
                FloatSettings.K_ACTION_SIDE_LONG,
                ActionRegistry.defaultForPreference(FloatSettings.K_ACTION_SIDE_LONG));
        YViewLayout.addSection(root, swipe);
        return root;
    }

    private SettingsActionsPage() {}
}
