#!/usr/bin/env python3
"""Scan reusable Android feature modules for YSuite integration hazards.

The scanner is intentionally dependency-free so it can run in GitHub Actions immediately
after submodules are checked out. It reports resource/manifest collisions plus process-global APIs
that are safe in standalone APKs but must be owned centrally when features share one YSuite process.
"""

from __future__ import annotations

import argparse
import collections
import pathlib
import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
A = "{%s}" % ANDROID_NS

HIGH_RISK_RESOURCE_TYPES = {
    "anim", "animator", "array", "bool", "color", "dimen", "drawable", "font",
    "integer", "layout", "menu", "mipmap", "navigation", "plurals", "raw", "string",
    "style", "transition", "xml",
}

GENERIC_HIGH_RISK_NAMES = {
    "app_name", "app_description", "apptheme", "theme.app", "file_paths", "share_paths",
    "accessibility_service_config",
}

# Standalone features are allowed to use these APIs. In the combined process they are reminders
# that YSuite must capture/reclaim ownership after feature initialization. Keep these as warnings,
# not hard failures, because the same source must remain independently buildable.
PROCESS_GLOBAL_PATTERNS = {
    "LSPOSED_LISTENER": "XposedServiceHelper.registerListener(",
    "LIBSU_DEFAULT_BUILDER": "Shell.setDefaultBuilder(",
    "UNCAUGHT_EXCEPTION_HANDLER": "Thread.setDefaultUncaughtExceptionHandler(",
    "SHARED_SCOPE_REMOVE": ".removeScope(",
}


def module_dirs(root: pathlib.Path) -> list[pathlib.Path]:
    features = root / "features"
    if not features.is_dir():
        return []
    return sorted(p for p in features.iterdir() if p.is_dir() and (p / "feature").is_dir())


def normalize_file_resource_name(path: pathlib.Path) -> str:
    name = path.name
    if name.endswith(".9.png"):
        return name[:-6]
    return path.stem


def collect_resources(module: pathlib.Path):
    res = module / "feature" / "src" / "main" / "res"
    result: set[tuple[str, str]] = set()
    parse_errors: list[str] = []
    if not res.is_dir():
        return result, parse_errors

    for folder in sorted(p for p in res.iterdir() if p.is_dir()):
        base_type = folder.name.split("-", 1)[0]
        if base_type == "values":
            for xml in sorted(folder.glob("*.xml")):
                try:
                    root = ET.parse(xml).getroot()
                except Exception as exc:
                    parse_errors.append(f"{xml.relative_to(module)}: {exc}")
                    continue
                for child in root:
                    name = child.attrib.get("name")
                    if not name:
                        continue
                    rtype = child.attrib.get("type") if child.tag == "item" else child.tag
                    if rtype:
                        result.add((rtype, name))
        else:
            for file in sorted(p for p in folder.iterdir() if p.is_file() and not p.name.startswith(".")):
                result.add((base_type, normalize_file_resource_name(file)))
    return result, parse_errors


def manifest_info(module: pathlib.Path):
    manifest = module / "feature" / "src" / "main" / "AndroidManifest.xml"
    findings = {
        "providers": [], "exported": [], "application_name": None, "application_theme": None,
        "activity_themes": [], "parse_error": None,
    }
    if not manifest.is_file():
        return findings
    try:
        root = ET.parse(manifest).getroot()
    except Exception as exc:
        findings["parse_error"] = str(exc)
        return findings

    app = root.find("application")
    if app is None:
        return findings
    findings["application_name"] = app.attrib.get(A + "name")
    findings["application_theme"] = app.attrib.get(A + "theme")

    for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
        for node in app.findall(tag):
            name = node.attrib.get(A + "name", "<unnamed>")
            exported = node.attrib.get(A + "exported")
            permission = node.attrib.get(A + "permission")
            if tag == "provider":
                authority = node.attrib.get(A + "authorities")
                if authority:
                    findings["providers"].append((name, authority))
            if tag == "activity":
                theme = node.attrib.get(A + "theme")
                if theme:
                    findings["activity_themes"].append((name, theme))
            if exported == "true" and not permission:
                findings["exported"].append((tag, name))
    return findings


