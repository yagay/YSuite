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


def fix_ydiag_duplicates() -> None:
    # These already live in runtime_i18n.xml and should be reused by generated source.
    duplicate = {"ydiag_export_targets", "ydiag_export_issues"}
    for locale in ("values", "values-zh-rCN"):
        remove_resource_keys(
            ROOT / "apps/YDiag/feature/src/main/res" / locale / "dynamic_i18n.xml",
            duplicate,
        )


def fix_yfloat_resource_escaping() -> None:
    # aapt treats an unescaped ASCII apostrophe in values XML as a string escape boundary.
    # Keep the sentence readable and locale-safe by using the typographic apostrophe instead.
    upsert_resource(
        ROOT / "apps/YFloat/feature/src/main/res/values/dynamic_i18n.xml",
        "yfloat_dynamic_436814f4594d",
        "This is YFloat’s FLAG_SECURE test window. Verifying whether the system_server hook can capture its marker colors during a short lease…",
    )


def fix_ycore_dynamic_status() -> None:
    for locale, value in (
        ("values", "Connected · API %1$d"),
        ("values-zh-rCN", "已连接 · API %1$d"),
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
    # The Xposed module executes in system_server/target processes where the YNotify/Ysuite
    # initializer Provider is not guaranteed to exist. Store a stable protocol marker there and
    # localize it only in the host presentation process.
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

    # The generic second-wave resource for the Chinese literal is no longer used after replacing
    # the hook-process text with the protocol marker; remove it to prevent dead translation drift.
    for locale in ("values", "values-zh-rCN"):
        remove_resource_keys(
            ROOT / "apps/YNotify/feature/src/main/res" / locale / "dynamic_i18n.xml",
            {"ynotify_dynamic_11c5c95735ca"},
        )


def main() -> None:
    fix_ydiag_duplicates()
    fix_yfloat_resource_escaping()
    fix_ycore_dynamic_status()
    fix_ynotify_hook_boundary()
    print("I18N_DYNAMIC_POSTFIX=ok")


if __name__ == "__main__":
    main()
