#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from generate_feature_catalog import ALLOWED_COMPONENT_TYPES, load_features

ROOT = Path(__file__).resolve().parents[1]
FEATURE_MANAGER = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt"
GENERATED_FEATURES = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/GeneratedFeatureCatalog.kt"
GENERATED_HOOKS = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/GeneratedXposedPlugins.java"
GENERATED_MODULES = ROOT / "config/generated/feature-modules.tsv"
GENERATED_REPLACED_COMPONENTS = ROOT / "config/generated/host-replaced-components.tsv"
XPOSED_HOST = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuiteXposedModule.java"
BRIDGE_RECEIVER = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/ipc/SuiteBridgeReceiver.kt"
SUITE_MANIFEST = ROOT / "suite/YSuite/src/main/AndroidManifest.xml"
SETTINGS = ROOT / "settings.gradle.kts"
SUITE_BUILD = ROOT / "suite/YSuite/build.gradle.kts"

ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"
TOOLS_NODE = "{http://schemas.android.com/tools}node"
NAMESPACE_RE = re.compile(r"\bnamespace\s*(?:=\s*)?[\"']([^\"']+)[\"']")


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


def project_namespace(project_dir: Path) -> str:
    build_file = first_existing(project_dir, "build.gradle.kts", "build.gradle")
    if build_file is None:
        fail(f"feature build file missing: {project_dir.relative_to(ROOT)}")
    match = NAMESPACE_RE.search(build_file.read_text(encoding="utf-8"))
    if not match:
        fail(f"feature namespace missing: {build_file.relative_to(ROOT)}")
    return match.group(1)


def resolved_component_name(raw: str, namespace: str) -> str:
    if raw.startswith("."):
        return namespace + raw
    if "." in raw:
        return raw
    return f"{namespace}.{raw}"


def standalone_components(item: dict) -> set[tuple[str, str]]:
    project_dir = ROOT / item["project_dir"]
    manifest = project_dir / "src/main/AndroidManifest.xml"
    if not manifest.is_file():
        fail(f"{item['id']}: feature manifest missing: {manifest.relative_to(ROOT)}")
    namespace = project_namespace(project_dir)
    root = ET.parse(manifest).getroot()
    application = root.find("application")
    if application is None:
        return set()
    found: set[tuple[str, str]] = set()
    for child in application:
        component_type = child.tag.rsplit("}", 1)[-1]
        if component_type not in ALLOWED_COMPONENT_TYPES:
            continue
        raw_name = child.attrib.get(ANDROID_NAME)
        if raw_name:
            found.add((component_type, resolved_component_name(raw_name, namespace)))
    return found


def suite_removed_components() -> set[tuple[str, str]]:
    root = ET.parse(SUITE_MANIFEST).getroot()
    application = root.find("application")
    if application is None:
        fail("YSuite manifest has no <application>")
    found: set[tuple[str, str]] = set()
    for child in application:
        component_type = child.tag.rsplit("}", 1)[-1]
        if component_type not in ALLOWED_COMPONENT_TYPES:
            continue
        if child.attrib.get(TOOLS_NODE) != "remove":
            continue
        class_name = child.attrib.get(ANDROID_NAME)
        if not class_name:
            fail(f"YSuite removed {component_type} is missing android:name")
        found.add((component_type, class_name))
    return found


def verify_replaced_components(features: list[dict]) -> int:
    expected: set[tuple[str, str]] = set()
    owners: dict[tuple[str, str], str] = {}

    for item in features:
        feature_id = item["id"]
        declared = standalone_components(item)
        feature_replaced: set[tuple[str, str]] = set()
        for component in item.get("replaced_components") or []:
            key = (component["type"], component["class"])
            expected.add(key)
            owners[key] = feature_id
            feature_replaced.add(key)
            if key not in declared:
                fail(
                    f"{feature_id}: catalog replaced component is not declared by standalone "
                    f"feature manifest: {key[0]} {key[1]}"
                )

        boot_receiver = item.get("boot_receiver")
        if boot_receiver and ("receiver", boot_receiver) not in feature_replaced:
            fail(
                f"{feature_id}: boot_receiver must also be listed in replaced_components: "
                f"{boot_receiver}"
            )
        for route in item.get("ipc_routes") or []:
            receiver = route["receiver"]
            if ("receiver", receiver) not in feature_replaced:
                fail(
                    f"{feature_id}: IPC receiver must also be listed in replaced_components: "
                    f"{receiver}"
                )

    actual = suite_removed_components()
    missing = expected - actual
    extra = actual - expected
    if missing:
        details = ", ".join(f"{kind} {name} ({owners[(kind, name)]})" for kind, name in sorted(missing))
        fail(f"YSuite manifest is missing catalog-owned tools:node=remove entries: {details}")
    if extra:
        details = ", ".join(f"{kind} {name}" for kind, name in sorted(extra))
        fail(
            "YSuite manifest has manually maintained remove entries not owned by "
            f"config/features.toml: {details}"
        )
    return len(expected)


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
    if build_file.suffix == ".kts":
        local_api = 'project(":api")'
        standalone_api = 'project(":ysuite-api")'
    else:
        local_api = "project(':api')"
        standalone_api = "project(':ysuite-api')"
    if local_api not in build_text or standalone_api not in build_text:
        fail(f"{feature_id}: managed feature must prefer local YSuite api in suite and standalone builds")

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

    for path in (
        GENERATED_FEATURES,
        GENERATED_HOOKS,
        GENERATED_MODULES,
        GENERATED_REPLACED_COMPONENTS,
    ):
        if not path.is_file():
            fail(f"generated catalog output is missing: {path.relative_to(ROOT)}")

    replaced_count = verify_replaced_components(features)

    settings = SETTINGS.read_text(encoding="utf-8")
    suite_build = SUITE_BUILD.read_text(encoding="utf-8")
    manager = FEATURE_MANAGER.read_text(encoding="utf-8")
    xposed = XPOSED_HOST.read_text(encoding="utf-8")
    bridge = BRIDGE_RECEIVER.read_text(encoding="utf-8")

    if 'file("config/generated/feature-modules.tsv")' not in settings:
        fail("settings.gradle.kts must load generated feature module mapping")
    if 'rootProject.file("config/generated/feature-modules.tsv")' not in suite_build:
        fail("YSuite build must load generated feature dependency mapping")
    if "GeneratedFeatureCatalog.all" not in manager:
        fail("FeatureRegistry must delegate to GeneratedFeatureCatalog")
    if "GeneratedFeatureCatalog.ipcActionOwners" not in manager:
        fail("FeatureRegistry must use the generated exact IPC action index")
    if "FeatureRegistry.resolveIpcRoute(action)" not in bridge:
        fail("SuiteBridgeReceiver must use FeatureRegistry.resolveIpcRoute")
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
    ipc_count = sum(len(item.get("ipc_routes") or []) for item in features)
    managed_count = sum(item.get("lifecycle", "managed") == "managed" for item in features)
    if managed_count != len(features):
        fail(f"all features must be managed: {managed_count}/{len(features)}")

    print(
        f"feature-catalog: OK authoritative=config/features.toml "
        f"features={len(features)} hooks={hook_count} ipc={ipc_count} "
        f"replaced={replaced_count} managed={managed_count}"
    )


if __name__ == "__main__":
    main()
