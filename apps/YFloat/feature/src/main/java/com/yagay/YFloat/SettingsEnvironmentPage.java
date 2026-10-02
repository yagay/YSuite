package com.yagay.YFloat;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Environment-driven visibility settings page. */
final class SettingsEnvironmentPage {
    static LinearLayout build(SettingsActivity activity, FloatSettings fs) {
        SettingsPageUi ui = new SettingsPageUi(activity, fs);
        LinearLayout root = AppUi.pageRoot(activity,
                activity.getString(R.string.yfloat_environment_settings),
                activity.getString(R.string.yfloat_environment_page_desc));

        AppUi.Section behavior = AppUi.section(activity,
                activity.getString(R.string.yfloat_display_behavior_section), null);
        ui.check(behavior.body, activity.getString(R.string.yfloat_avoid_keyboard), null,
                FloatSettings.K_IME_AVOID, fs.imeAvoid());
        ui.check(behavior.body, activity.getString(R.string.yfloat_quick_move_entry), null,
                FloatSettings.K_QUICK_MOVE, fs.quickMoveEnabled());
        AppUi.addSection(root, behavior);

        AppUi.Section hidden = AppUi.section(activity,
                activity.getString(R.string.yfloat_hide_by_app_section),
                activity.getString(R.string.yfloat_hide_by_app_desc));
        LinearLayout hideBlock = AppUi.settingBlock(activity);
        EditText edit = new EditText(activity);
        AppUi.styleInput(activity, edit);
        edit.setText(fs.hiddenPackagesRaw());
        edit.setHint(R.string.yfloat_package_list_hint);
        edit.setSingleLine(false);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setMinHeight(AppUi.dp(activity, 110));
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fs.setHiddenPackagesRaw(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        hideBlock.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        AppUi.addRow(hidden.body, hideBlock);
        AppUi.addSection(root, hidden);
        return root;
    }

    private SettingsEnvironmentPage() {}
}
