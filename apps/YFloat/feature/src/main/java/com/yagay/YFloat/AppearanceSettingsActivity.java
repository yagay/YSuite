package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.yagay.yui.YView;

/** App appearance plus text-toolbar density and menu-management settings. */
public final class AppearanceSettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = YViewLayout.pageRoot(this,
                getString(R.string.yfloat_appearance_title),
                getString(R.string.yfloat_appearance_desc));

        YViewSection theme = YViewLayout.section(this, getString(R.string.yfloat_theme_title), null);
        addThemeSpinner(theme.body);
        YViewLayout.addSection(root, theme);

        YViewSection textMenu = YViewLayout.section(this,
                getString(R.string.yfloat_text_menu_title),
                getString(R.string.yfloat_text_menu_desc));
        addMainItemCountSlider(textMenu.body);
        YViewLayout.addSection(root, textMenu);

        TextView note = YViewLayout.caption(this, getString(R.string.yfloat_text_menu_note), 12);
        note.setPadding(Math.max(1, YView.controlGap(this) / 3), 0, Math.max(1, YView.controlGap(this) / 3), Math.max(1, YView.controlGap(this) / 3));
        root.addView(note);

        YViewSection menus = YViewLayout.section(this,
                getString(R.string.yfloat_menu_management),
                getString(R.string.yfloat_menu_management_desc));
        YViewLayout.addRow(menus.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_text_action_menu),
                getString(R.string.yfloat_text_action_menu_desc),
                () -> startActivity(MenuPickerActivity.customIntent(this))));
        YViewLayout.addRow(menus.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_share_menu),
                getString(R.string.yfloat_share_menu_desc),
                () -> startActivity(MenuPickerActivity.targetIntent(this, TargetMenuStore.MODE_SHARE))));
        YViewLayout.addRow(menus.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_process_menu),
                getString(R.string.yfloat_process_menu_desc),
                () -> startActivity(MenuPickerActivity.targetIntent(this, TargetMenuStore.MODE_PROCESS))));
        YViewLayout.addRow(menus.body, YViewLayout.navRow(this,
                getString(R.string.yfloat_rename_menu_items),
                getString(R.string.yfloat_rename_menu_items_desc),
                () -> startActivity(new android.content.Intent(this, MenuLabelEditorActivity.class))));
        YViewLayout.addSection(root, menus);

        setContentView(YViewLayout.scrollPage(this, root));
    }

    private void addThemeSpinner(LinearLayout parent) {
        String[] labels = {
                getString(R.string.yfloat_theme_system),
                getString(R.string.yfloat_theme_light),
                getString(R.string.yfloat_theme_dark)
        };
        YViewLayout.addRow(parent, YViewLayout.spinnerSetting(
                this,
                getString(R.string.yfloat_theme_mode),
                labels,
                ThemeSettings.mode(this),
                false,
                position -> ThemeSettings.setMode(this, position)));
    }

    private void addMainItemCountSlider(LinearLayout parent) {
        int current = TextMenuSettings.mainItemCount(this);
        YViewLayout.addRow(parent, YViewLayout.sliderSetting(
                this,
                getString(R.string.yfloat_main_menu_count),
                TextMenuSettings.MIN_MAIN_ITEMS,
                TextMenuSettings.MAX_MAIN_ITEMS,
                current,
                value -> getString(R.string.yfloat_item_count, value),
                value -> TextMenuSettings.setMainItemCount(this, value)));

        TextView hint = YViewLayout.caption(this, getString(R.string.yfloat_main_menu_count_hint), 12);
        hint.setPadding(0, Math.max(1, YView.controlGap(this) / 4), 0, 0);
        parent.addView(hint);
    }

}
