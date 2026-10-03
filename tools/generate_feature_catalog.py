#!/usr/bin/env python3
from __future__ import annotations

import argparse
import difflib
import json
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "config/features.toml"
GENERATED_FEATURES = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/GeneratedFeatureCatalog.kt"
GENERATED_HOOKS = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/GeneratedXposedPlugins.java"
GENERATED_MODULES = ROOT / "config/generated/feature-modules.tsv"
GENERATED_REPLACED_COMPONENTS = ROOT / "config/generated/host-replaced-components.tsv"

ALLOWED_CAPABILITIES = {
    "ROOT", "LSPOSED", "ACCESSIBILITY", "NOTIFICATION_LISTENER", "OVERLAY", "NOTIFICATIONS",
    "ALL_FILES", "FILE_SHARE", "NFC",
}
ALLOWED_COMPONENT_TYPES = {"activity", "service", "receiver", "provider"}


def fail(message: str) -> None:
    print(f"feature-generator: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def q(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def load_features() -> list[dict]:
    data = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
    if data.get("schema") != 5:
        fail("config/features.toml must use schema = 5")
    features = data.get("feature") or []
    if not features:
        fail("catalog has no [[feature]] entries")

    ids: set[str] = set()
    modules: set[str] = set()
    project_dirs: set[str] = set()
    standalone_packages: set[str] = set()
    hook_ids: set[str] = set()
    ipc_actions: dict[str, str] = {}
    replaced_components: dict[tuple[str, str], str] = {}

    for item in features:
        for key in ("id", "name", "description", "gradle_module", "project_dir", "entry_activity"):
            if not str(item.get(key, "")).strip():
                fail(f"feature is missing required field {key}: {item!r}")
        feature_id = item["id"]
        if feature_id in ids:
            fail(f"duplicate feature id: {feature_id}")
        ids.add(feature_id)

        module = item["gradle_module"]
        if not module.startswith(":"):
            fail(f"{feature_id}: gradle_module must start with ':'")
        if module in modules:
            fail(f"duplicate gradle_module: {module}")
        modules.add(module)

        project_dir = item["project_dir"]
        if project_dir in project_dirs:
            fail(f"duplicate project_dir: {project_dir}")
        project_dirs.add(project_dir)

        standalone_enabled = bool(item.get("standalone_enabled", False))
        standalone_package = str(item.get("standalone_package", "")).strip()
        if standalone_enabled and not standalone_package:
            fail(f"{feature_id}: standalone_enabled requires standalone_package")
        if standalone_package:
            if standalone_package in standalone_packages:
                fail(f"duplicate standalone_package: {standalone_package}")
            standalone_packages.add(standalone_package)

        if item.get("lifecycle", "managed") != "managed":
            fail(f"{feature_id}: legacy lifecycle is no longer supported; use lifecycle = 'managed'")

        capabilities = set(item.get("capabilities") or [])
        unknown = capabilities - ALLOWED_CAPABILITIES
        if unknown:
            fail(f"{feature_id}: unknown capabilities: {sorted(unknown)}")

        if "ipc_receivers" in item:
            fail(f"{feature_id}: ipc_receivers is obsolete; declare exact ipc_routes instead")
        for route in item.get("ipc_routes") or []:
            action = str(route.get("action", "")).strip()
            receiver = str(route.get("receiver", "")).strip()
            if not action or not receiver:
                fail(f"{feature_id}: every ipc route requires action and receiver")
            previous = ipc_actions.get(action)
            if previous is not None:
                fail(f"duplicate IPC action owner: {action} ({previous}, {feature_id})")
            ipc_actions[action] = feature_id

        for component in item.get("replaced_components") or []:
            component_type = str(component.get("type", "")).strip()
            class_name = str(component.get("class", "")).strip()
            if component_type not in ALLOWED_COMPONENT_TYPES:
                fail(f"{feature_id}: invalid replaced component type: {component_type!r}")
            if not class_name:
                fail(f"{feature_id}: replaced component class is required")
            key = (component_type, class_name)
            previous = replaced_components.get(key)
            if previous is not None:
                fail(f"duplicate host-replaced component: {component_type} {class_name} ({previous}, {feature_id})")
            replaced_components[key] = feature_id

        for hook in item.get("hooks") or []:
            hook_id = str(hook.get("id", ""))
            hook_class = str(hook.get("class", ""))
            if not hook_id or not hook_class:
                fail(f"{feature_id}: every hook requires id and class")
            if not hook_id.startswith(feature_id + "/"):
                fail(f"{feature_id}: hook id must be namespaced: {hook_id}")
            if hook_id in hook_ids:
                fail(f"duplicate hook id: {hook_id}")
            hook_ids.add(hook_id)
    return features


def render_feature_catalog(features: list[dict]) -> str:
    lines = [
        "package com.yagay.suite.core", "",
        "// Generated from config/features.toml by tools/generate_feature_catalog.py.",
        "// Do not edit this file directly.",
        "internal object GeneratedFeatureCatalog {",
        "    val all: List<FeatureSpec> = listOf(",
    ]
    for item in features:
        caps = item.get("capabilities") or []
        cap_expr = "emptySet()" if not caps else "setOf(" + ", ".join(f"SuiteCapability.{cap}" for cap in caps) + ")"
        routes = item.get("ipc_routes") or []
        route_expr = "emptyMap()" if not routes else "mapOf(" + ", ".join(f"{q(r['action'])} to {q(r['receiver'])}" for r in routes) + ")"
        runtime = item.get("runtime")
        accessibility = item.get("accessibility_bridge")
        notification = item.get("notification_listener_bridge")
        boot = item.get("boot_receiver")
        standalone_package = item.get("standalone_package")
        lines.extend([
            "        FeatureSpec(",
            f"            id = {q(item['id'])},",
            f"            name = {q(item['name'])},",
            f"            description = {q(item['description'])},",
            f"            entryActivityClassName = {q(item['entry_activity'])},",
            f"            runtimeInitializerClassName = {q(runtime) if runtime else 'null'},",
            f"            standalonePackageName = {q(standalone_package) if standalone_package else 'null'},",
            f"            standaloneEnabled = {'true' if item.get('standalone_enabled', False) else 'false'},",
            f"            sharedCapabilities = {cap_expr},",
            f"            accessibilityBridgeClassName = {q(accessibility) if accessibility else 'null'},",
            f"            notificationListenerBridgeClassName = {q(notification) if notification else 'null'},",
            f"            bootReceiverClassName = {q(boot) if boot else 'null'},",
            f"            ipcRoutes = {route_expr},",
            f"            requiresRoot = {'true' if 'ROOT' in caps else 'false'},",
            f"            requiresHook = {'true' if item.get('hooks') else 'false'},",
            f"            defaultEnabled = {'true' if item.get('default_enabled', True) else 'false'},",
            "        ),",
        ])
    lines.extend(["    )", ""])

    routes = [(item["id"], r["action"], r["receiver"]) for item in features for r in item.get("ipc_routes") or []]
    if routes:
        lines.append("    val ipcActionOwners: Map<String, Pair<String, String>> = mapOf(")
        for feature_id, action, receiver in routes:
            lines.append(f"        {q(action)} to Pair({q(feature_id)}, {q(receiver)}),")
        lines.append("    )")
    else:
        lines.append("    val ipcActionOwners: Map<String, Pair<String, String>> = emptyMap()")
    lines.extend(["}", ""])
    return "\n".join(lines)


def render_xposed_plugins(features: list[dict]) -> str:
    lines = ["package com.yagay.YSuite.xposed;", "", "// Generated from config/features.toml by tools/generate_feature_catalog.py.", "// Do not edit this file directly.", "final class GeneratedXposedPlugins {", "    private GeneratedXposedPlugins() {}", "", "    static final Entry[] ENTRIES = {"]
    for item in features:
        for hook in item.get("hooks") or []:
            lines.append(f"            new Entry({q(hook['id'])}, {q(hook['class'])}),")
    lines.extend(["    };", "", "    record Entry(String id, String entryClassName) {}", "}", ""])
    return "\n".join(lines)


def render_module_map(features: list[dict]) -> str:
    lines = ["# Generated from config/features.toml by tools/generate_feature_catalog.py.", "# gradle_module<TAB>project_dir"]
    lines.extend(f"{item['gradle_module']}\t{item['project_dir']}" for item in features)
    lines.append("")
    return "\n".join(lines)


def render_replaced_components(features: list[dict]) -> str:
    lines = ["# Generated from config/features.toml by tools/generate_feature_catalog.py.", "# feature_id<TAB>component_type<TAB>class_name"]
    for item in features:
        for component in item.get("replaced_components") or []:
            lines.append(f"{item['id']}\t{component['type']}\t{component['class']}")
    lines.append("")
    return "\n".join(lines)


def outputs(features: list[dict]) -> dict[Path, str]:
    return {
        GENERATED_FEATURES: render_feature_catalog(features),
        GENERATED_HOOKS: render_xposed_plugins(features),
        GENERATED_MODULES: render_module_map(features),
        GENERATED_REPLACED_COMPONENTS: render_replaced_components(features),
    }


def check_file(path: Path, expected: str) -> bool:
    actual = path.read_text(encoding="utf-8") if path.is_file() else ""
    if actual == expected:
        return True
    print(f"feature-generator: stale generated file: {path.relative_to(ROOT)}", file=sys.stderr)
    diff = difflib.unified_diff(actual.splitlines(), expected.splitlines(), fromfile=str(path.relative_to(ROOT)), tofile=str(path.relative_to(ROOT)) + " (generated)", lineterm="")
    for line in list(diff)[:120]:
        print(line, file=sys.stderr)
    return False


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail when generated files are stale")
    args = parser.parse_args()
    features = load_features()
    rendered = outputs(features)
    if args.check:
        ok = all(check_file(path, content) for path, content in rendered.items())
        if not ok:
            raise SystemExit(1)
        print(f"feature-generator: OK features={len(features)} outputs={len(rendered)}")
        return
    for path, content in rendered.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        print(f"feature-generator: wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
