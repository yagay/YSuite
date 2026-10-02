package com.yagay.YFloat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

/** Navigation/lifecycle host for focused YFloat settings pages. */
public class SettingsActivity extends AppCompatActivity {
    private static final String EXTRA_SECTION = "settings_section";

    public static final int SECTION_HOME = 0;
    public static final int SECTION_ICON = 1;
    public static final int SECTION_GESTURE = 2;
    public static final int SECTION_CAPTURE = 3;
    public static final int SECTION_ENVIRONMENT = 4;
    public static final int SECTION_ACTIONS = 5;
    public static final int SECTION_PRIVILEGE = 6;

    static final int REQUEST_CUSTOM_ICON = 401;
    static final int REQUEST_SLIDE_ICONS = 402;

    private FloatSettings fs;

    public static Intent intent(Context c, int section) {
        return new Intent(c, SettingsActivity.class).putExtra(EXTRA_SECTION, section);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        fs = new FloatSettings(this);
        int section = getIntent().getIntExtra(EXTRA_SECTION, SECTION_HOME);
        LinearLayout root;
        switch (section) {
            case SECTION_ICON -> root = SettingsIconPage.build(this, fs);
            case SECTION_GESTURE -> root = SettingsGesturePage.build(this, fs);
            case SECTION_CAPTURE -> root = SettingsCapturePage.build(this, fs);
            case SECTION_ENVIRONMENT -> root = SettingsEnvironmentPage.build(this, fs);
            case SECTION_ACTIONS -> root = SettingsActionsPage.build(this, fs);
            case SECTION_PRIVILEGE -> root = PrivilegeSettingsPanel.build(this, fs);
            default -> root = buildHomePage();
        }
        setContentView(AppUi.scrollPage(this, root));
    }

    private LinearLayout buildHomePage() {
        LinearLayout root = AppUi.pageRoot(this,
                getString(R.string.yfloat_settings_title),
                getString(R.string.yfloat_settings_desc));
        AppUi.Section categories = AppUi.section(this,
                getString(R.string.yfloat_settings_categories), null);
        addCategory(categories.body,
                getString(R.string.yfloat_float_icon_settings),
                getString(R.string.yfloat_settings_icon_desc), SECTION_ICON);
        addCategory(categories.body,
                getString(R.string.yfloat_gesture_settings),
                getString(R.string.yfloat_settings_gesture_desc), SECTION_GESTURE);
        addCategory(categories.body,
                getString(R.string.yfloat_capture_ocr_settings),
                getString(R.string.yfloat_settings_capture_desc), SECTION_CAPTURE);
        addCategory(categories.body,
                getString(R.string.yfloat_environment_settings),
                getString(R.string.yfloat_settings_environment_desc), SECTION_ENVIRONMENT);
        addCategory(categories.body,
                getString(R.string.yfloat_advanced_permissions),
                getString(R.string.yfloat_settings_privilege_desc), SECTION_PRIVILEGE);
        addCategory(categories.body,
                getString(R.string.yfloat_settings_actions),
                getString(R.string.yfloat_settings_actions_desc), SECTION_ACTIONS);
        AppUi.addSection(root, categories);
        return root;
    }

    private void addCategory(LinearLayout parent, String title, String subtitle, int section) {
        AppUi.addRow(parent, AppUi.navRow(this, title, subtitle,
                () -> startActivity(intent(this, section))));
    }

    private void persistReadPermission(android.net.Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {}
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CUSTOM_ICON && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            persistReadPermission(data.getData());
            fs.selectCustomIcon(data.getData().toString());
        } else if (requestCode == REQUEST_SLIDE_ICONS && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> uris = new java.util.ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    android.net.Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri == null) continue;
                    uris.add(uri.toString());
                    persistReadPermission(uri);
                }
            } else if (data.getData() != null) {
                uris.add(data.getData().toString());
                persistReadPermission(data.getData());
            }
            if (!uris.isEmpty()) fs.selectSlideIcons(String.join("|", uris));
        }
    }
}
