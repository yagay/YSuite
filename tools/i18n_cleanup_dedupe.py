#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
KEYS = {
    "ynotify_repair_summary",
    "ynotify_repair_ambiguous",
    "ynotify_repair_rechecked",
}


def remove_keys(path: Path) -> None:
    if not path.exists():
        return
    tree = ET.parse(path)
    root = tree.getroot()
    changed = False
    for node in list(root):
        if node.tag == "string" and node.attrib.get("name") in KEYS:
            root.remove(node)
            changed = True
    if changed:
        ET.indent(tree, space="    ")
        tree.write(path, encoding="utf-8", xml_declaration=True)


def preserve_locale_root_import() -> None:
    path = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/model/DiagnosticReport.java"
    text = path.read_text(encoding="utf-8")
    if "Locale.ROOT" not in text or "import java.util.Locale;" in text:
        return
    marker = "import java.util.List;\n"
    if marker not in text:
        raise RuntimeError("DiagnosticReport import marker not found")
    text = text.replace(marker, marker + "import java.util.Locale;\n", 1)
    path.write_text(text, encoding="utf-8")


base = ROOT / "apps/YNotify/feature/src/main/res"
for locale in ("values", "values-zh-rCN"):
    remove_keys(base / locale / "dynamic_i18n.xml")

preserve_locale_root_import()
print("I18N_CLEANUP_DEDUPE=ok")
