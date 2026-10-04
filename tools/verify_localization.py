#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def string_keys(path: Path):
    root = ET.parse(path).getroot()
    return {node.attrib["name"] for node in root if node.tag in {"string", "plurals", "string-array"} and "name" in node.attrib}

for zh in ROOT.rglob("src/main/res/values-zh-rCN/strings.xml"):
    default = zh.parent.parent / "values" / "strings.xml"
    if not default.exists():
        errors.append(f"{zh.relative_to(ROOT)}: missing default values/strings.xml")
        continue
    base_keys = string_keys(default)
    zh_keys = string_keys(zh)
    missing = sorted(base_keys - zh_keys)
    extra = sorted(zh_keys - base_keys)
    if missing:
        errors.append(f"{zh.relative_to(ROOT)}: missing keys: {', '.join(missing)}")
    if extra:
        errors.append(f"{zh.relative_to(ROOT)}: extra keys: {', '.join(extra)}")

locale_config = ROOT / "core/resources/src/main/res/xml/locales_config.xml"
if not locale_config.exists():
    errors.append("Missing locales_config.xml")
else:
    content = locale_config.read_text(encoding="utf-8")
    for required in ('android:name="en"', 'android:name="zh-Hans"'):
        if required not in content:
            errors.append(f"locales_config.xml missing {required}")

if errors:
    print("\n".join(errors))
    sys.exit(1)

print("Localization parity OK")
