package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;


/** App appearance plus text-toolbar density and menu-management settings. */
public final class AppearanceSettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        boolean embeddedInSuite = "com.yagay.YSuite".equals(getPackageName());
        LinearLayout root = YViewLayout.pageRoot(this,
                getString(embeddedInSuite ? R.string.yfloat_menu_management : R.string.yfloat_appearance_title),
                getString(embeddedInSuite ? R.string.yfloat_menu_management_desc : R.string.yfloat_appearance_desc));

        // YSuite already owns theme and visible text-action count in YAppearanceStore.
        // Standalone YFloat still exposes local controls for its own package preferences.
        if (!embeddedInSuite) {
            YViewSection theme = YViewLayout.section(this, getString(R.string.yfloat_theme_title), null);
            addThemeSpinner(theme.body);
            YViewLayout.addSection(root, theme);

            YViewSection textMenu = YViewLayout.section(this,
                    getString(R.string.yfloat_text_menu_title),
                    getString(R.string.yfloat_text_menu_desc));
            addMainItemCountSlider(textMenu.body);
            YViewLayout.addRow(textMenu.body, YViewLayout.sectionNote(this,
                    getString(R.string.yfloat_text_menu_note)));
            YViewLayout.addSection(root, textMenu);
        }

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

        YViewLayout.addRow(parent, YViewLayout.sectionNote(this,
                getString(R.string.yfloat_main_menu_count_hint)));
    }

}
