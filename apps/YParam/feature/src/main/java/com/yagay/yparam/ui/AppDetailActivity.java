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

import com.yagay.yparam.YParamApp;
import com.yagay.yparam.data.AppConfig;
import com.yagay.yparam.data.ConfigRepository;
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

        LinearLayout statusCard = YViewLayout.card(root, "配置状态", "未设置的字段始终返回真实系统/应用默认值");
        summary = YViewLayout.statusLine(this, "");
        scopeState = YViewLayout.statusLine(this, "");
        statusCard.addView(summary);
        statusCard.addView(scopeState);
        refreshStatus();

        LinearLayout actions = YViewLayout.actionRow(root);
        Button scope = YViewLayout.secondaryButton(this, "加入 LSPosed 作用域");
        scope.setOnClickListener(v -> requestScope());
        Button save = YViewLayout.primaryButton(this, "保存");
        save.setOnClickListener(v -> save());
        YViewLayout.addAction(actions, scope);
        YViewLayout.addAction(actions, save);

        section("快速模板");
        LinearLayout presets = horizontal();
        Button tablet = button("平板"); tablet.setOnClickListener(v -> applyPreset("tablet"));
        Button compact = button("紧凑"); compact.setOnClickListener(v -> applyPreset("compact"));
        Button uk = button("英国环境"); uk.setOnClickListener(v -> applyPreset("uk"));
        presets.addView(tablet, weight()); presets.addView(compact, weight()); presets.addView(uk, weight());
        root.addView(presets);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        Configuration cf = getResources().getConfiguration();

        section("显示与分辨率");
        addChoiceField("densityDpi", "DPI / densityDpi", String.valueOf(dm.densityDpi), val(config.densityDpi),
                "输入任意 72–1000 的 DPI", new String[]{"120","160","213","240","280","320","360","380","400","420","440","480","560","640"});
        addChoiceField("widthPixels", "虚拟宽度 px", String.valueOf(dm.widthPixels), val(config.widthPixels),
                "输入自定义宽度", new String[]{"720","900","1080","1200","1440"});
        addChoiceField("heightPixels", "虚拟高度 px", String.valueOf(dm.heightPixels), val(config.heightPixels),
                "输入自定义高度", new String[]{"1280","1600","1920","2160","2340","2400","2520","2670","2772","3120","3200"});
        addChoiceField("smallestWidthDp", "最小宽度 dp", String.valueOf(cf.smallestScreenWidthDp), val(config.smallestWidthDp),
                "输入自定义 smallestWidthDp", new String[]{"320","360","384","392","411","480","600","720","840"});
        addChoiceField("screenWidthDp", "screenWidthDp", String.valueOf(cf.screenWidthDp), val(config.screenWidthDp),
                "输入自定义 screenWidthDp", new String[]{"320","360","384","392","411","480","600","720","840"});
        addChoiceField("screenHeightDp", "screenHeightDp", String.valueOf(cf.screenHeightDp), val(config.screenHeightDp),
                "输入自定义 screenHeightDp", new String[]{"640","720","800","840","891","960","1080","1280"});
        addChoiceField("fontScale", "字体缩放", String.valueOf(cf.fontScale), val(config.fontScale),
                "输入自定义比例，例如 1.08", new String[]{"0.80","0.90","0.95","1.00","1.05","1.10","1.15","1.20","1.30"});
        addField("xdpi", "xDpi", String.valueOf(dm.xdpi), val(config.xdpi), "高级参数；留空=真实值");
        addField("ydpi", "yDpi", String.valueOf(dm.ydpi), val(config.ydpi), "高级参数；留空=真实值");
        addChoiceField("refreshRate", "刷新率 Hz", defaultRefreshRate(), val(config.refreshRate),
                "输入自定义刷新率", new String[]{"60","90","120","144","165"});

        section("语言、地区与时间");
        addChoiceField("localeTag", "Locale / 应用语言", Locale.getDefault().toLanguageTag(), val(config.localeTag),
                "输入 BCP-47，例如 es-ES", new String[]{"zh-CN","zh-TW","en-GB","en-US","ja-JP","ko-KR","de-DE","fr-FR"});
        addChoiceField("timeZoneId", "时区", TimeZone.getDefault().getID(), val(config.timeZoneId),
                "输入 IANA 时区", new String[]{"UTC","Asia/Shanghai","Asia/Hong_Kong","Asia/Tokyo","Asia/Seoul","Asia/Kuala_Lumpur","Asia/Singapore","Europe/London","Europe/Paris","Europe/Berlin","America/New_York","America/Chicago","America/Denver","America/Los_Angeles"});
        nightMode = addSpinner("深色模式", "系统当前=" + nightText(cf), new String[]{"默认", "浅色", "深色"}, nightIndex(config.nightMode));

        section("窗口与方向");
        orientation = addSpinner("屏幕方向", defaultOrientation(), new String[]{"默认", "竖屏", "横屏", "全传感器", "锁定当前"}, orientationIndex(config.orientation));
        screenshots = addSpinner("截图 / FLAG_SECURE", "应用默认", new String[]{"默认", "强制允许截图", "强制禁止截图"}, boolIndex(config.allowScreenshots));
        keepScreen = addSpinner("保持屏幕常亮", "应用默认", new String[]{"默认", "强制常亮", "不强制常亮"}, boolIndex(config.keepScreenOn));
        addField("userAgent", "WebView User-Agent", "WebView 默认", val(config.userAgent), "只覆盖 WebSettings.getDefaultUserAgent");

        section("定位与模拟");
        locationMode = addSpinner("定位模式", "真实系统定位", new String[]{"默认/真实", "固定位置", "随机半径"}, locationIndex(config.locationMode));
        addField("latitude", "纬度", "真实系统定位", val(config.latitude), "-90..90");
        addField("longitude", "经度", "真实系统定位", val(config.longitude), "-180..180");
        addField("altitude", "海拔 m", "Location 原值", val(config.altitude), "可留空");
        addChoiceField("accuracy", "精度 m", "Location 原值", val(config.accuracy),
                "输入自定义精度", new String[]{"3","5","10","20","50","100"});
        addChoiceField("speed", "速度 m/s", "Location 原值", val(config.speed),
                "输入自定义速度", new String[]{"0","1.4","5","10","20","30"});
        addChoiceField("bearing", "方向 °", "Location 原值", val(config.bearing),
                "输入 0–360", new String[]{"0","45","90","135","180","225","270","315"});
        addChoiceField("randomRadiusMeters", "随机半径 m", "0", val(config.randomRadiusMeters),
                "输入自定义随机半径", new String[]{"0","10","50","100","500","1000","5000"});
        addChoiceField("locationUpdateIntervalMs", "随机更新间隔 ms", "5000", val(config.locationUpdateIntervalMs),
                "输入自定义毫秒数", new String[]{"1000","3000","5000","10000","30000","60000"});

        section("应用原始信息 / 诊断");
        LinearLayout rawInfo = YViewLayout.card(root, "真实信息快照", "用于对比覆盖前后的环境参数");
        rawInfo.addView(text(buildRawInfo(), 13, false));

        Button reset = YViewLayout.secondaryButton(this, "恢复这个应用全部默认");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("恢复全部默认？")
                .setMessage("将删除 YParam 对此应用的全部覆盖。应用之后直接读取真实系统/自身默认值。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (d, w) -> {
                    if (ConfigRepository.reset(packageName)) { config = new AppConfig(); buildUi(); toast("已恢复默认"); }
                    else toast("LSPosed 服务不可用，未写入");
                }).show());
        root.addView(reset);
    }

    private void addField(String key, String label, String defaultValue, String current, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(8));
        TextView labelView = text(label, 15, true);
        box.addView(labelView);
        TextView def = text("默认/真实：" + defaultValue, 12, false);
        box.addView(def);
        LinearLayout row = horizontal();
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setText(current == null ? "" : current);
        row.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button clear = button("恢复");
        clear.setOnClickListener(v -> edit.setText(""));
        row.addView(clear);
        box.addView(row);
        fields.put(key, edit);
        root.addView(box);
    }

    private Spinner addChoiceField(String key, String label, String defaultValue, String current, String hint, String[] presets) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(8));
        box.addView(text(label, 15, true));
        box.addView(text("默认/真实：" + defaultValue, 12, false));

        String[] options = new String[presets.length + 2];
        options[0] = "默认";
        System.arraycopy(presets, 0, options, 1, presets.length);
        options[options.length - 1] = "自定义…";

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
        Button reset = button("恢复");
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
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(0, dp(6), 0, dp(8));
        box.addView(text(label, 15, true)); box.addView(text("默认/真实：" + defaultValue, 12, false));
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        s.setSelection(Math.max(0, Math.min(selected, options.length - 1)));
        box.addView(s); root.addView(box); return s;
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
            if (!ConfigRepository.save(packageName, c)) { toast("LSPosed 服务不可用，未写入"); return; }
            config = c; refreshStatus(); toast("已保存；已在作用域中的目标 App 重启进程后完整生效，运行时读取项可即时更新");
        } catch (IllegalArgumentException e) { toast(e.getMessage()); }
    }

    private void validate(AppConfig c) {
        if (c.densityDpi != null && (c.densityDpi < 72 || c.densityDpi > 1000)) throw new IllegalArgumentException("DPI 建议范围：72–1000");
        if (c.widthPixels != null && c.widthPixels < 100) throw new IllegalArgumentException("虚拟宽度过小");
        if (c.heightPixels != null && c.heightPixels < 100) throw new IllegalArgumentException("虚拟高度过小");
        if (c.latitude != null && (c.latitude < -90 || c.latitude > 90)) throw new IllegalArgumentException("纬度必须在 -90..90");
        if (c.longitude != null && (c.longitude < -180 || c.longitude > 180)) throw new IllegalArgumentException("经度必须在 -180..180");
        if (c.locationMode != null && (c.latitude == null || c.longitude == null)) throw new IllegalArgumentException("启用定位模拟需要填写经纬度");
        if (c.localeTag != null && Locale.forLanguageTag(c.localeTag).getLanguage().isBlank()) throw new IllegalArgumentException("Locale 格式无效，例如 en-GB");
    }

    private void requestScope() {
        if (YParamApp.getService() == null) { toast("LSPosed 服务未连接"); return; }
        ConfigRepository.requestScope(packageName, new XposedService.OnScopeEventListener() {
            @Override public void onScopeRequestApproved(List<String> approved) {
                runOnUiThread(() -> {
                    refreshStatus();
                    toast(approved != null && approved.contains(packageName) ? "已加入作用域" : "作用域未批准");
                });
            }
            @Override public void onScopeRequestFailed(String message) {
                runOnUiThread(() -> toast("作用域请求失败：" + message));
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
        toast("模板已填入；检查后点击保存");
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
            YViewLayout.setStatus(summary, "YParam 覆盖项：" + config.overrideCount(), config.overrideCount() > 0 ? YViewStatusTone.Good : YViewStatusTone.Neutral);
        }
        if (scopeState != null) {
            boolean connected = YParamApp.getService() != null;
            boolean inScope = connected && ConfigRepository.isInScope(packageName);
            String state = !connected ? "LSPosed：未连接" : inScope ? "LSPosed：已在作用域" : "LSPosed：未在作用域";
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
            DisplayMetrics dm = getResources().getDisplayMetrics(); Configuration cf = getResources().getConfiguration();
            JSONObject j = new JSONObject();
            j.put("capturedAt", System.currentTimeMillis()); j.put("densityDpi", dm.densityDpi); j.put("widthPixels", dm.widthPixels); j.put("heightPixels", dm.heightPixels);
            j.put("fontScale", cf.fontScale); j.put("smallestWidthDp", cf.smallestScreenWidthDp); j.put("screenWidthDp", cf.screenWidthDp); j.put("screenHeightDp", cf.screenHeightDp);
            j.put("locale", Locale.getDefault().toLanguageTag()); j.put("timezone", TimeZone.getDefault().getID());
            return j.toString();
        } catch (Throwable t) { return "{}"; }
    }

    private String buildRawInfo() {
        StringBuilder b = new StringBuilder();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS);
            ApplicationInfo ai = pi.applicationInfo;
            b.append("版本：").append(pi.versionName).append(" (").append(pi.getLongVersionCode()).append(")\n");
            if (ai != null) b.append("UID：").append(ai.uid).append("\nminSdk：").append(ai.minSdkVersion).append("  targetSdk：").append(ai.targetSdkVersion).append('\n');
            b.append("请求权限：").append(pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length).append('\n');
            ActivityInfo launch = launchActivityInfo();
            if (launch != null) b.append("启动 Activity：").append(launch.name).append("\nManifest orientation：").append(launch.screenOrientation).append('\n');
        } catch (Throwable t) { b.append("应用信息读取失败：").append(t.getClass().getSimpleName()).append('\n'); }
        DisplayMetrics dm = getResources().getDisplayMetrics(); Configuration cf = getResources().getConfiguration();
        b.append("\n系统显示：").append(dm.widthPixels).append('×').append(dm.heightPixels).append("  ").append(dm.densityDpi).append("dpi\n");
        b.append("Configuration：").append(cf.screenWidthDp).append('×').append(cf.screenHeightDp).append("dp  sw=").append(cf.smallestScreenWidthDp).append("dp\n");
        b.append("Locale：").append(Locale.getDefault().toLanguageTag()).append("\nTimezone：").append(TimeZone.getDefault().getID()).append('\n');
        SharedPreferences p = YParamApp.remotePrefs();
        if (p != null && p.contains("baseline." + packageName)) b.append("\n首次修改前快照：\n").append(p.getString("baseline." + packageName, ""));
        return b.toString();
    }

    private ActivityInfo launchActivityInfo() {
        try {
            var i = getPackageManager().getLaunchIntentForPackage(packageName);
            if (i == null || i.getComponent() == null) return null;
            return getPackageManager().getActivityInfo(i.getComponent(), 0);
        } catch (Throwable t) { return null; }
    }

    private String defaultOrientation() { ActivityInfo a = launchActivityInfo(); return a == null ? "未声明/无启动 Activity" : String.valueOf(a.screenOrientation); }
    private String defaultRefreshRate() { try { return String.valueOf(getDisplay().getRefreshRate()); } catch (Throwable t) { return "系统值"; } }
    private static String nightText(Configuration c) { int n = c.uiMode & Configuration.UI_MODE_NIGHT_MASK; return n == Configuration.UI_MODE_NIGHT_YES ? "深色" : n == Configuration.UI_MODE_NIGHT_NO ? "浅色" : "未指定"; }

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

    private void section(String s) { YViewLayout.sectionHeader(root, s); }
    private TextView text(String s, int sp, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); if (bold) v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD); return v; }
    private Button button(String s) { return YViewLayout.secondaryButton(this, s); }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override public void onServiceChanged() { runOnUiThread(this::refreshStatus); }
}
