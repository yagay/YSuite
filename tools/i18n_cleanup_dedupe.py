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


base = ROOT / "apps/YNotify/feature/src/main/res"
for locale in ("values", "values-zh-rCN"):
    remove_keys(base / locale / "dynamic_i18n.xml")

print("I18N_CLEANUP_DEDUPE=ok")