def process_global_findings(module: pathlib.Path) -> list[tuple[str, str, int]]:
    source_root = module / "feature" / "src" / "main" / "java"
    findings: list[tuple[str, str, int]] = []
    if not source_root.is_dir():
        return findings
    for source in sorted(list(source_root.rglob("*.java")) + list(source_root.rglob("*.kt"))):
        try:
            text = source.read_text(encoding="utf-8")
        except Exception:
            continue
        for line_no, line in enumerate(text.splitlines(), 1):
            for kind, needle in PROCESS_GLOBAL_PATTERNS.items():
                if needle in line:
                    findings.append((kind, str(source.relative_to(module)), line_no))
    return findings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".", help="YSuite repository root")
    parser.add_argument(
        "--fail-on-high-risk", action="store_true",
        help="exit non-zero when a high-risk cross-feature resource/provider collision exists",
    )
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    modules = module_dirs(root)
    if not modules:
        print("[integration-scan] no feature submodules found", file=sys.stderr)
        return 2

    resources_by_key: dict[tuple[str, str], set[str]] = collections.defaultdict(set)
    manifests = {}
    process_globals = {}
    parse_errors = []

    for module in modules:
        resources, errors = collect_resources(module)
        parse_errors.extend(f"{module.name}: {e}" for e in errors)
        for key in resources:
            resources_by_key[key].add(module.name)
        manifests[module.name] = manifest_info(module)
        process_globals[module.name] = process_global_findings(module)

    collisions = {key: sorted(owners) for key, owners in resources_by_key.items() if len(owners) > 1}
    high_risk = {key: owners for key, owners in collisions.items() if key[0] in HIGH_RISK_RESOURCE_TYPES}

    print("[integration-scan] modules:", ", ".join(m.name for m in modules))
    print(f"[integration-scan] cross-feature resource collisions: {len(collisions)}")
    for (rtype, name), owners in sorted(
        collisions.items(),
        key=lambda item: (0 if item[0][0] in HIGH_RISK_RESOURCE_TYPES else 1, item[0][0], item[0][1]),
    ):
        severity = "HIGH" if rtype in HIGH_RISK_RESOURCE_TYPES else "INFO"
        generic = " GENERIC" if name.lower() in GENERIC_HIGH_RISK_NAMES else ""
        print(f"RESOURCE {severity}{generic} {rtype}/{name} <- {', '.join(owners)}")

    authority_owners: dict[str, list[tuple[str, str]]] = collections.defaultdict(list)
    manifest_high_risk = False
    for module_name, info in manifests.items():
        if info["parse_error"]:
            parse_errors.append(f"{module_name}: manifest: {info['parse_error']}")
            continue
        if info["application_name"]:
            print(f"MANIFEST WARN {module_name}: feature manifest declares application name {info['application_name']}")
        if info["application_theme"]:
            print(f"MANIFEST INFO {module_name}: application theme {info['application_theme']}")
        for activity, theme in info["activity_themes"]:
            print(f"MANIFEST THEME {module_name}: {activity} -> {theme}")
        for provider, authority in info["providers"]:
            authority_owners[authority].append((module_name, provider))
        for tag, component in info["exported"]:
            print(f"MANIFEST EXPORTED {module_name}: {tag} {component} has no component permission")

    for authority, owners in sorted(authority_owners.items()):
        if len(owners) > 1:
            manifest_high_risk = True
            rendered = ", ".join(f"{m}:{c}" for m, c in owners)
            print(f"MANIFEST HIGH provider authority collision {authority} <- {rendered}")

    total_process_globals = 0
    for module_name, findings in process_globals.items():
        for kind, source, line_no in findings:
            total_process_globals += 1
            print(
                f"PROCESS GLOBAL {module_name}: {kind} at {source}:{line_no} -- "
                "standalone use is allowed; YSuite must broker/reclaim shared ownership"
            )
    print(f"[integration-scan] process-global integration reminders: {total_process_globals}")

    for error in parse_errors:
        print(f"PARSE WARN {error}")

    print(f"[integration-scan] high-risk resource collisions: {len(high_risk)}")
    if args.fail_on_high_risk and (high_risk or manifest_high_risk):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
