#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
FEATURE_MANAGER = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt"
GENERATED_FEATURES = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/GeneratedFeatureCatalog.kt"
GENERATED_HOOKS = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/GeneratedXposedPlugins.java"
GENERATED_MODULES = ROOT / "config/generated/feature-modules.tsv"
XPOSED_HOST = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuiteXposedModule.java"
SETTINGS = ROOT / "settings.gradle.kts"
SUITE_BUILD = ROOT / "suite/YSuite/build.gradle.kts"


def fail(message: str) -> None:
    print(f"feature-catalog: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    features = load_features()

    for item in features:
        project_dir = ROOT / item["project_dir"]
        if not project_dir.is_dir():
            fail(f"{item['id']}: project_dir does not exist: {item['project_dir']}")

    for path in (GENERATED_FEATURES, GENERATED_HOOKS, GENERATED_MODULES):
        if not path.is_file():
            fail(f"generated catalog output is missing: {path.relative_to(ROOT)}")

    settings = SETTINGS.read_text(encoding="utf-8")
    suite_build = SUITE_BUILD.read_text(encoding="utf-8")
    manager = FEATURE_MANAGER.read_text(encoding="utf-8")
    xposed = XPOSED_HOST.read_text(encoding="utf-8")

    if 'file("config/generated/feature-modules.tsv")' not in settings:
        fail("settings.gradle.kts must load generated feature module mapping")
    if 'rootProject.file("config/generated/feature-modules.tsv")' not in suite_build:
        fail("YSuite build must load generated feature dependency mapping")
    if "GeneratedFeatureCatalog.all" not in manager:
        fail("FeatureRegistry must delegate to GeneratedFeatureCatalog")
    if "GeneratedXposedPlugins.ENTRIES" not in xposed:
        fail("SuiteXposedModule must delegate to GeneratedXposedPlugins")

    # No feature may be registered manually in the four consumers again. This makes
    # config/features.toml the only human-edited feature inventory.
    for item in features:
        module = item["gradle_module"]
        if f'include("{module}")' in settings:
            fail(f"manual feature include returned to settings.gradle.kts: {module}")
        if f'implementation(project("{module}"))' in suite_build:
            fail(f"manual feature dependency returned to suite build: {module}")
        if f'id = "{item["id"]}"' in manager:
            fail(f"manual FeatureSpec returned to FeatureManager.kt: {item['id']}")
        for hook in item.get("hooks") or []:
            marker = f'new PluginSpec("{hook["id"]}"'
            if marker in xposed:
                fail(f"manual Xposed PluginSpec returned to SuiteXposedModule: {hook['id']}")

    hook_count = sum(len(item.get("hooks") or []) for item in features)
    print(
        f"feature-catalog: OK authoritative=config/features.toml "
        f"features={len(features)} hooks={hook_count}"
    )


if __name__ == "__main__":
    main()
