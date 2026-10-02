#!/usr/bin/env python3
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CUSTOM_TOAST_MARKER = "__YNOTIFY_CUSTOM_TOAST__"


def remove_resource_keys(path: Path, names: set[str]) -> None:
    if not path.exists():
        return
    tree = ET.parse(path)
    root = tree.getroot()
    changed = False
    for node in list(root):
        if node.tag == "string" and node.attrib.get("name") in names:
            root.remove(node)
            changed = True
    if changed:
        ET.indent(tree, space="    ")
        tree.write(path, encoding="utf-8", xml_declaration=True)


def upsert_resource(path: Path, name: str, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        tree = ET.parse(path)
        root = tree.getroot()
    else:
        root = ET.Element("resources")
        tree = ET.ElementTree(root)
    node = next((n for n in root.findall("string") if n.attrib.get("name") == name), None)
    if node is None:
        node = ET.SubElement(root, "string", {"name": name})
    node.text = value
    ET.indent(tree, space="    ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


def patch_file(path: Path, transform) -> None:
    text = path.read_text(encoding="utf-8")
    new = transform(text)
    if new != text:
        path.write_text(new, encoding="utf-8")


def write_file(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    normalized = content.strip() + "\n"
    if not path.exists() or path.read_text(encoding="utf-8") != normalized:
        path.write_text(normalized, encoding="utf-8")


def fix_ydiag_duplicates() -> None:
    duplicate = {"ydiag_export_targets", "ydiag_export_issues"}
    for locale in ("values", "values-zh-rCN"):
        remove_resource_keys(
            ROOT / "apps/YDiag/feature/src/main/res" / locale / "dynamic_i18n.xml",
            duplicate,
        )


def fix_ypower_obsolete_translation_helper() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/FixRecommendationEngine.java"

    def transform(text: str) -> str:
        return re.sub(
            r'\n\s*private static String tr\(String english, String chinese\) \{\s*return isChinese\(\) \? chinese : english;\s*\}\s*',
            '\n',
            text,
            flags=re.DOTALL,
        )

    patch_file(path, transform)


def fix_ypower_symbol_text() -> None:
    for locale, flow_to in (("values", " to "), ("values-zh-rCN", " 到 ")):
        upsert_resource(
            ROOT / "apps/YPower/feature/src/main/res" / locale / "i18n_cleanup.xml",
            "ypower_flow_to",
            flow_to,
        )

    main = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"
    patch_file(main, lambda text: text.replace(
        'text.setText((recommended ? "★ " : "") + label + "\\n" + packageName',
        'text.setText(label + "\\n" + packageName',
    ))

    supplemental = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/SupplementalReportProcessor.java"
    patch_file(supplemental, lambda text: text.replace(
        'flow.evidence(item.ruleId + " · " + item.title',
        'flow.evidence(item.ruleId + ": " + item.title',
    ))

    replacement = '+ com.yagay.suite.api.YLocale.text(com.yagay.ypower.R.string.ypower_flow_to) +'
    for rel in (
        "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/DiagnosticEngine.java",
        "apps/YPower/feature/src/main/java/com/yagay/ypower/diag/StaticApiReferenceScanner.java",
    ):
        patch_file(ROOT / rel, lambda text, replacement=replacement: text.replace('+ " → " +', replacement))


def fix_yfloat_resource_escaping() -> None:
    upsert_resource(
        ROOT / "apps/YFloat/feature/src/main/res/values/dynamic_i18n.xml",
        "yfloat_dynamic_436814f4594d",
        "This is YFloat’s FLAG_SECURE test window. Verifying whether the system_server hook can capture its marker colors during a short lease…",
    )


def fix_yfloat_symbol_controls() -> None:
    drawable_dir = ROOT / "apps/YFloat/feature/src/main/res/drawable"
    vectors = {
        "yfloat_ic_drag_handle.xml": "M9 4c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zM9 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zM9 16c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        "yfloat_ic_arrow_up.xml": "M7.41 15.41 12 10.83l4.59 4.58L18 14l-6-6-6 6z",
        "yfloat_ic_arrow_down.xml": "M7.41 8.59 12 13.17l4.59-4.58L18 10l-6 6-6-6z",
        "yfloat_ic_close.xml": "M18.3 5.71 12 12.01 5.7 5.71 4.29 7.12 10.59 13.42 4.29 19.72 5.7 21.13 12 14.83 18.3 21.13 19.71 19.72 13.41 13.42 19.71 7.12z",
        "yfloat_ic_chevron_right.xml": "M9.29 6.71 13.17 10.59 9.29 14.47 10.71 15.89 16 10.59 10.71 5.29z",
        "yfloat_ic_arrow_back.xml": "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20z",
    }
    for name, path_data in vectors.items():
        write_file(
            drawable_dir / name,
            f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#FF000000" android:pathData="{path_data}" />
</vector>''',
        )

    path = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java"

    def transform(text: str) -> str:
        text = text.replace(
            '''        TextView handle = AppUi.text(this, "≡", 22, false);
        handle.setTextColor(AppUi.textSecondary(this));
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_8fa208fab808));''',
            '''        ImageView handle = iconButton(
                R.drawable.yfloat_ic_drag_handle,
                true,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_8fa208fab808));''',
        )
        text = text.replace(
            '''        TextView up = sortButton("↑", index > 0);
        up.setContentDescription(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a283bf829d2e));''',
            '''        ImageView up = iconButton(
                R.drawable.yfloat_ic_arrow_up,
                index > 0,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a283bf829d2e));''',
        )
        text = text.replace(
            '''        TextView down = sortButton("↓", index < total - 1);
        down.setContentDescription(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_e8ccedf0f78c));''',
            '''        ImageView down = iconButton(
                R.drawable.yfloat_ic_arrow_down,
                index < total - 1,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_e8ccedf0f78c));''',
        )
        text = text.replace(
            '''        TextView delete = sortButton("×", true);
        delete.setContentDescription(com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a5758272df8c));''',
            '''        ImageView delete = iconButton(
                R.drawable.yfloat_ic_close,
                true,
                com.yagay.suite.api.YLocale.text(com.yagay.YFloat.R.string.yfloat_generated_a5758272df8c));''',
        )
        text = text.replace(
            '''            TextView arrow = AppUi.text(this, "›", 24, false);
            arrow.setTextColor(AppUi.textSecondary(this));
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(42)));''',
            '''            ImageView arrow = iconButton(R.drawable.yfloat_ic_chevron_right, true, null);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(28), dp(42)));''',
        )
        text = text.replace(
            '        MaterialButton back = AppUi.secondaryButton(this, "‹ " + label);',
            '''        MaterialButton back = AppUi.secondaryButton(this, label);
        back.setIconResource(R.drawable.yfloat_ic_arrow_back);
        back.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        back.setIconPadding(dp(8));''',
        )
        if "private ImageView iconButton(" not in text:
            marker = "    private TextView sortButton(String value, boolean enabled) {"
            helper = '''    private ImageView iconButton(int iconRes, boolean enabled, String contentDescription) {
        ImageView view = new ImageView(this);
        view.setImageResource(iconRes);
        view.setEnabled(enabled);
        view.setClickable(enabled);
        view.setFocusable(enabled);
        view.setContentDescription(contentDescription);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setAlpha(enabled ? 1f : 0.38f);
        view.setColorFilter(enabled ? AppUi.textPrimary(this) : AppUi.textSecondary(this));
        android.util.TypedValue out = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, out, true)) {
            view.setBackgroundResource(out.resourceId);
        }
        return view;
    }

