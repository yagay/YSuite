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
TYPED_BOOLEAN_PLACEHOLDER_RE = re.compile(
    r'android:(required|enabled|exported|grantUriPermissions|multiprocess|directBootAware)\s*=\s*"\$\{[^}]+\}"'
)
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
    feature_manifests: dict[str, str] = {}

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

        manifest = ROOT / item["project_dir"] / "src/main/AndroidManifest.xml"
        if manifest.is_file():
            text = manifest.read_text(encoding="utf-8")
            feature_manifests[feature_id] = text
            typed_placeholders = sorted(set(TYPED_BOOLEAN_PLACEHOLDER_RE.findall(text)))
            if typed_placeholders:
                fields = ", ".join(f"android:{name}" for name in typed_placeholders)
                fail(
                    f"{feature_id}: Feature manifest uses placeholders for typed boolean attributes "
                    f"({fields}); resolve final typed values in the app host instead"
                )

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
    if 'android:name="android.hardware.nfc"' not in host_manifest:
        fail("standalone host manifest must own the NFC hardware feature declaration")
    if 'android:required="${standaloneNfcRequired}"' not in host_manifest:
        fail("standalone host manifest must map the final NFC hardware requirement")
    if 'tools:replace="android:required"' not in host_manifest:
        fail("standalone host NFC declaration must explicitly override the Feature default")

    ynfc_text = feature_manifests.get("ynfc", "")
    if 'android:name="android.hardware.nfc"' not in ynfc_text:
        fail("ynfc: Feature manifest must declare the NFC hardware capability")
    if 'android:required="false"' not in ynfc_text:
        fail("ynfc: Feature manifest must keep NFC optional; final requirement belongs to the app host")
    if "${standaloneNfcRequired}" in ynfc_text:
        fail("ynfc: typed standaloneNfcRequired placeholder must not appear in the Feature manifest")

    host_build = HOST_BUILD.read_text(encoding="utf-8")
    if 'resValue("string", "standalone_app_name", selected.name)' not in host_build:
        fail("standalone name must be generated from catalog name")
    if 'resValue("string", "standalone_app_description", selected.description)' not in host_build:
        fail("standalone description must be generated from catalog description")
    if 'manifestPlaceholders["standaloneNfcRequired"] = selected.nfcRequired.toString()' not in host_build:
        fail("standalone host must map standalone_nfc_required")

    suite_build = SUITE_BUILD.read_text(encoding="utf-8")
    if 'manifestPlaceholders["standaloneNfcRequired"]' in suite_build:
        fail("YSuite host must not carry standalone-only NFC placeholders")
    if 'create("compact")' in suite_build and 'isDebuggable = false' not in suite_build:
        fail("YSuite compact build must be non-debuggable so minification and optimization are effective")

    print(f"standalone-metadata: OK standalone={len(standalone)} asset-fields={len(RESOURCE_FIELDS)}")


if __name__ == "__main__":
    main()
