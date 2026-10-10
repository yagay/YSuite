package com.yagay.YFloat;

import com.yagay.yui.YViewLayout;
import com.yagay.yui.YViewSection;
import com.yagay.yui.YView;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Unified menu manager using the same visual hierarchy as the rest of YFloat. */
public final class MenuPickerActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "section";
    public static final String EXTRA_MODE = "mode";
    public static final String SECTION_CUSTOM = "custom";
    public static final String SECTION_TARGET = "target";

    private String section;
    private String targetMode;
    private Runnable localBackAction;

    public static Intent customIntent(Context c) {
        return new Intent(c, MenuPickerActivity.class)
                .putExtra(EXTRA_SECTION, SECTION_CUSTOM);
    }

    public static Intent targetIntent(Context c, String mode) {
        return new Intent(c, MenuPickerActivity.class)
                .putExtra(EXTRA_SECTION, SECTION_TARGET)
                .putExtra(EXTRA_MODE, TargetMenuStore.MODE_PROCESS.equals(mode)
                        ? TargetMenuStore.MODE_PROCESS : TargetMenuStore.MODE_SHARE);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                handleBackNavigation();
            }
        });
        section = SECTION_TARGET.equals(getIntent().getStringExtra(EXTRA_SECTION))
                ? SECTION_TARGET : SECTION_CUSTOM;
        targetMode = TargetMenuStore.MODE_PROCESS.equals(getIntent().getStringExtra(EXTRA_MODE))
                ? TargetMenuStore.MODE_PROCESS : TargetMenuStore.MODE_SHARE;
        if (isTargetSection()) showTargetManager();
        else showCustomHome();
    }

    // -----------------------------------------------------------------------------------------
    // Custom text actions
    // -----------------------------------------------------------------------------------------

    private void showCustomHome() {
        LinearLayout root = page(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_bbd8b45f263b),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_6e3a792bba29), null);

        YViewSection add = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_2e9d4ef0e0f3),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d3967f250042) );
        YViewLayout.addRow(add.body, YViewLayout.navRow(this,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b3221af51e30),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_00fc4ee7dba2),
                this::showCustomApps));
        YViewLayout.addRow(add.body, YViewLayout.navRow(this,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_cc4bc628204a),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_23d869f51ca4),
                this::showCustomIntentTypes));
        YViewLayout.addSection(root, add);

        List<CustomMenuActionStore.Item> items = CustomMenuActionStore.load(this);
        YViewSection current = YViewLayout.section(this,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_37e04e11cd7e) + items.size() + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_56891917dcd7),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f2d0c5283f9b) );
        if (items.isEmpty()) {
            addEmpty(current.body, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_1a29d210018d));
        } else {
            current.body.setOnDragListener((v, event) -> onCustomSortDrag(current.body, event));
            for (int i = 0; i < items.size(); i++) {
                CustomMenuActionStore.Item item = items.get(i);
                final int index = i;
                YViewLayout.addRow(current.body, sortableRow(
                        item.id,
                        item.label,
                        CustomMenuActionStore.typeLabel(item.type),
                        appIcon(item.packageName),
                        index,
                        items.size(),
                        null,
                        () -> {
                            if (CustomMenuActionStore.move(this, item.id, -1)) showCustomHome();
                        },
                        () -> {
                            if (CustomMenuActionStore.move(this, item.id, 1)) showCustomHome();
                        },
                        () -> {
                            CustomMenuActionStore.remove(this, item.id);
                            Toast.makeText(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d7156debd885), Toast.LENGTH_SHORT).show();
                            showCustomHome();
                        }));
            }
        }
        YViewLayout.addSection(root, current);
        show(root);
    }

    private boolean onCustomSortDrag(LinearLayout list, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof String;
            case DragEvent.ACTION_DRAG_LOCATION:
                return true;
            case DragEvent.ACTION_DROP: {
                Object state = event.getLocalState();
                if (!(state instanceof String id)) return false;
                CustomMenuActionStore.moveTo(this, id, dropIndexForY(list, event.getY()));
                showCustomHome();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                return true;
            default:
                return true;
        }
    }

    private void showCustomApps() {
        LinearLayout root = page(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b3221af51e30),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b16cf344bfe7),
                this::showCustomHome);
        addLocalBack(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d35e0660dc9b));

        List<ApplicationInfo> apps;
        try { apps = new ArrayList<>(pm().getInstalledApplications(0)); }
        catch (Throwable t) { apps = new ArrayList<>(); }
        apps.removeIf(a -> a == null || !a.enabled || getPackageName().equals(a.packageName));
        apps.sort(Comparator.comparing(this::appLabel, String.CASE_INSENSITIVE_ORDER));

        final List<ApplicationInfo> appList = apps;
        YViewSection list = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_360008c909ca), null);
        renderCustomApps(list.body, appList, "");
        addSearchField(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_48611beed801), query ->
                renderCustomApps(list.body, appList, query));
        YViewLayout.addSection(root, list);
        show(root);
    }

    private void showCustomAppActions(String pkg, String appLabel) {
        LinearLayout root = page(appLabel,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f8ee76155c0a),
                this::showCustomApps);
        addLocalBack(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_ee66c300fff6));

        ArrayList<Discovered> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IntentTypeCatalog.Spec spec : IntentTypeCatalog.all()) {
            Intent probe = spec.probeIntent().setPackage(pkg);
            for (ResolveInfo ri : query(probe)) {
                if (ri.activityInfo == null) continue;
                String key = spec.type + "|" + ri.activityInfo.packageName + "|" + ri.activityInfo.name;
                if (!seen.add(key)) continue;
                all.add(new Discovered(appLabel + ": " + spec.title,
                        resolveLabel(ri), appIcon(pkg), ri.activityInfo.packageName,
                        ri.activityInfo.name, spec.type));
            }
        }

        try {
            PackageInfo pi = pm().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                ArrayList<ActivityInfo> acts = new ArrayList<>(Arrays.asList(pi.activities));
                acts.sort(Comparator.comparing(this::activityLabel, String.CASE_INSENSITIVE_ORDER));
                for (ActivityInfo ai : acts) {
                    if (!canDirectLaunch(ai)) continue;
                    String key = CustomMenuActionStore.TYPE_ACTIVITY + "|" + ai.packageName + "|" + ai.name;
                    if (!seen.add(key)) continue;
                    all.add(new Discovered(activityLabel(ai),
                            com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_fc11417376e3) + shortClass(ai.name),
                            appIcon(pkg), ai.packageName, ai.name,
                            CustomMenuActionStore.TYPE_ACTIVITY));
                }
            }
        } catch (Throwable ignored) {}

        YViewSection entries = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_3dc5e4ed0792) + all.size() + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_4380927eaa54), null);
        if (all.isEmpty()) {
            addEmpty(entries.body, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_19b2e463a253));
        } else {
            for (Discovered d : all) YViewLayout.addRow(entries.body, customDiscoveredRow(d));
        }
        YViewLayout.addSection(root, entries);
        show(root);
    }

    private void showCustomIntentTypes() {
        LinearLayout root = page(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_cc4bc628204a),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_6b2e5f2e8a0b),
                this::showCustomHome);
        addLocalBack(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d35e0660dc9b));

        YViewSection types = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b522870a5b41), null);
        for (IntentTypeCatalog.Spec spec : IntentTypeCatalog.all()) {
            YViewLayout.addRow(types.body, actionRow(spec.title, spec.description, null,
                    () -> showCustomHandlersForType(spec), null));
        }
        YViewLayout.addSection(root, types);
        show(root);
    }

    private void showCustomHandlersForType(IntentTypeCatalog.Spec spec) {
        LinearLayout root = page(spec.title,
                spec.description + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_dynamic_27e11ac203f0),
                this::showCustomIntentTypes);
        addLocalBack(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_69b17b1959f6));

        List<ResolveInfo> handlers = query(spec.probeIntent());
        handlers.removeIf(ri -> ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName));
        handlers.sort(Comparator.comparing(this::resolveAppThenActivityLabel, String.CASE_INSENSITIVE_ORDER));

        YViewSection entries = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f698355e1d6c), null);
        renderIntentHandlers(entries.body, handlers, spec, "");
        addSearchField(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_c7122a703331), query ->
                renderIntentHandlers(entries.body, handlers, spec, query));
        YViewLayout.addSection(root, entries);
        show(root);
    }

    private android.widget.EditText addSearchField(LinearLayout root,
                                                   String hint,
                                                   java.util.function.Consumer<String> onQuery) {
        LinearLayout box = YViewLayout.settingBlock(this);
        box.setPadding(YViewLayout.dp(this, 2), YViewLayout.dp(this, 2),
                YViewLayout.dp(this, 2), YViewLayout.dp(this, 7));

        android.widget.EditText input = new android.widget.EditText(this);
        YViewLayout.styleInput(this, input);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = YViewLayout.dp(this, 3);
        root.addView(box, lp);

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable editable) {
                if (onQuery != null) onQuery.accept(editable == null ? "" : editable.toString());
            }
        });
        return input;
    }

    private int renderCustomApps(LinearLayout body,
                                 List<ApplicationInfo> apps,
                                 String query) {
        body.removeAllViews();
        int shown = 0;
        for (ApplicationInfo app : apps) {
            String label = appLabel(app);
            String pkg = app.packageName;
            if (!matchesSearch(query, label, pkg)) continue;
            Drawable icon = null;
            try { icon = app.loadIcon(pm()); } catch (Throwable ignored) {}
            YViewLayout.addRow(body,
                    actionRow(label, pkg, icon, () -> showCustomAppActions(pkg, label), null));
            shown++;
        }
        if (shown == 0) addEmpty(body, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d762aa139025));
        return shown;
    }

    private int renderIntentHandlers(LinearLayout body,
                                     List<ResolveInfo> handlers,
                                     IntentTypeCatalog.Spec spec,
                                     String query) {
        body.removeAllViews();
        int shown = 0;
        for (ResolveInfo ri : handlers) {
            if (ri == null || ri.activityInfo == null) continue;
            ActivityInfo ai = ri.activityInfo;
            String appName = appLabel(ai.applicationInfo);
            String activityName = resolveLabel(ri);
            if (!matchesSearch(query,
                    appName,
                    ai.packageName,
                    activityName,
                    ai.name,
                    shortClass(ai.name))) continue;
            Discovered d = new Discovered(
                    appName + ": " + spec.title,
                    activityName, appIcon(ai.packageName),
                    ai.packageName, ai.name, spec.type);
            YViewLayout.addRow(body, customDiscoveredRow(d));
            shown++;
        }
        if (shown == 0) {
            addEmpty(body, query == null || query.isBlank()
                    ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f8311b3840d9)
                    : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_2860feba86b5));
        }
        return shown;
    }

    private int renderTargetAdd(LinearLayout body,
                                List<TargetMenuStore.Item> available,
                                List<TargetMenuStore.Item> current,
                                String query) {
        body.removeAllViews();
        int shown = 0;
        for (TargetMenuStore.Item item : available) {
            if (!matchesSearch(query,
                    item.label,
                    item.packageName,
                    item.className,
                    shortClass(item.className))) continue;
            YViewLayout.addRow(body,
                    actionRow(item.label, shortClass(item.className), targetIcon(item), () -> {
                        saveCurrentTargetOrder(current);
                        if (TargetMenuStore.add(this, targetMode, item)) {
                            Toast.makeText(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_f0ba972e8baf), Toast.LENGTH_SHORT).show();
                            showTargetManager();
                        }
                    }, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b6ab7d48faeb)));
            shown++;
        }
        if (shown == 0) {
            addEmpty(body, available.isEmpty()
                    ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_c679fa60caac)
                    : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_7883801730b9));
        }
        return shown;
    }

    private boolean matchesSearch(String query, String... values) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.isEmpty()) return true;
        if (values == null) return false;
        for (String value : values) {
            if (value != null && value.toLowerCase(java.util.Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private View customDiscoveredRow(Discovered d) {
        return actionRow(d.label, d.subtitle, d.icon, () -> {
            CustomMenuActionStore.Item item = new CustomMenuActionStore.Item(
                    null, d.label, d.pkg, d.cls, d.type);
            boolean added = CustomMenuActionStore.add(this, item);
            Toast.makeText(this,
                    added ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_d0f2dfdf45c9) : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_ae3c00ef7b5a),
                    Toast.LENGTH_SHORT).show();
            if (added) showCustomHome();
        }, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b6ab7d48faeb));
    }

    // -----------------------------------------------------------------------------------------
    // Share/process target ordering
    // -----------------------------------------------------------------------------------------

    private void showTargetManager() {
        localBackAction = null;
        boolean customized = TargetMenuStore.isCustomized(this, targetMode);
        List<TargetMenuStore.Item> systemItems = discoverTargetItems();
        List<TargetMenuStore.Item> items = TargetMenuStore.mergeWithSystem(this, targetMode, systemItems);

        LinearLayout root = page(isShareTarget() ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_484836c29054) : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_adfd32abd31f),
                isShareTarget()
                        ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_78c60f51edc7)
                        : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a8f164a758a8),
                null);

        YViewSection tools = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_1e1616217b08), null);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(YViewLayout.dp(this, 14), YViewLayout.dp(this, 8),
                YViewLayout.dp(this, 14), YViewLayout.dp(this, 8));
        MaterialButton add = YViewLayout.secondaryButton(this,
                isShareTarget() ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_31b59703873c) : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_31b59703873c));
        add.setOnClickListener(v -> showTargetAdd());
        MaterialButton reset = YViewLayout.secondaryButton(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_5be1c4cbf6e8));
        reset.setEnabled(customized);
        reset.setOnClickListener(v -> {
            TargetMenuStore.reset(this, targetMode);
            Toast.makeText(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_ac034f023e0c), Toast.LENGTH_SHORT).show();
            showTargetManager();
        });
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(0, -2, 1f);
        aLp.setMarginEnd(YViewLayout.dp(this, 6));
        buttons.addView(add, aLp);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, -2, 1f);
        rLp.setMarginStart(YViewLayout.dp(this, 6));
        buttons.addView(reset, rLp);
        YViewLayout.addRow(tools.body, buttons);
        YViewLayout.addSection(root, tools);

        YViewSection current = YViewLayout.section(this,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a3c88e3881d7) + items.size() + com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_56891917dcd7),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b12aa75c57c1) );
        if (items.isEmpty()) {
            addEmpty(current.body, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_efe8c5b14163));
        } else {
            current.body.setOnDragListener((v, event) -> onTargetSortDrag(current.body, event, items));
            for (int i = 0; i < items.size(); i++) {
                TargetMenuStore.Item item = items.get(i);
                final int index = i;
                YViewLayout.addRow(current.body, sortableRow(
                        item.key(),
                        item.label,
                        shortClass(item.className),
                        targetIcon(item),
                        index,
                        items.size(),
                        () -> saveCurrentTargetOrder(items),
                        () -> {
                            saveCurrentTargetOrder(items);
                            if (TargetMenuStore.move(this, targetMode, item.key(), -1)) showTargetManager();
                        },
                        () -> {
                            saveCurrentTargetOrder(items);
                            if (TargetMenuStore.move(this, targetMode, item.key(), 1)) showTargetManager();
                        },
                        () -> {
                            saveCurrentTargetOrder(items);
                            TargetMenuStore.remove(this, targetMode, item.key());
                            showTargetManager();
                        }));
            }
        }
        YViewLayout.addSection(root, current);
        show(root);
    }

    private void showTargetAdd() {
        List<TargetMenuStore.Item> discovered = discoverTargetItems();
        List<TargetMenuStore.Item> current = TargetMenuStore.mergeWithSystem(this, targetMode, discovered);
        Set<String> selected = new HashSet<>();
        for (TargetMenuStore.Item item : current) selected.add(item.key());

        LinearLayout root = page(isShareTarget() ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_8c63f2b1f649) : com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_de88667230fb),
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_b122dc2b43b3),
                this::showTargetManager);
        addLocalBack(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_fab257510ac5));

        ArrayList<TargetMenuStore.Item> available = new ArrayList<>();
        for (TargetMenuStore.Item item : discovered) {
            if (!selected.contains(item.key())) available.add(item);
        }

        YViewSection list = YViewLayout.section(this, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_fb9f01be58cf), null);
        renderTargetAdd(list.body, available, current, "");
        addSearchField(root, com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_7721bc688162), query ->
                renderTargetAdd(list.body, available, current, query));
        YViewLayout.addSection(root, list);
        show(root);
    }

    private boolean onTargetSortDrag(LinearLayout list, DragEvent event,
                                     List<TargetMenuStore.Item> snapshot) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getLocalState() instanceof String;
            case DragEvent.ACTION_DRAG_LOCATION:
                return true;
            case DragEvent.ACTION_DROP: {
                Object state = event.getLocalState();
                if (!(state instanceof String key)) return false;
                saveCurrentTargetOrder(snapshot);
                TargetMenuStore.moveTo(this, targetMode, key, dropIndexForY(list, event.getY()));
                showTargetManager();
                return true;
            }
            case DragEvent.ACTION_DRAG_ENDED:
                return true;
            default:
                return true;
        }
    }

    private void saveCurrentTargetOrder(List<TargetMenuStore.Item> snapshot) {
        TargetMenuStore.save(this, targetMode, new ArrayList<>(snapshot));
    }

    /** PackageManager remains the source list; YFloat stores only user order/hide state. */
    private List<TargetMenuStore.Item> discoverTargetItems() {
        Intent base = isShareTarget()
                ? new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "YFloat")
                : new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT, "YFloat")
                    .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);

        List<ResolveInfo> resolved;
        try { resolved = getPackageManager().queryIntentActivities(base, PackageManager.MATCH_DEFAULT_ONLY); }
        catch (Throwable t) { resolved = new ArrayList<>(); }
        if (resolved == null) resolved = new ArrayList<>();
        resolved = new ArrayList<>(resolved);
        resolved.removeIf(ri -> ri == null || ri.activityInfo == null
                || getPackageName().equals(ri.activityInfo.packageName));

        ArrayList<TargetMenuStore.Item> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            String key = pkg + "|" + cls;
            if (!seen.add(key)) continue;
            out.add(new TargetMenuStore.Item(resolveLabel(ri), pkg, cls));
        }
        return out;
    }

    // -----------------------------------------------------------------------------------------
    // Shared picker UI and package helpers
    // -----------------------------------------------------------------------------------------

    private View sortableRow(String dragKey,
                             String title,
                             String subtitle,
                             Drawable icon,
                             int index,
                             int total,
                             Runnable beforeDrag,
                             Runnable moveUp,
                             Runnable moveDown,
                             Runnable remove) {
        LinearLayout row = YViewLayout.baseRow(this);
        row.setPadding(YView.controlGap(this), YView.controlGap(this) / 2,
                YView.controlGap(this), YView.controlGap(this) / 2);

        ImageView handle = iconButton(
                R.drawable.yfloat_ic_drag_handle,
                true,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_8fa208fab808));
        handle.setOnLongClickListener(v -> {
            if (beforeDrag != null) beforeDrag.run();
            ClipData clip = ClipData.newPlainText("YFloat menu item", dragKey);
            return row.startDragAndDrop(clip, new View.DragShadowBuilder(row), dragKey, 0);
        });
        row.addView(handle, new LinearLayout.LayoutParams(dp(36), YView.touchTarget(this)));

        addIcon(row, icon, 34, 10);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = YViewLayout.text(this, title, 14, false);
        titleView.setSingleLine(true);
        texts.addView(titleView);
        if (subtitle != null && !subtitle.isBlank()) {
            TextView sub = YViewLayout.caption(this, subtitle, 11);
            sub.setSingleLine(true);
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, YView.touchTarget(this), 1f));

        ImageView up = iconButton(
                R.drawable.yfloat_ic_arrow_up,
                index > 0,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a283bf829d2e));
        up.setOnClickListener(v -> { if (moveUp != null) moveUp.run(); });
        row.addView(up, new LinearLayout.LayoutParams(dp(34), YView.touchTarget(this)));

        ImageView down = iconButton(
                R.drawable.yfloat_ic_arrow_down,
                index < total - 1,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_e8ccedf0f78c));
        down.setOnClickListener(v -> { if (moveDown != null) moveDown.run(); });
        row.addView(down, new LinearLayout.LayoutParams(dp(34), YView.touchTarget(this)));

        ImageView delete = iconButton(
                R.drawable.yfloat_ic_close,
                true,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a5758272df8c));
        delete.setOnClickListener(v -> { if (remove != null) remove.run(); });
        row.addView(delete, new LinearLayout.LayoutParams(dp(36), YView.touchTarget(this)));
        return row;
    }

    private View actionRow(String title,
                           String subtitle,
                           Drawable icon,
                           Runnable action,
                           String sideText) {
        LinearLayout row = YViewLayout.baseRow(this);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(YViewLayout.rowBackground(this));

        addIcon(row, icon, 36, 12);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = YViewLayout.text(this, title, 15, false);
        a.setSingleLine(true);
        texts.addView(a);
        if (subtitle != null && !subtitle.isBlank()) {
            TextView b = YViewLayout.caption(this, subtitle, 12);
            b.setSingleLine(true);
            b.setPadding(0, dp(2), dp(8), 0);
            texts.addView(b);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        if (sideText != null) {
            MaterialButton side = YViewLayout.compactButton(this, sideText);
            side.setOnClickListener(v -> { if (action != null) action.run(); });
            row.addView(side, new LinearLayout.LayoutParams(-2, -2));
        } else {
            ImageView arrow = iconButton(R.drawable.yfloat_ic_chevron_right, true, null);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), YView.touchTarget(this)));
        }
        row.setOnClickListener(v -> { if (action != null) action.run(); });
        return row;
    }

    private void addIcon(LinearLayout row, Drawable icon, int sizeDp, int endMarginDp) {
        if (icon == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        ip.setMarginEnd(dp(endMarginDp));
        row.addView(iv, ip);
    }

    private void addEmpty(LinearLayout parent, String message) {
        LinearLayout row = YViewLayout.baseRow(this);
        TextView empty = YViewLayout.caption(this, message, 13);
        empty.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        YViewLayout.addRow(parent, row);
    }

    private LinearLayout page(String title, String subtitle, Runnable backAction) {
        localBackAction = backAction;
        return YViewLayout.pageRoot(this, title, subtitle);
    }

    private void show(LinearLayout root) {
        setContentView(YViewLayout.scrollPage(this, root));
    }

    private void addLocalBack(LinearLayout root, String label) {
        MaterialButton back = YViewLayout.secondaryButton(this, label);
        back.setIconResource(R.drawable.yfloat_ic_arrow_back);
        back.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        back.setIconPadding(dp(8));
        back.setOnClickListener(v -> {
            Runnable action = localBackAction;
            if (action != null) action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = YViewLayout.dp(this, 12);
        root.addView(back, lp);
    }

    private int dropIndexForY(LinearLayout list, float y) {
        int count = list.getChildCount();
        if (count <= 1) return 0;
        for (int i = 0; i < count; i++) {
            View child = list.getChildAt(i);
            if (y < (child.getTop() + child.getBottom()) / 2f) return i;
        }
        return count - 1;
    }

    private ImageView iconButton(int iconRes, boolean enabled, String contentDescription) {
        ImageView view = new ImageView(this);
        view.setImageResource(iconRes);
        view.setEnabled(enabled);
        view.setClickable(enabled);
        view.setFocusable(enabled);
        view.setContentDescription(contentDescription);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setAlpha(enabled ? 1f : 0.38f);
        view.setColorFilter(enabled ? YViewLayout.textPrimary(this) : YViewLayout.textSecondary(this));
        android.util.TypedValue out = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)) {
            view.setBackgroundResource(out.resourceId);
        }
        return view;
    }

    private TextView sortButton(String value, boolean enabled) {
        TextView tv = YViewLayout.text(this, value, 19, false);
        tv.setGravity(Gravity.CENTER);
        tv.setEnabled(enabled);
        tv.setAlpha(enabled ? 1f : .25f);
        tv.setClickable(enabled);
        tv.setFocusable(enabled);
        tv.setBackground(YViewLayout.rowBackground(this));
        return tv;
    }

    private PackageManager pm() { return getPackageManager(); }

    private boolean canDirectLaunch(ActivityInfo ai) {
        if (ai == null || !ai.exported || !ai.enabled) return false;
        if (ai.permission == null || ai.permission.isBlank()) return true;
        return checkSelfPermission(ai.permission) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private List<ResolveInfo> query(Intent intent) {
        try {
            List<ResolveInfo> list = pm().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    private String resolveAppThenActivityLabel(ResolveInfo ri) {
        if (ri == null || ri.activityInfo == null) return "";
        return appLabel(ri.activityInfo.applicationInfo) + " " + resolveLabel(ri);
    }

    private String resolveLabel(ResolveInfo ri) {
        try {
            CharSequence c = ri.loadLabel(pm());
            if (c != null && !c.toString().isBlank()) return c.toString();
        } catch (Throwable ignored) {}
        return ri == null || ri.activityInfo == null
                ? com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_360008c909ca) : activityLabel(ri.activityInfo);
    }

    private String activityLabel(ActivityInfo ai) {
        try {
            CharSequence c = ai.loadLabel(pm());
            if (c != null && !c.toString().isBlank()) return c.toString();
        } catch (Throwable ignored) {}
        return shortClass(ai == null ? "" : ai.name);
    }

    private String appLabel(ApplicationInfo ai) {
        if (ai == null) return com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_360008c909ca);
        try {
            CharSequence c = ai.loadLabel(pm());
            if (c != null && !c.toString().isBlank()) return c.toString();
        } catch (Throwable ignored) {}
        return ai.packageName;
    }

    private Drawable appIcon(String pkg) {
        try { return pm().getApplicationIcon(pkg); }
        catch (Throwable ignored) { return null; }
    }

    private Drawable targetIcon(TargetMenuStore.Item item) {
        try {
            return pm().getActivityIcon(new ComponentName(item.packageName, item.className));
        } catch (Throwable ignored) {
            return appIcon(item.packageName);
        }
    }

    private String shortClass(String name) {
        if (name == null) return "Activity";
        int p = name.lastIndexOf('.');
        return p >= 0 ? name.substring(p + 1) : name;
    }

    private boolean isTargetSection() {
        return SECTION_TARGET.equals(section);
    }

    private boolean isShareTarget() {
        return TargetMenuStore.MODE_SHARE.equals(targetMode);
    }

    private int dp(int value) {
        return YViewLayout.dp(this, value);
    }

    private void handleBackNavigation() {
        Runnable action = localBackAction;
        if (action != null) {
            localBackAction = null;
            action.run();
        } else {
            finish();
        }
    }

    private static final class Discovered {
        final String label, subtitle, pkg, cls, type;
        final Drawable icon;
        Discovered(String label, String subtitle, Drawable icon,
                   String pkg, String cls, String type) {
            this.label = label;
            this.subtitle = subtitle;
            this.icon = icon;
            this.pkg = pkg;
            this.cls = cls;
            this.type = type;
        }
    }
}