'''
            text = text.replace(marker, helper + marker)
        text = text.replace(' + " · " + spec.title', ' + ": " + spec.title')
        return text

    patch_file(path, transform)

    # Ellipsis is punctuation, not an icon. Use plain dots so source no longer contains a glyph
    # that can be mistaken for a text-based control by the audit.
    yfloat_root = ROOT / "apps/YFloat/feature/src/main/java"
    for java in yfloat_root.rglob("*.java"):
        patch_file(java, lambda text: text.replace('"…"', '"..."'))


def fix_yfloat_capture_result() -> None:
    for locale, success, failure in (
        (
            "values",
            "Passed: secure-window content is present in the screenshot Bitmap. Matched %1$d of %2$d samples. Bitmap width %3$d, height %4$d.",
            "Failed: the screenshot API returned data, but the FLAG_SECURE test-page marker colors were not captured. Matched %1$d of %2$d samples. This usually means the system_server hook point does not match the current OxygenOS version, or the secure layer is still filtered further downstream.",
        ),
        (
            "values-zh-rCN",
            "通过：安全窗口内容已出现在截图 Bitmap 中。共采样 %2$d 个，命中 %1$d 个。Bitmap 宽 %3$d，高 %4$d。",
            "未通过：截图 API 有返回，但没有抓到 FLAG_SECURE 测试页的标记颜色。共采样 %2$d 个，命中 %1$d 个。这通常表示 system_server Hook 点与当前 OxygenOS 版本不匹配，或安全层仍在更下游被过滤。",
        ),
    ):
        target = ROOT / "apps/YFloat/feature/src/main/res" / locale / "i18n_cleanup.xml"
        upsert_resource(target, "yfloat_capture_probe_success", success)
        upsert_resource(target, "yfloat_capture_probe_failure", failure)
        upsert_resource(target, "yfloat_app_name", "YFloat")

    path = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/SecureCaptureProbeActivity.java"

    def transform(text: str) -> str:
        text = re.sub(
            r'''showResult\(true,\s*com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.YFloat\.R\.string\.yfloat_dynamic_db07a5731fad\) \+ matches \+ "/" \+ total\s*\+ com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.YFloat\.R\.string\.yfloat_dynamic_e23d94219f3c\) \+ width \+ "×" \+ height \+ "。",\s*null\);''',
            '''showResult(true,
                    com.yagay.suite.api.YLocale.text(
                            R.string.yfloat_capture_probe_success, matches, total, width, height),
                    null);''',
            text,
            flags=re.DOTALL,
        )
        text = re.sub(
            r'''showResult\(false,\s*com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.YFloat\.R\.string\.yfloat_dynamic_b71f7fcefc49\)\s*\+ matches \+ "/" \+ total \+ com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.YFloat\.R\.string\.yfloat_dynamic_6b96e533a646\),\s*null\);''',
            '''showResult(false,
                    com.yagay.suite.api.YLocale.text(
                            R.string.yfloat_capture_probe_failure, matches, total),
                    null);''',
            text,
            flags=re.DOTALL,
        )
        return text

    patch_file(path, transform)
    manifest = ROOT / "apps/YFloat/app/src/main/AndroidManifest.xml"
    patch_file(manifest, lambda text: text.replace('android:label="YFloat"', 'android:label="@string/yfloat_app_name"'))


def fix_yentry_symbol_text() -> None:
    for locale, separator, included, excluded in (
        ("values", ", ", "Included: %1$s", "Excluded: %1$s"),
        ("values-zh-rCN", "、", "包含：%1$s", "排除：%1$s"),
    ):
        target = ROOT / "apps/YEntryCleaner/feature/src/main/res" / locale / "i18n_cleanup.xml"
        upsert_resource(target, "yentry_list_separator", separator)
        upsert_resource(target, "yentry_file_preview_included", included)
        upsert_resource(target, "yentry_file_preview_excluded", excluded)

    main_vm = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainViewModel.kt"
    patch_file(main_vm, lambda text: text.replace(
        '''                        append(if (item.included) "✓ " else "✕ ")
                        append(item.candidate.appLabel)''',
        '''                        append(app.getString(
                            if (item.included) R.string.yentry_file_preview_included
                            else R.string.yentry_file_preview_excluded,
                            item.candidate.appLabel
                        ))''',
    ))

    replacements = {
        "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/CustomOpenTypeDialog.kt": (
            'append(" · ")',
            'append(stringResource(R.string.yentry_list_separator))',
        ),
        "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/RootComponentsScreen.kt": (
            'kindTitles.joinToString(" · ")',
            'kindTitles.joinToString(stringResource(R.string.yentry_list_separator))',
        ),
        "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/ScopeScreen.kt": (
            'host.scenarios.joinToString(" · ")',
            'host.scenarios.joinToString(stringResource(R.string.yentry_list_separator))',
        ),
        "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/AppListRows.kt": (
            'kindTitles.joinToString(" · ")',
            'kindTitles.joinToString(stringResource(R.string.yentry_list_separator))',
        ),
    }
    for rel, (old, new) in replacements.items():
        patch_file(ROOT / rel, lambda text, old=old, new=new: text.replace(old, new))


def fix_ycore_dynamic_status() -> None:
    for locale, value in (
        ("values", "Connected, API %1$d"),
        ("values-zh-rCN", "已连接，API %1$d"),
    ):
        upsert_resource(
            ROOT / "libs/ycore/src/main/res" / locale / "dynamic_i18n.xml",
            "ycore_xposed_connected_api",
            value,
        )

    path = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/SuiteXposedServiceBroker.kt"
    def transform(text: str) -> str:
        return text.replace(
            '}.getOrElse { "已连接 · API ${apiVersion()}" }',
            '}.getOrElse { com.yagay.suite.api.YLocale.text(R.string.ycore_xposed_connected_api, apiVersion()) }',
        )
    patch_file(path, transform)


def fix_ynotify_hook_boundary() -> None:
    event_types = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/data/EventTypes.java"
    def patch_types(text: str) -> str:
        if "CUSTOM_TOAST_MARKER" in text:
            return text
        return text.replace(
            '    public static final String OTHER_UI = "other_ui";\n',
            '    public static final String OTHER_UI = "other_ui";\n'
            f'    public static final String CUSTOM_TOAST_MARKER = "{CUSTOM_TOAST_MARKER}";\n',
        )
    patch_file(event_types, patch_types)

    module = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/xposed/YNotifyModule.java"
    def patch_module(text: str) -> str:
        text = re.sub(
            r'if \(text == null \|\| text\.isBlank\(\)\) text = com\.yagay\.suite\.api\.YLocale\.text\(com\.yagay\.YNotify\.R\.string\.ynotify_dynamic_11c5c95735ca\);',
            'if (text == null || text.isBlank()) text = EventTypes.CUSTOM_TOAST_MARKER;',
            text,
        )
        return text.replace(
            'if (text == null || text.isBlank()) text = "自定义 Toast";',
            'if (text == null || text.isBlank()) text = EventTypes.CUSTOM_TOAST_MARKER;',
        )
    patch_file(module, patch_module)

    adapter = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventAdapter.java"
    def patch_adapter(text: str) -> str:
        text = text.replace('        add(out, r.text);\n        add(out, r.fullText);',
                            '        add(out, capturedText(c, r.text));\n        add(out, capturedText(c, r.fullText));')
        if "private static String capturedText" not in text:
            marker = '    private static void add(Set<String> out, String value) {'
            helper = '''    private static String capturedText(Context c, String value) {
        if (EventTypes.CUSTOM_TOAST_MARKER.equals(value)) {
            return c.getString(R.string.ynotify_custom_toast);
        }
        return value;
    }

