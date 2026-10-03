#!/usr/bin/env python3
"""Fail CI when YSuite stops being the single host for system-facing capabilities."""

from __future__ import annotations

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_XPOSED_ENTRY = "com.yagay.YSuite.xposed.SuiteXposedModule"


def fail(message: str) -> None:
    print(f"[host-ownership] ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    entry_file = ROOT / "suite/YSuite/src/main/resources/META-INF/xposed/java_init.list"
    entries = [
        line.strip()
        for line in entry_file.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if entries != [EXPECTED_XPOSED_ENTRY]:
        fail(f"YSuite must have exactly one Xposed entry; found {entries!r}")

    host_source = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuiteXposedModule.java"
    registry_source = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuiteHookRegistry.java"
    if not host_source.is_file() or not registry_source.is_file():
        fail("single Xposed host or shared hook registry is missing")

    gradle = (ROOT / "suite/YSuite/build.gradle.kts").read_text(encoding="utf-8")
    if 'resources.merges += "META-INF/xposed/*"' in gradle:
        fail("dependency Xposed metadata must never be merged into the host")

    properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
    property_match = re.search(r"^ySuiteHostVersionCode=(\d+)\s*$", properties, re.MULTILINE)
    fallback_match = re.search(r"orNull\?\.toIntOrNull\(\)\s*\?:\s*(\d+)", gradle)
    if not property_match or not fallback_match:
        fail("YSuite host versionCode must be declared in gradle.properties and suite/YSuite/build.gradle.kts")
    property_version = int(property_match.group(1))
    fallback_version = int(fallback_match.group(1))
    if property_version != fallback_version:
        fail(
            "YSuite Hook generation mismatch: "
            f"gradle.properties={property_version} suite fallback={fallback_version}"
        )

    manifest = (ROOT / "suite/YSuite/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    required_host_components = (
        ".accessibility.SuiteAccessibilityService",
        ".notification.SuiteNotificationListenerService",
        ".system.SuiteBootReceiver",
        ".ipc.SuiteBridgeReceiver",
        ".ipc.SuiteGoogleBridgeProvider",
        ".ipc.SuiteMiniGuardStatusProvider",
        ".ipc.SuiteNfcConfigProvider",
    )
    for component in required_host_components:
        if component not in manifest:
            fail(f"shared host component missing: {component}")

    forbidden_plugin_components = (
        "com.yagay.YFloat.LensAccessibilityService",
        "com.yagay.YNotify.collector.UiAccessibilityService",
        "com.yagay.YNotify.collector.NotificationCaptureService",
        "com.yagay.ypower.root.YPowerRootService",
        "com.yagay.ypower.root.BootReceiver",
        "com.yagay.YFloat.FloatServiceBootReceiver",
        "com.yagay.YNotify.collector.XposedEventReceiver",
        "com.yagay.YFloat.GoogleCtsBridgeReceiver",
        "com.yagay.YFloat.GoogleCtsTraceReceiver",
        "com.yagay.YFloat.GoogleCtsBridgeProvider",
        "com.yagay.YMiniGuard.EngineStatusProvider",
        "com.yagay.YNFC.ConfigProvider",
    )
    for component in forbidden_plugin_components:
        marker = f'android:name="{component}"'
        if marker not in manifest:
            fail(f"combined manifest must explicitly remove standalone component: {component}")
        start = manifest.index(marker)
        window = manifest[start:start + 260]
        if 'tools:node="remove"' not in window:
            fail(f"standalone component is not removed in YSuite: {component}")

    for source_path in (
        ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/YSuiteApp.kt",
        ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt",
    ):
        text = source_path.read_text(encoding="utf-8")
        forbidden_calls = (
            "SuiteXposedServiceBroker.capture(",
            "SuiteCrashTracker.reclaim(",
            "RootManager.reclaim(",
        )
        for call in forbidden_calls:
            if call in text:
                fail(f"legacy reclaim/capture path remains in {source_path.name}: {call}")

    reload_coordinator = ROOT / "libs/ycore/src/main/java/com/yagay/suite/core/SuiteHookReloadCoordinator.kt"
    boot_receiver = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/system/SuiteBootReceiver.kt"
    if not reload_coordinator.is_file():
        fail("host-owned Hook reload coordinator is missing")
    boot_text = boot_receiver.read_text(encoding="utf-8")
    if "requestAfterPackageReplaced" not in boot_text or "ACTION_MY_PACKAGE_REPLACED" not in boot_text:
        fail("YSuite package replacement must trigger host-owned selective Hook hot reload")

    yfloat_runtime = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatSuiteRuntime.java"
    if yfloat_runtime.is_file():
        yfloat_text = yfloat_runtime.read_text(encoding="utf-8")
        if "!XposedHostBridge.isSuiteHost(app)" not in yfloat_text:
            fail("embedded YFloat must not own automatic target-process reload in YSuite mode")

    # Normal host pages use the canonical YFeature* design-system surface. Older YPlugin* APIs may
    # remain for source compatibility in feature code, but should no longer be required by the host.
    ui_framework = ROOT / "libs/yui/src/main/java/com/yagay/yui/YFeatureFramework.kt"
    host_ui = (ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt").read_text(encoding="utf-8")
    if not ui_framework.is_file():
        fail("shared YUI feature framework is missing")
    for primitive in ("YFeatureScaffold", "YFeatureList", "YFeatureCard", "YActionRow"):
        if primitive not in host_ui:
            fail(f"YSuite host UI must use shared YUI primitive: {primitive}")

    theme_overrides = (ROOT / "suite/YSuite/src/main/res/values/feature_theme_overrides.xml").read_text(
        encoding="utf-8"
    )
    for theme in (
        "Theme.YEntryCleaner",
        "Theme.YMiniGuard",
        "Theme.YNFC",
        "Theme.YParam",
        "Theme.YTaskManager",
    ):
        if f'name="{theme}" parent="Theme.YSuite"' not in theme_overrides:
            fail(f"combined host theme override missing: {theme}")

    print("[host-ownership] single Xposed entry: OK")
    print("[host-ownership] Hook generation versionCode consistency: OK")
    print("[host-ownership] Xposed dependency metadata merge disabled: OK")
    print("[host-ownership] sole Accessibility/Notification/Boot/IPC/Provider ownership: OK")
    print("[host-ownership] host-owned package-replaced Hook hot reload: OK")
    print("[host-ownership] legacy reclaim/capture paths absent: OK")
    print("[host-ownership] shared YFeature UI shell/theme ownership: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
