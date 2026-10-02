#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]
FEATURE_MANAGER = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt"

KOTLIN_FACTORY_RE = re.compile(
    r"@JvmStatic(?:(?:\s|@\w+(?:\([^\n]*\))?)+?)"
    r"(?:public\s+)?fun\s+get\s*\(\s*[A-Za-z_][A-Za-z0-9_]*\s*:\s*Context\b",
    re.MULTILINE,
)
JAVA_FACTORY_RE = re.compile(
    r"\bpublic\s+static\s+[A-Za-z0-9_$.<>?]+\s+get\s*\(\s*"
    r"(?:@\w+(?:\([^)]*\))?\s*)*Context\s+[A-Za-z_][A-Za-z0-9_]*\b",
    re.MULTILINE,
)
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)", re.MULTILINE)


def fail(message: str) -> None:
    print(f"runtime-contract: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def runtime_source(feature: dict) -> Path | None:
    runtime = feature.get("runtime")
    if not runtime:
        return None
    base = ROOT / feature["project_dir"] / "src/main/java" / Path(*runtime.split("."))
    for suffix in (".kt", ".java"):
        candidate = Path(str(base) + suffix)
        if candidate.is_file():
            return candidate
    return None


def verify_runtime(feature: dict) -> None:
    feature_id = feature["id"]
    runtime = feature.get("runtime")
    if not runtime:
        fail(f"{feature_id}: managed feature is missing runtime")

    source = runtime_source(feature)
    if source is None:
        fail(f"{feature_id}: runtime source does not exist: {runtime}")

    text = source.read_text(encoding="utf-8")
    if "ManagedFeatureRuntime" not in text:
        fail(f"{feature_id}: {runtime} must implement ManagedFeatureRuntime")

    package_match = PACKAGE_RE.search(text)
    expected_package, _, expected_class = runtime.rpartition(".")
    if not package_match or package_match.group(1) != expected_package:
        fail(
            f"{feature_id}: runtime package mismatch for {runtime}; "
            f"source={source.relative_to(ROOT)}"
        )
    if source.stem != expected_class:
        fail(
            f"{feature_id}: runtime file/class mismatch for {runtime}; "
            f"source={source.relative_to(ROOT)}"
        )

    has_factory = (
        KOTLIN_FACTORY_RE.search(text) is not None
        if source.suffix == ".kt"
        else JAVA_FACTORY_RE.search(text) is not None
    )
    if not has_factory:
        fail(
            f"{feature_id}: {runtime} must expose a JVM-static get(Context) factory "
            "for FeatureSpec.instantiateRuntime()"
        )


def main() -> None:
    manager = FEATURE_MANAGER.read_text(encoding="utf-8")
    required_loader = 'getMethod("get", Context::class.java).invoke(null, context.applicationContext)'
    if required_loader not in manager:
        fail("FeatureSpec runtime loader contract changed; update this verifier with the loader")

    features = load_features()
    for feature in features:
        if feature.get("lifecycle", "managed") != "managed":
            fail(f"{feature['id']}: only managed runtimes are supported")
        verify_runtime(feature)

    print(f"runtime-contract: OK managed={len(features)} static-get-context={len(features)}")


if __name__ == "__main__":
    main()
