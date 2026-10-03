package com.yagay.YFloat;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;
import com.yagay.yui.YView;

/** App appearance plus text-toolbar density and menu-management settings. */
public final class AppearanceSettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = AppUi.pageRoot(this,
                getString(R.string.yfloat_appearance_title),
                getString(R.string.yfloat_appearance_desc));

        AppUi.Section theme = AppUi.section(this, getString(R.string.yfloat_theme_title), null);
        addThemeSpinner(theme.body);
        AppUi.addSection(root, theme);

        AppUi.Section textMenu = AppUi.section(this,
                getString(R.string.yfloat_text_menu_title),
                getString(R.string.yfloat_text_menu_desc));
        addMainItemCountSlider(textMenu.body);
        AppUi.addSection(root, textMenu);

        TextView note = AppUi.caption(this, getString(R.string.yfloat_text_menu_note), 12);
        note.setPadding(Math.max(1, YView.controlGap(this) / 3), 0, Math.max(1, YView.controlGap(this) / 3), Math.max(1, YView.controlGap(this) / 3));
        root.addView(note);

        AppUi.Section menus = AppUi.section(this,
                getString(R.string.yfloat_menu_management),
                getString(R.string.yfloat_menu_management_desc));
        AppUi.addRow(menus.body, AppUi.navRow(this,
                getString(R.string.yfloat_text_action_menu),
                getString(R.string.yfloat_text_action_menu_desc),
                () -> startActivity(MenuPickerActivity.customIntent(this))));
        AppUi.addRow(menus.body, AppUi.navRow(this,
                getString(R.string.yfloat_share_menu),
                getString(R.string.yfloat_share_menu_desc),
                () -> startActivity(MenuPickerActivity.targetIntent(this, TargetMenuStore.MODE_SHARE))));
        AppUi.addRow(menus.body, AppUi.navRow(this,
                getString(R.string.yfloat_process_menu),
                getString(R.string.yfloat_process_menu_desc),
                () -> startActivity(MenuPickerActivity.targetIntent(this, TargetMenuStore.MODE_PROCESS))));
        AppUi.addRow(menus.body, AppUi.navRow(this,
                getString(R.string.yfloat_rename_menu_items),
                getString(R.string.yfloat_rename_menu_items_desc),
                () -> startActivity(new android.content.Intent(this, MenuLabelEditorActivity.class))));
        AppUi.addSection(root, menus);

        setContentView(AppUi.scrollPage(this, root));
    }

    private void addThemeSpinner(LinearLayout parent) {
        LinearLayout block = AppUi.settingBlock(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(AppUi.text(this, getString(R.string.yfloat_theme_mode), 14, false));
        TextView sub = AppUi.caption(this, getString(R.string.yfloat_theme_mode_desc), 12);
        sub.setPadding(0, Math.max(1, YView.controlGap(this) / 4), YView.controlGap(this), 0);
        copy.addView(sub);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));

        String[] labels = {
                getString(R.string.yfloat_theme_system),
                getString(R.string.yfloat_theme_light),
                getString(R.string.yfloat_theme_dark)
        };
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(ThemeSettings.mode(this));
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                 android.view.View view,
                                                 int position, long id) {
                ThemeSettings.setMode(AppearanceSettingsActivity.this, position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        row.addView(spinner, new LinearLayout.LayoutParams(-2, YView.touchTarget(this)));
        block.addView(row);
        AppUi.addRow(parent, block);
    }

    private void addMainItemCountSlider(LinearLayout parent) {
        int current = TextMenuSettings.mainItemCount(this);
        LinearLayout block = AppUi.settingBlock(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = AppUi.text(this, getString(R.string.yfloat_main_menu_count), 14, false);
        TextView value = AppUi.caption(this, getString(R.string.yfloat_item_count, current), 13);
        value.setGravity(Gravity.END);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(value, new LinearLayout.LayoutParams(-2, -2));
        block.addView(top);

        Slider slider = new Slider(this);
        slider.setValueFrom(TextMenuSettings.MIN_MAIN_ITEMS);
        slider.setValueTo(TextMenuSettings.MAX_MAIN_ITEMS);
        slider.setStepSize(1f);
        slider.setValue(current);
        slider.addOnChangeListener((s, next, fromUser) -> {
            int count = Math.round(next);
            TextMenuSettings.setMainItemCount(this, count);
            value.setText(getString(R.string.yfloat_item_count, TextMenuSettings.mainItemCount(this)));
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) { }
            @Override public void onStopTrackingTouch(Slider slider) {
                int count = Math.round(slider.getValue());
                TextMenuSettings.setMainItemCount(AppearanceSettingsActivity.this, count);
                value.setText(getString(R.string.yfloat_item_count,
                        TextMenuSettings.mainItemCount(AppearanceSettingsActivity.this)));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Math.max(1, YView.controlGap(this) / 3);
        block.addView(slider, lp);

        TextView hint = AppUi.caption(this, getString(R.string.yfloat_main_menu_count_hint), 12);
        hint.setPadding(0, Math.max(1, YView.controlGap(this) / 4), 0, 0);
        block.addView(hint);

        AppUi.addRow(parent, block);
    }
}
