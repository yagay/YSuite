#!/usr/bin/env python3
"""Enforce the one-host/many-feature architecture without blocking Android-required feature slots."""

from __future__ import annotations

import re
import sys
from collections import defaultdict
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]


def error(message: str, errors: list[str]) -> None:
    errors.append(message)


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def gradle_namespace(gradle: str, feature_id: str) -> str:
    match = re.search(r'\bnamespace\s*=\s*"([^"]+)"', gradle)
    if not match:
        raise SystemExit(f"[feature-boundary] ERROR: {feature_id}: Android namespace is required")
    return match.group(1)


def resolve_component_name(namespace: str, class_name: str) -> str:
    class_name = class_name.strip()
    if class_name.startswith("."):
        return namespace + class_name
    if "." not in class_name:
        return namespace + "." + class_name
    return class_name


def main() -> int:
    features = load_features()
    errors: list[str] = []

    standalone_settings = read(ROOT / "settings.gradle.kts")
    if 'include(":standalone")' not in standalone_settings:
        error("generic :standalone host is not included in settings.gradle.kts", errors)
    if 'file("standalone/host")' not in standalone_settings:
        error("generic :standalone host must live at standalone/host", errors)

    standalone_gradle = read(ROOT / "standalone/host/build.gradle.kts")
    for marker in (
        "ySuiteStandaloneFeature",
        "config/generated/standalone-features.tsv",
        "project(selected.gradleModule)",
    ):
        if marker not in standalone_gradle:
            error(f"standalone composer missing marker: {marker}", errors)

    module_to_feature = {str(item["gradle_module"]): str(item["id"]) for item in features}
    component_owners: dict[tuple[str, str], list[str]] = defaultdict(list)
    authorities: dict[str, list[str]] = defaultdict(list)

    for item in features:
        feature_id = str(item["id"])
        project_dir = ROOT / str(item["project_dir"])
        gradle = read(project_dir / "build.gradle.kts")
        manifest = read(project_dir / "src/main/AndroidManifest.xml")
        namespace = gradle_namespace(gradle, feature_id)

        if 'id("com.android.application")' in gradle or "alias(libs.plugins.android.application)" in gradle:
            error(f"{feature_id}: Feature module must be an Android library, not an application", errors)

        # Features may depend on shared host/api/ui/core libraries, but never directly on another Feature.
        for module, owner in module_to_feature.items():
            if owner == feature_id:
                continue
            patterns = (
                f'project("{module}")',
                f"project('{module}')",
                f'project(path = "{module}")',
            )
            if any(pattern in gradle for pattern in patterns):
                error(
                    f"{feature_id}: direct dependency on Feature {owner} ({module}); use Host API/Capability instead",
                    errors,
                )

        # A Feature must never become a second Application owner when embedded.
        if re.search(r"<application\b[^>]*\bandroid:name\s*=", manifest, re.DOTALL):
            error(f"{feature_id}: Feature manifest must not declare android:name on <application>", errors)

        # Feature libraries must not package their own LSPosed module metadata. Standalone host or YSuite owns it.
        feature_resources = project_dir / "src/main/resources/META-INF/xposed"
        if feature_resources.exists():
            error(f"{feature_id}: LSPosed module metadata belongs to host, not Feature", errors)

        # Detect physical Android component collisions before manifest merge. Relative names are resolved
        # against each Feature namespace so .ui.MainActivity in two different namespaces is not a collision.
        for tag in ("activity", "service", "receiver", "provider"):
            for class_name in re.findall(
                rf"<{tag}\b[^>]*android:name\s*=\s*\"([^\"]+)\"",
                manifest,
                flags=re.DOTALL,
            ):
                resolved = resolve_component_name(namespace, class_name)
                component_owners[(tag, resolved)].append(feature_id)

        # Authorities are globally unique inside one APK. ${applicationId} remains supported and safe.
        for authority in re.findall(r"android:authorities\s*=\s*\"([^\"]+)\"", manifest):
            authorities[authority].append(feature_id)

    for (kind, class_name), owners in component_owners.items():
        unique = sorted(set(owners))
        if len(unique) > 1:
            error(
                f"duplicate Android component class: {kind} {class_name} owners={unique}",
                errors,
            )

    for authority, owners in authorities.items():
        unique = sorted(set(owners))
        if len(unique) > 1:
            error(f"duplicate provider authority: {authority} owners={unique}", errors)

    raw_file_provider_owners = []
    for item in features:
        manifest = read(ROOT / str(item["project_dir"]) / "src/main/AndroidManifest.xml")
        if 'android:name="androidx.core.content.FileProvider"' in manifest:
            raw_file_provider_owners.append(str(item["id"]))
    if len(raw_file_provider_owners) > 1:
        error(
            "multiple Features declare raw androidx.core.content.FileProvider; "
            f"use a dedicated subclass or host-owned provider: {sorted(raw_file_provider_owners)}",
            errors,
        )

    if errors:
        for message in errors:
            print(f"[feature-boundary] ERROR: {message}", file=sys.stderr)
        return 1

    print(f"[feature-boundary] Feature library boundary: OK ({len(features)} features)")
    print("[feature-boundary] Feature-to-Feature hard dependencies: none")
    print("[feature-boundary] Application/LSPosed host ownership: OK")
    print("[feature-boundary] Android component classes/authorities: collision-free")
    print("[feature-boundary] generic standalone composer: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