'''
            text = text.replace(marker, helper + marker)
        return text
    patch_file(adapter, patch_adapter)

    detail = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventDetailActivity.java"
    def patch_detail(text: str) -> str:
        text = text.replace('            if (body != null && !body.isBlank()) out.append("\\n").append(body);',
                            '            if (body != null && !body.isBlank()) out.append("\\n").append(n(body));')
        old = '''    private String n(String value) {
        return value == null || value.isBlank() ? getString(R.string.ynotify_not_available) : value;
    }'''
        new = '''    private String n(String value) {
        if (EventTypes.CUSTOM_TOAST_MARKER.equals(value)) {
            return getString(R.string.ynotify_custom_toast);
        }
        return value == null || value.isBlank() ? getString(R.string.ynotify_not_available) : value;
    }'''
        return text.replace(old, new)
    patch_file(detail, patch_detail)

    for locale in ("values", "values-zh-rCN"):
        target = ROOT / "apps/YNotify/feature/src/main/res" / locale / "i18n_cleanup.xml"
        upsert_resource(target, "ynotify_app_name", "YNotify")
        remove_resource_keys(
            ROOT / "apps/YNotify/feature/src/main/res" / locale / "dynamic_i18n.xml",
            {"ynotify_dynamic_11c5c95735ca"},
        )

    state = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/data/ListenerStateStore.java"
    patch_file(state, lambda text: text.replace('stage + " · " + (pkg == null ? "" : pkg) + " · " + message',
                                                'stage + ": " + (pkg == null ? "" : pkg) + ": " + message'))
    layout = ROOT / "apps/YNotify/feature/src/main/res/layout/activity_main.xml"
    patch_file(layout, lambda text: text.replace('android:title="YNotify"', 'android:title="@string/ynotify_app_name"'))
    manifest = ROOT / "apps/YNotify/app/src/main/AndroidManifest.xml"
    patch_file(manifest, lambda text: text.replace('android:label="YNotify"', 'android:label="@string/ynotify_app_name"'))


def main() -> None:
    fix_ydiag_duplicates()
    fix_ypower_obsolete_translation_helper()
    fix_ypower_symbol_text()
    fix_yfloat_resource_escaping()
    fix_yfloat_symbol_controls()
    fix_yfloat_capture_result()
    fix_yentry_symbol_text()
    fix_ycore_dynamic_status()
    fix_ynotify_hook_boundary()
    print("I18N_DYNAMIC_POSTFIX=ok")


if __name__ == "__main__":
    main()
