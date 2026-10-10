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
    'ProcessBuilder("su"': "Root process ownership belongs to the shared Core Root host",
    'Runtime.getRuntime().exec("su': "Root process ownership belongs to the shared Core Root host",
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


    # Enforce a single in-repository copy of the public Host API and visual system.
    for build in sorted(FEATURE_ROOT.glob("*/feature/build.gradle.kts")):
        source = build.read_text(encoding="utf-8")
        for dependency in ('project(":api")', 'project(":ui")'):
            if dependency not in source:
                failures.append(f"{build.relative_to(ROOT)}: missing shared {dependency}")
        if "com.github.yagay.YSuite:api" in source or "com.github.yagay.YSuite:ui" in source:
            # External feature-only source checkouts retain an explicit fallback, never used
            # by the monorepo or when the local standalone project aliases are available.
            if 'rootProject.findProject(":api")' not in source or 'rootProject.findProject(":ysuite-api")' not in source:
                failures.append(f"{build.relative_to(ROOT)}: remote API fallback is not guarded by local project selection")

    expected_shared = {
        "apps/YDiag/feature/src/main/java/com/yagay/ydiag/root/RootShell.kt": "FeatureRootCommands.execute",
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/system/RootShell.kt": "FeatureRootCommands.execute",
        "apps/YPower/feature/src/main/java/com/yagay/ypower/root/RootShell.java": "FeatureRootCommands.execute",
        "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/root/RootShell.kt": "FeatureRootCommands.execute",
        "apps/YDiag/feature/src/main/java/com/yagay/ydiag/data/Preferences.kt": "FeatureSettings.named",
        "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/data/SettingsRepository.kt": "FeatureSettings.named",
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/CardRepository.kt": "FeatureSettings.named",
        "apps/YPower/feature/src/main/java/com/yagay/ypower/data/ProfileStore.java": "FeatureSettings.named",
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/AppLogger.kt": "FeatureLogBuffer",
        "apps/YDiag/feature/src/main/java/com/yagay/ydiag/export/DiagnosticExporter.kt": "FeatureDiagnosticArchive",
        "apps/YNotify/feature/src/main/java/com/yagay/YNotify/util/DiagnosticsExporter.java": "FeatureDiagnosticArchive",
        "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/DiagnosticsManager.java": "FeatureDiagnosticArchive",
        "next/core/platform/android/src/main/java/com/yagay/ysuite/platform/android/DefaultPlatformServices.kt": "HostedRootGateway",
    }
    for name, marker in expected_shared.items():
        file = ROOT / name
        if not file.is_file() or marker not in file.read_text(encoding="utf-8"):
            failures.append(f"{name}: expected to use shared infrastructure {marker}")

    if failures:
        print("Feature infrastructure boundary violations:")
        for failure in failures:
            print(f"  - {failure}")
        print("\nKeep module-specific business code in apps/*/feature, but route shared infrastructure through yapi.")
        return 1

    print("Shared infrastructure boundary OK: feature code uses public yapi facades and Core-owned Root.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
