#!/usr/bin/env python3
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config/standalone-features.properties"


def list_field(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(";") if item.strip()]


def load_specs():
    specs = {}
    for raw in CONFIG.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if len(parts) != 7:
            raise SystemExit("Invalid standalone feature config")
        specs[parts[0]] = {
            "runtime_modules": list_field(parts[4]),
            "xposed_classes": list_field(parts[5]),
            "scopes": list_field(parts[6]),
        }
    return specs


if len(sys.argv) != 3:
    raise SystemExit(
        "usage: verify_standalone_apk.py <feature-id> <apk-path>"
    )

feature_id = sys.argv[1]
apk_path = Path(sys.argv[2])
spec = load_specs().get(feature_id)
if spec is None:
    raise SystemExit(f"Unknown standalone feature: {feature_id}")
if not apk_path.is_file():
    raise SystemExit(f"APK not found: {apk_path}")

with zipfile.ZipFile(apk_path) as archive:
    names = set(archive.namelist())
    expected_entries = spec["xposed_classes"]
    if expected_entries:
        required = {
            "META-INF/xposed/java_init.list",
            "META-INF/xposed/module.prop",
            "META-INF/xposed/scope.list",
        }
        missing = sorted(required - names)
        if missing:
            raise SystemExit(
                "Missing standalone Xposed metadata: " + ", ".join(missing)
            )

        init_lines = [
            line.strip()
            for line in archive.read(
                "META-INF/xposed/java_init.list"
            ).decode("utf-8").splitlines()
            if line.strip()
        ]
        if init_lines != expected_entries:
            raise SystemExit(
                "java_init.list mismatch: "
                f"expected={expected_entries} actual={init_lines}"
            )

        module_prop = archive.read(
            "META-INF/xposed/module.prop"
        ).decode("utf-8")
        if "minApiVersion=102" not in module_prop:
            raise SystemExit("Standalone module.prop does not target API 102")

        scope_lines = [
            line.strip()
            for line in archive.read(
                "META-INF/xposed/scope.list"
            ).decode("utf-8").splitlines()
            if line.strip()
        ]
        if scope_lines != spec["scopes"]:
            raise SystemExit(
                "scope.list mismatch: "
                f"expected={spec['scopes']} actual={scope_lines}"
            )

print(f"Standalone APK metadata OK: {feature_id}")
