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


def runtime_source(item: dict) -> Path | None:
    runtime = item.get("runtime")
    if not runtime:
        return None
    stem = ROOT / item["project_dir"] / "src/main/java" / Path(*runtime.split("."))
    for suffix in (".kt", ".java"):
        candidate = Path(str(stem) + suffix)
        if candidate.is_file():
            return candidate
    return None


def first_existing(parent: Path, *names: str) -> Path | None:
    for name in names:
        candidate = parent / name
        if candidate.is_file():
            return candidate
    return None


def verify_managed_runtime(item: dict) -> None:
    feature_id = item["id"]
    lifecycle = item.get("lifecycle", "managed")
    if lifecycle != "managed":
        fail(f"{feature_id}: legacy runtimes are no longer supported; lifecycle must be managed")

    runtime = item.get("runtime")
    if not runtime:
        return

    source = runtime_source(item)
    if source is None:
        fail(f"{feature_id}: managed runtime source could not be resolved from {runtime}")
    text = source.read_text(encoding="utf-8")
    if "ManagedFeatureRuntime" not in text:
        fail(f"{feature_id}: runtime does not implement ManagedFeatureRuntime")

    project_dir = ROOT / item["project_dir"]
    build_file = first_existing(project_dir, "build.gradle.kts", "build.gradle")
    build_text = build_file.read_text(encoding="utf-8") if build_file else ""
    if "com.github.yagay.YSuite:api" not in build_text:
        fail(f"{feature_id}: managed feature must depend on the shared YSuite api module")

    standalone_settings = first_existing(project_dir.parent, "settings.gradle.kts", "settings.gradle")
    settings_text = standalone_settings.read_text(encoding="utf-8") if standalone_settings else ""
    if "producesModule" not in settings_text or "com.github.yagay.YSuite:api" not in settings_text:
        fail(f"{feature_id}: standalone settings must expose the shared YSuite api module")


def main() -> None:
    features = load_features()

    for item in features:
        project_dir = ROOT / item["project_dir"]
        if not project_dir.is_dir():
            fail(f"{item['id']}: project_dir does not exist: {item['project_dir']}")
        verify_managed_runtime(item)

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
    managed_count = sum(item.get("lifecycle", "managed") == "managed" for item in features)
    if managed_count != len(features):
        fail(f"all features must be managed: {managed_count}/{len(features)}")

    print(
        f"feature-catalog: OK authoritative=config/features.toml "
        f"features={len(features)} hooks={hook_count} managed={managed_count}"
    )


if __name__ == "__main__":
    main()
