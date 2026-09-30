#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "config/features.toml"
FEATURE_MANAGER = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt"
XPOSED_HOST = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuiteXposedModule.java"
SETTINGS = ROOT / "settings.gradle.kts"
SUITE_BUILD = ROOT / "suite/YSuite/build.gradle.kts"

ALLOWED_CAPABILITIES = {
    "ROOT",
    "LSPOSED",
    "ACCESSIBILITY",
    "NOTIFICATION_LISTENER",
    "OVERLAY",
    "NOTIFICATIONS",
}


def fail(message: str) -> None:
    print(f"feature-catalog: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    data = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
    if data.get("schema") != 1:
        fail("unsupported or missing schema version")

    features = data.get("feature") or []
    if not features:
        fail("no [[feature]] entries")

    ids = [item.get("id") for item in features]
    if len(ids) != len(set(ids)):
        fail(f"duplicate feature ids: {ids}")

    settings = SETTINGS.read_text(encoding="utf-8")
    suite_build = SUITE_BUILD.read_text(encoding="utf-8")
    manager = FEATURE_MANAGER.read_text(encoding="utf-8")
    xposed = XPOSED_HOST.read_text(encoding="utf-8")

    registry_ids = re.findall(r'FeatureSpec\(\s*id\s*=\s*"([^"]+)"', manager)
    if set(registry_ids) != set(ids):
        fail(f"FeatureRegistry ids differ from catalog: registry={registry_ids} catalog={ids}")

    catalog_hooks: list[tuple[str, str]] = []
    for item in features:
        feature_id = item["id"]
        module = item["gradle_module"]
        project_dir = item["project_dir"]
        entry_activity = item["entry_activity"]
        runtime = item.get("runtime", "")
        lifecycle = item.get("lifecycle")
        capabilities = set(item.get("capabilities") or [])

        if lifecycle not in {"legacy", "managed"}:
            fail(f"{feature_id}: lifecycle must be legacy or managed")
        unknown = capabilities - ALLOWED_CAPABILITIES
        if unknown:
            fail(f"{feature_id}: unknown capabilities: {sorted(unknown)}")
        if not (ROOT / project_dir).is_dir():
            fail(f"{feature_id}: project_dir does not exist: {project_dir}")
        if f'include("{module}")' not in settings:
            fail(f"{feature_id}: {module} missing from settings.gradle.kts")
        if f'project("{module}").projectDir = file("{project_dir}")' not in settings:
            fail(f"{feature_id}: project_dir wiring differs from settings.gradle.kts")
        if f'implementation(project("{module}"))' not in suite_build:
            fail(f"{feature_id}: {module} missing from YSuite dependencies")
        for required in (feature_id, item["name"], entry_activity):
            if f'"{required}"' not in manager:
                fail(f"{feature_id}: FeatureRegistry missing {required}")
        if runtime and f'"{runtime}"' not in manager:
            fail(f"{feature_id}: FeatureRegistry missing runtime {runtime}")

        for hook in item.get("hooks") or []:
            hook_id = hook["id"]
            hook_class = hook["class"]
            if not hook_id.startswith(feature_id + "/"):
                fail(f"{feature_id}: hook id must be namespaced: {hook_id}")
            catalog_hooks.append((hook_id, hook_class))

    host_hooks = re.findall(
        r'new\s+PluginSpec\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)',
        xposed,
    )
    if host_hooks != catalog_hooks:
        fail(
            "SuiteXposedModule PLUGINS differs from catalog:\n"
            f"  host={host_hooks}\n  catalog={catalog_hooks}"
        )

    print(
        f"feature-catalog: OK features={len(features)} hooks={len(catalog_hooks)} "
        f"managed={sum(item.get('lifecycle') == 'managed' for item in features)}"
    )


if __name__ == "__main__":
    main()
