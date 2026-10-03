#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
HOST_MANIFEST = ROOT / "standalone/host/src/main/AndroidManifest.xml"
HOST_BUILD = ROOT / "standalone/host/build.gradle.kts"
SUITE_BUILD = ROOT / "suite/YSuite/build.gradle.kts"
RESOURCE_RE = re.compile(r"^@(?P<kind>[a-z_]+)/(?P<name>[A-Za-z0-9_.]+)$")
RESOURCE_FIELDS = {
    "standalone_icon": {"drawable", "mipmap"},
    "standalone_round_icon": {"drawable", "mipmap"},
    "standalone_theme": {"style"},
    "standalone_locale_config": {"xml"},
}
BOOLEAN_FIELDS = {
    "standalone_allow_backup",
    "standalone_uses_cleartext_traffic",
    "standalone_nfc_required",
}


def fail(message: str) -> None:
    print(f"standalone-metadata: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def resource_exists(res_root: Path, kind: str, name: str) -> bool:
    if not res_root.is_dir():
        return False
    for directory in res_root.glob(f"{kind}*"):
        if directory.is_dir() and any(p.is_file() for p in directory.glob(f"{name}.*")):
            return True
    needle = f'name="{name}"'
    for directory in res_root.glob("values*"):
        if not directory.is_dir():
            continue
        for xml in directory.glob("*.xml"):
            text = xml.read_text(encoding="utf-8", errors="ignore")
            if needle in text and (
                re.search(rf"<{re.escape(kind)}\b[^>]*\bname=\"{re.escape(name)}\"", text)
                or (kind == "style" and re.search(rf"<style\b[^>]*\bname=\"{re.escape(name)}\"", text))
            ):
                return True
    return False


def verify_resource(feature: dict, field: str, value: str) -> None:
    match = RESOURCE_RE.fullmatch(value)
    if not match:
        fail(f"{feature['id']}: {field} must be an Android resource reference, got {value!r}")
    kind, name = match.group("kind"), match.group("name")
    if kind not in RESOURCE_FIELDS[field]:
        fail(f"{feature['id']}: {field} has invalid type @{kind}/{name}")
    roots = [
        ROOT / feature["project_dir"] / "src/main/res",
        ROOT / "standalone/host/src/main/res",
        ROOT / "libs/yui/src/main/res",
    ]
    if not any(resource_exists(root, kind, name) for root in roots):
        fail(f"{feature['id']}: {field} points to missing resource {value}")


def main() -> None:
    features = load_features()
    standalone = [item for item in features if item.get("standalone_enabled", False)]
    for item in features:
        feature_id = item["id"]
        metadata_keys = [key for key in (*RESOURCE_FIELDS.keys(), *BOOLEAN_FIELDS) if key in item]
        if metadata_keys and not item.get("standalone_enabled", False):
            fail(f"{feature_id}: standalone metadata requires standalone_enabled=true")
        for field in RESOURCE_FIELDS:
            value = item.get(field)
            if value is not None:
                verify_resource(item, field, str(value))
        for field in BOOLEAN_FIELDS:
            if field in item and type(item[field]) is not bool:
                fail(f"{feature_id}: {field} must be boolean")
        if item.get("standalone_nfc_required", False):
            manifest = ROOT / item["project_dir"] / "src/main/AndroidManifest.xml"
            text = manifest.read_text(encoding="utf-8")
            if "android.hardware.nfc" not in text or "${standaloneNfcRequired}" not in text:
                fail(f"{feature_id}: NFC requirement must be bridged through standaloneNfcRequired")

    host_manifest = HOST_MANIFEST.read_text(encoding="utf-8")
    for token in (
        "standaloneAllowBackup", "standaloneIcon", "standaloneLocaleConfig",
        "standaloneRoundIcon", "standaloneTheme", "standaloneUsesCleartextTraffic",
    ):
        if f"${{{token}}}" not in host_manifest:
            fail(f"standalone host manifest is missing placeholder {token}")
    if 'android:label="@string/standalone_app_name"' not in host_manifest:
        fail("standalone host label must use generated catalog string")
    if 'android:description="@string/standalone_app_description"' not in host_manifest:
        fail("standalone host description must use generated catalog string")

    host_build = HOST_BUILD.read_text(encoding="utf-8")
    if 'resValue("string", "standalone_app_name", selected.name)' not in host_build:
        fail("standalone name must be generated from catalog name")
    if 'resValue("string", "standalone_app_description", selected.description)' not in host_build:
        fail("standalone description must be generated from catalog description")
    if 'manifestPlaceholders["standaloneNfcRequired"] = selected.nfcRequired.toString()' not in host_build:
        fail("standalone host must map standalone_nfc_required")
    if 'manifestPlaceholders["standaloneNfcRequired"] = "false"' not in SUITE_BUILD.read_text(encoding="utf-8"):
        fail("YSuite host must keep standaloneNfcRequired=false")

    print(f"standalone-metadata: OK standalone={len(standalone)} asset-fields={len(RESOURCE_FIELDS)}")


if __name__ == "__main__":
    main()
