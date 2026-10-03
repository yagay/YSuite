#!/usr/bin/env python3
"""Prevent feature modules from bypassing the shared YSuite infrastructure facade."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FEATURE_ROOT = ROOT / "apps"

FORBIDDEN = {
    "com.yagay.suite.core.SuiteRootGateway": "Root must go through FeatureServices/FeatureHost",
    "com.yagay.suite.core.SuiteLog": "logging must go through FeatureServices/FeatureHost",
    "com.yagay.suite.core.SuiteXposedServiceBroker": "LSPosed host routing must go through XposedHostBridge",
    "FeatureHostBinding": "Feature runtimes must not own Host lifecycle; Core owns it",
    "FeatureHostRegistry": "feature code should resolve shared infrastructure through FeatureServices",
}

ALLOWED_SUFFIXES = {".kt", ".java"}


def feature_sources():
    for path in FEATURE_ROOT.glob("*/feature/src/main/**/*"):
        if path.is_file() and path.suffix in ALLOWED_SUFFIXES:
            yield path


def main() -> int:
    failures: list[str] = []
    for path in feature_sources():
        text = path.read_text(encoding="utf-8", errors="replace")
        rel = path.relative_to(ROOT)
        for needle, reason in FORBIDDEN.items():
            if needle in text:
                failures.append(f"{rel}: forbidden {needle!r} — {reason}")

    if failures:
        print("Feature infrastructure boundary violations:")
        for failure in failures:
            print(f"  - {failure}")
        print("\nKeep module-specific business code in apps/*/feature, but route shared infrastructure through yapi.")
        return 1

    print("Shared infrastructure boundary OK: feature code uses public yapi facades.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
