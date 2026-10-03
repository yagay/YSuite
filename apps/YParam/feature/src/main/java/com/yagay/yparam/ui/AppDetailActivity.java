package com.yagay.yparam.ui;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yagay.yparam.R;
import com.yagay.yparam.YParamApp;
import com.yagay.yparam.data.AppConfig;
import com.yagay.yparam.data.ConfigRepository;
import com.yagay.yui.YView;
import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewScreen;
import com.yagay.yui.YViewStatusTone;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import io.github.libxposed.service.XposedService;

public final class AppDetailActivity extends AppCompatActivity implements YParamApp.ServiceObserver {
    private String packageName;
    private AppConfig config;
    private LinearLayout root;
    private TextView summary;
    private TextView scopeState;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, Spinner> choiceSpinners = new LinkedHashMap<>();
    private final Map<String, String[]> choicePresets = new LinkedHashMap<>();
    private Spinner nightMode, orientation, screenshots, keepScreen, locationMode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("package");
        if (packageName == null) { finish(); return; }
        config = ConfigRepository.get(packageName);
        YParamApp.addObserver(this);
        buildUi();
    }

    @Override protected void onDestroy() {
        YParamApp.removeObserver(this);
        super.onDestroy();
    }

    private void buildUi() {
        fields.clear();
        choiceSpinners.clear();
        choicePresets.clear();

        PackageManager pm = getPackageManager();
        String name = packageName;
        try { name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0))); } catch (Throwable ignored) {}
        setTitle(name);

        YViewScreen screen = YViewLayout.install(this, name, packageName);
        root = screen.getContent();

        LinearLayout statusCard = YViewLayout.card(root, getString(R.string.yparam_config_status), getString(R.string.yparam_config_status_desc));
        summary = YViewLayout.statusLine(this, "");
        scopeState = YViewLayout.statusLine(this, "");
        statusCard.addView(summary);
        statusCard.addView(scopeState);
        refreshStatus();

        LinearLayout actions = YViewLayout.actionRow(root);
        Button scope = YViewLayout.secondaryButton(this, getString(R.string.yparam_add_scope));
        scope.setOnClickListener(v -> requestScope());
        Button save = YViewLayout.primaryButton(this, getString(R.string.yparam_save));
        save.setOnClickListener(v -> save());
        YViewLayout.addAction(actions, scope);
        YViewLayout.addAction(actions, save);

        section(R.string.yparam_quick_presets);
        LinearLayout presets = horizontal();
        Button tablet = button(R.string.yparam_preset_tablet); tablet.setOnClickListener(v -> applyPreset("tablet"));
        Button compact = button(R.string.yparam_preset_compact); compact.setOnClickListener(v -> applyPreset("compact"));
        Button uk = button(R.string.yparam_preset_uk); uk.setOnClickListener(v -> applyPreset("uk"));
        presets.addView(tablet, weight()); presets.addView(compact, weight()); presets.addView(uk, weight());
        root.addView(presets);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        Configuration cf = getResources().getConfiguration();

        section(R.string.yparam_display_section);
        addChoiceField("densityDpi", R.string.yparam_density_label, String.valueOf(dm.densityDpi), val(config.densityDpi),
                R.string.yparam_density_hint, new String[]{"120","160","213","240","280","320","360","380","400","420","440","480","560","640"});
        addChoiceField("widthPixels", R.string.yparam_width_label, String.valueOf(dm.widthPixels), val(config.widthPixels),
                R.string.yparam_width_hint, new String[]{"720","900","1080","1200","1440"});
        addChoiceField("heightPixels", R.string.yparam_height_label, String.valueOf(dm.heightPixels), val(config.heightPixels),
                R.string.yparam_height_hint, new String[]{"1280","1600","1920","2160","2340","2400","2520","2670","2772","3120","3200"});
        addChoiceField("smallestWidthDp", R.string.yparam_smallest_width_label, String.valueOf(cf.smallestScreenWidthDp), val(config.smallestWidthDp),
                R.string.yparam_smallest_width_hint, new String[]{"320","360","384","392","411","480","600","720","840"});
        addChoiceField("screenWidthDp", R.string.yparam_screen_width_label, String.valueOf(cf.screenWidthDp), val(config.screenWidthDp),
                R.string.yparam_screen_width_hint, new String[]{"320","360","384","392","411","480","600","720","840"});
        addChoiceField("screenHeightDp", R.string.yparam_screen_height_label, String.valueOf(cf.screenHeightDp), val(config.screenHeightDp),
                R.string.yparam_screen_height_hint, new String[]{"640","720","800","840","891","960","1080","1280"});
        addChoiceField("fontScale", R.string.yparam_font_scale_label, String.valueOf(cf.fontScale), val(config.fontScale),
                R.string.yparam_font_scale_hint, new String[]{"0.80","0.90","0.95","1.00","1.05","1.10","1.15","1.20","1.30"});
        addField("xdpi", "xDpi", String.valueOf(dm.xdpi), val(config.xdpi), getString(R.string.yparam_advanced_real_hint));
        addField("ydpi", "yDpi", String.valueOf(dm.ydpi), val(config.ydpi), getString(R.string.yparam_advanced_real_hint));
        addChoiceField("refreshRate", R.string.yparam_refresh_rate_label, defaultRefreshRate(), val(config.refreshRate),
                R.string.yparam_refresh_rate_hint, new String[]{"60","90","120","144","165"});

        section(R.string.yparam_region_section);
        addChoiceField("localeTag", R.string.yparam_locale_label, Locale.getDefault().toLanguageTag(), val(config.localeTag),
                R.string.yparam_locale_hint, new String[]{"zh-CN","zh-TW","en-GB","en-US","ja-JP","ko-KR","de-DE","fr-FR"});
        addChoiceField("timeZoneId", R.string.yparam_timezone_label, TimeZone.getDefault().getID(), val(config.timeZoneId),
                R.string.yparam_timezone_hint, new String[]{"UTC","Asia/Shanghai","Asia/Hong_Kong","Asia/Tokyo","Asia/Seoul","Asia/Kuala_Lumpur","Asia/Singapore","Europe/London","Europe/Paris","Europe/Berlin","America/New_York","America/Chicago","America/Denver","America/Los_Angeles"});
        nightMode = addSpinner(getString(R.string.yparam_night_mode), getString(R.string.yparam_system_current, nightText(cf)),
                new String[]{getString(R.string.yparam_default), getString(R.string.yparam_light), getString(R.string.yparam_dark)}, nightIndex(config.nightMode));

        section(R.string.yparam_window_section);
        orientation = addSpinner(getString(R.string.yparam_orientation), defaultOrientation(),
                new String[]{getString(R.string.yparam_default), getString(R.string.yparam_portrait), getString(R.string.yparam_landscape), getString(R.string.yparam_full_sensor), getString(R.string.yparam_lock_current)}, orientationIndex(config.orientation));
        screenshots = addSpinner(getString(R.string.yparam_screenshot_label), getString(R.string.yparam_app_default),
                new String[]{getString(R.string.yparam_default), getString(R.string.yparam_force_allow_screenshot), getString(R.string.yparam_force_block_screenshot)}, boolIndex(config.allowScreenshots));
        keepScreen = addSpinner(getString(R.string.yparam_keep_screen_label), getString(R.string.yparam_app_default),
                new String[]{getString(R.string.yparam_default), getString(R.string.yparam_force_screen_on), getString(R.string.yparam_do_not_force_screen_on)}, boolIndex(config.keepScreenOn));
        addField("userAgent", "WebView User-Agent", getString(R.string.yparam_webview_default), val(config.userAgent), getString(R.string.yparam_user_agent_hint));

        section(R.string.yparam_location_section);
        locationMode = addSpinner(getString(R.string.yparam_location_mode), getString(R.string.yparam_real_location),
                new String[]{getString(R.string.yparam_default_real), getString(R.string.yparam_fixed_location), getString(R.string.yparam_random_radius)}, locationIndex(config.locationMode));
        addField("latitude", getString(R.string.yparam_latitude), getString(R.string.yparam_real_location), val(config.latitude), "-90 to 90");
        addField("longitude", getString(R.string.yparam_longitude), getString(R.string.yparam_real_location), val(config.longitude), "-180 to 180");
        addField("altitude", getString(R.string.yparam_altitude), getString(R.string.yparam_location_original), val(config.altitude), getString(R.string.yparam_optional));
        addChoiceField("accuracy", R.string.yparam_accuracy, getString(R.string.yparam_location_original), val(config.accuracy),
                R.string.yparam_accuracy_hint, new String[]{"3","5","10","20","50","100"});
        addChoiceField("speed", R.string.yparam_speed, getString(R.string.yparam_location_original), val(config.speed),
                R.string.yparam_speed_hint, new String[]{"0","1.4","5","10","20","30"});
        addChoiceField("bearing", R.string.yparam_bearing, getString(R.string.yparam_location_original), val(config.bearing),
                R.string.yparam_bearing_hint, new String[]{"0","45","90","135","180","225","270","315"});
        addChoiceField("randomRadiusMeters", R.string.yparam_random_radius_label, "0", val(config.randomRadiusMeters),
                R.string.yparam_random_radius_hint, new String[]{"0","10","50","100","500","1000","5000"});
        addChoiceField("locationUpdateIntervalMs", R.string.yparam_update_interval, "5000", val(config.locationUpdateIntervalMs),
                R.string.yparam_update_interval_hint, new String[]{"1000","3000","5000","10000","30000","60000"});

        section(R.string.yparam_diagnostic_section);
        LinearLayout rawInfo = YViewLayout.card(root, getString(R.string.yparam_snapshot_title), getString(R.string.yparam_snapshot_desc));
        rawInfo.addView(text(buildRawInfo(), 13, false));

        Button reset = YViewLayout.secondaryButton(this, getString(R.string.yparam_reset_all));
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.yparam_reset_title)
                .setMessage(R.string.yparam_reset_message)
                .setNegativeButton(R.string.yparam_cancel, null)
                .setPositiveButton(R.string.yparam_restore, (d, w) -> {
                    if (ConfigRepository.reset(packageName)) { config = new AppConfig(); buildUi(); toast(R.string.yparam_restored); }
                    else toast(R.string.yparam_write_unavailable);
                }).show());
        root.addView(reset);
    }

    private void addField(String key, int labelRes, String defaultValue, String current, String hint) {
        addField(key, getString(labelRes), defaultValue, current, hint);
    }

    private void addField(String key, String label, String defaultValue, String current, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Math.max(1, YView.controlGap(this) / 2), 0, YView.controlGap(this));
        box.addView(text(label, 15, true));
        box.addView(text(getString(R.string.yparam_default_real_value, defaultValue), 12, false));
        LinearLayout row = horizontal();
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setText(current == null ? "" : current);
        row.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = button(R.string.yparam_restore);
        clear.setOnClickListener(v -> edit.setText(""));
        row.addView(clear);
        box.addView(row);
        fields.put(key, edit);
        root.addView(box);
    }

    private Spinner addChoiceField(String key, int labelRes, String defaultValue, String current, int hintRes, String[] presets) {
        return addChoiceField(key, getString(labelRes), defaultValue, current, getString(hintRes), presets);
    }

    private Spinner addChoiceField(String key, String label, String defaultValue, String current, String hint, String[] presets) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Math.max(1, YView.controlGap(this) / 2), 0, YView.controlGap(this));
        box.addView(text(label, 15, true));
        box.addView(text(getString(R.string.yparam_default_real_value, defaultValue), 12, false));

        String[] options = new String[presets.length + 2];
        options[0] = getString(R.string.yparam_default);
        System.arraycopy(presets, 0, options, 1, presets.length);
        options[options.length - 1] = getString(R.string.yparam_custom);

        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));

        EditText custom = new EditText(this);
        custom.setSingleLine(true);
        custom.setHint(hint);
        custom.setText(current == null ? "" : current);

        int selected = 0;
        if (current != null && !current.isBlank()) {
            selected = presets.length + 1;
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(current)) {
                    selected = i + 1;
                    break;
                }
            }
        }
        final int customIndex = presets.length + 1;
        custom.setVisibility(selected == customIndex ? View.VISIBLE : View.GONE);
        spinner.setSelection(selected, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) custom.setText("");
                else if (position <= presets.length) custom.setText(presets[position - 1]);
                custom.setVisibility(position == customIndex ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout row = horizontal();
        row.addView(spinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button reset = button(R.string.yparam_restore);
        reset.setOnClickListener(v -> spinner.setSelection(0));
        row.addView(reset);
        box.addView(row);
        box.addView(custom);
        root.addView(box);

        fields.put(key, custom);
        choiceSpinners.put(key, spinner);
        choicePresets.put(key, presets);
        return spinner;
    }

    private Spinner addSpinner(String label, String defaultValue, String[] options, int selected) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Math.max(1, YView.controlGap(this) / 2), 0, YView.controlGap(this));
        box.addView(text(label, 15, true));
        box.addView(text(getString(R.string.yparam_default_real_value, defaultValue), 12, false));
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        s.setSelection(Math.max(0, Math.min(selected, options.length - 1)));
        box.addView(s);
        root.addView(box);
        return s;
    }

    private void save() {
        try {
            AppConfig c = new AppConfig();
            c.densityDpi = intField("densityDpi");
            c.widthPixels = intField("widthPixels");
            c.heightPixels = intField("heightPixels");
            c.smallestWidthDp = intField("smallestWidthDp");
            c.screenWidthDp = intField("screenWidthDp");
            c.screenHeightDp = intField("screenHeightDp");
            c.fontScale = floatField("fontScale");
            c.xdpi = floatField("xdpi"); c.ydpi = floatField("ydpi"); c.refreshRate = floatField("refreshRate");
            c.localeTag = stringField("localeTag"); c.timeZoneId = stringField("timeZoneId");
            c.nightMode = switch (nightMode.getSelectedItemPosition()) { case 1 -> "light"; case 2 -> "dark"; default -> null; };
            c.orientation = switch (orientation.getSelectedItemPosition()) { case 1 -> "portrait"; case 2 -> "landscape"; case 3 -> "sensor"; case 4 -> "locked"; default -> null; };
            c.allowScreenshots = triBool(screenshots.getSelectedItemPosition());
            c.keepScreenOn = triBool(keepScreen.getSelectedItemPosition());
            c.userAgent = stringField("userAgent");
            c.locationMode = switch (locationMode.getSelectedItemPosition()) { case 1 -> "fixed"; case 2 -> "random"; default -> null; };
            c.latitude = doubleField("latitude"); c.longitude = doubleField("longitude"); c.altitude = doubleField("altitude");
            c.accuracy = floatField("accuracy"); c.speed = floatField("speed"); c.bearing = floatField("bearing"); c.randomRadiusMeters = floatField("randomRadiusMeters");
            c.locationUpdateIntervalMs = intField("locationUpdateIntervalMs");
            validate(c);
            saveBaselineIfNeeded();
            if (!ConfigRepository.save(packageName, c)) { toast(R.string.yparam_write_unavailable); return; }
            config = c;
            refreshStatus();
            toast(R.string.yparam_saved);
        } catch (IllegalArgumentException e) {
            toast(e.getMessage());
        }
    }

    private void validate(AppConfig c) {
        if (c.densityDpi != null && (c.densityDpi < 72 || c.densityDpi > 1000)) throw new IllegalArgumentException(getString(R.string.yparam_validation_dpi));
        if (c.widthPixels != null && c.widthPixels < 100) throw new IllegalArgumentException(getString(R.string.yparam_validation_width));
        if (c.heightPixels != null && c.heightPixels < 100) throw new IllegalArgumentException(getString(R.string.yparam_validation_height));
        if (c.latitude != null && (c.latitude < -90 || c.latitude > 90)) throw new IllegalArgumentException(getString(R.string.yparam_validation_latitude));
        if (c.longitude != null && (c.longitude < -180 || c.longitude > 180)) throw new IllegalArgumentException(getString(R.string.yparam_validation_longitude));
        if (c.locationMode != null && (c.latitude == null || c.longitude == null)) throw new IllegalArgumentException(getString(R.string.yparam_validation_location));
        if (c.localeTag != null && Locale.forLanguageTag(c.localeTag).getLanguage().isBlank()) throw new IllegalArgumentException(getString(R.string.yparam_validation_locale));
    }

    private void requestScope() {
        if (YParamApp.getService() == null) { toast(R.string.yparam_scope_disconnected); return; }
        ConfigRepository.requestScope(packageName, new XposedService.OnScopeEventListener() {
            @Override public void onScopeRequestApproved(List<String> approved) {
                runOnUiThread(() -> {
                    refreshStatus();
                    toast(approved != null && approved.contains(packageName) ? R.string.yparam_scope_added : R.string.yparam_scope_not_approved);
                });
            }
            @Override public void onScopeRequestFailed(String message) {
                runOnUiThread(() -> toast(getString(R.string.yparam_scope_failed, message)));
            }
        });
    }

    private void applyPreset(String preset) {
        if ("tablet".equals(preset)) {
            setFieldValue("densityDpi", "320");
            setFieldValue("smallestWidthDp", "600");
            setFieldValue("fontScale", "1.00");
        } else if ("compact".equals(preset)) {
            setFieldValue("densityDpi", "380");
            setFieldValue("fontScale", "0.95");
        } else if ("uk".equals(preset)) {
            setFieldValue("localeTag", "en-GB");
            setFieldValue("timeZoneId", "Europe/London");
        }
        toast(R.string.yparam_preset_applied);
    }

    private void setFieldValue(String key, String value) {
        EditText edit = fields.get(key);
        if (edit == null) return;
        edit.setText(value == null ? "" : value);
        Spinner spinner = choiceSpinners.get(key);
        String[] presets = choicePresets.get(key);
        if (spinner == null || presets == null) return;
        int selected = value == null || value.isBlank() ? 0 : presets.length + 1;
        if (value != null) {
            for (int i = 0; i < presets.length; i++) {
                if (presets[i].equals(value)) {
                    selected = i + 1;
                    break;
                }
            }
        }
        spinner.setSelection(selected);
    }

    private void refreshStatus() {
        if (summary != null) {
            YViewLayout.setStatus(summary, getString(R.string.yparam_override_count, config.overrideCount()), config.overrideCount() > 0 ? YViewStatusTone.Good : YViewStatusTone.Neutral);
        }
        if (scopeState != null) {
            boolean connected = YParamApp.getService() != null;
            boolean inScope = connected && ConfigRepository.isInScope(packageName);
            String state = !connected ? getString(R.string.yparam_scope_state_disconnected) : inScope ? getString(R.string.yparam_scope_state_in) : getString(R.string.yparam_scope_state_out);
            YViewLayout.setStatus(scopeState, state, inScope ? YViewStatusTone.Good : connected ? YViewStatusTone.Warning : YViewStatusTone.Error);
        }
    }

    private void saveBaselineIfNeeded() {
        SharedPreferences p = YParamApp.remotePrefs();
        if (p == null) return;
        String key = "baseline." + packageName;
        if (!p.contains(key)) p.edit().putString(key, buildBaseline()).commit();
    }

    private String buildBaseline() {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Configuration cf = getResources().getConfiguration();
            JSONObject j = new JSONObject();
            j.put("capturedAt", System.currentTimeMillis());
            j.put("densityDpi", dm.densityDpi);
            j.put("widthPixels", dm.widthPixels);
            j.put("heightPixels", dm.heightPixels);
            j.put("fontScale", cf.fontScale);
            j.put("smallestWidthDp", cf.smallestScreenWidthDp);
            j.put("screenWidthDp", cf.screenWidthDp);
            j.put("screenHeightDp", cf.screenHeightDp);
            j.put("locale", Locale.getDefault().toLanguageTag());
            j.put("timezone", TimeZone.getDefault().getID());
            return j.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    private String buildRawInfo() {
        StringBuilder b = new StringBuilder();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            ApplicationInfo ai = pi.applicationInfo;
            b.append(getString(R.string.yparam_version, pi.versionName, pi.getLongVersionCode())).append('\n');
            if (ai != null) b.append(getString(R.string.yparam_uid_sdk, ai.uid, ai.minSdkVersion, ai.targetSdkVersion)).append('\n');
            b.append(getString(R.string.yparam_requested_permissions, pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length)).append('\n');
            ActivityInfo launch = launchActivityInfo();
            if (launch != null) b.append(getString(R.string.yparam_launch_activity, launch.name, launch.screenOrientation)).append('\n');
        } catch (Throwable t) {
            b.append(getString(R.string.yparam_app_info_failed, t.getClass().getSimpleName())).append('\n');
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Configuration cf = getResources().getConfiguration();
        b.append('\n').append(getString(R.string.yparam_system_display, dm.widthPixels, dm.heightPixels, dm.densityDpi)).append('\n');
        b.append(getString(R.string.yparam_configuration_info, cf.screenWidthDp, cf.screenHeightDp, cf.smallestScreenWidthDp)).append('\n');
        b.append(getString(R.string.yparam_locale_timezone, Locale.getDefault().toLanguageTag(), TimeZone.getDefault().getID())).append('\n');
        SharedPreferences p = YParamApp.remotePrefs();
        if (p != null && p.contains("baseline." + packageName)) {
            b.append('\n').append(getString(R.string.yparam_baseline_snapshot, p.getString("baseline." + packageName, "")));
        }
        return b.toString();
    }

    private ActivityInfo launchActivityInfo() {
        try {
            var i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null || i.getComponent() == null) return null;
            return getPackageManager().getActivityInfo(i.getComponent(), 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private String defaultOrientation() {
        ActivityInfo a = launchActivityInfo();
        return a == null ? getString(R.string.yparam_orientation_undeclared) : String.valueOf(a.screenOrientation);
    }

    private String defaultRefreshRate() {
        try { return String.valueOf(getDisplay().getRefreshRate()); }
        catch (Throwable t) { return getString(R.string.yparam_system_value); }
    }

    private String nightText(Configuration c) {
        int n = c.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return n == Configuration.UI_MODE_NIGHT_YES ? getString(R.string.yparam_dark)
                : n == Configuration.UI_MODE_NIGHT_NO ? getString(R.string.yparam_light)
                : getString(R.string.yparam_unspecified);
    }

    private Integer intField(String k) { String s = stringField(k); return s == null ? null : Integer.valueOf(s); }
    private Float floatField(String k) { String s = stringField(k); return s == null ? null : Float.valueOf(s); }
    private Double doubleField(String k) { String s = stringField(k); return s == null ? null : Double.valueOf(s); }
    private String stringField(String k) { String s = fields.get(k).getText().toString().trim(); return s.isEmpty() ? null : s; }
    private static Boolean triBool(int pos) { return pos == 0 ? null : pos == 1; }
    private static String val(Object o) { return o == null ? null : String.valueOf(o); }
    private static int nightIndex(String s) { return "light".equals(s) ? 1 : "dark".equals(s) ? 2 : 0; }
    private static int orientationIndex(String s) { return "portrait".equals(s) ? 1 : "landscape".equals(s) ? 2 : "sensor".equals(s) ? 3 : "locked".equals(s) ? 4 : 0; }
    private static int boolIndex(Boolean b) { return b == null ? 0 : b ? 1 : 2; }
    private static int locationIndex(String s) { return "fixed".equals(s) ? 1 : "random".equals(s) ? 2 : 0; }

    private void section(int resId) { YViewLayout.sectionHeader(root, getString(resId)); }
    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        if (bold) YView.styleItemTitle(v);
        else YView.styleCaption(v);
        return v;
    }
    private Button button(int resId) { return YViewLayout.secondaryButton(this, getString(resId)); }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(int resId) { toast(getString(resId)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override public void onServiceChanged() { runOnUiThread(this::refreshStatus); }
}
