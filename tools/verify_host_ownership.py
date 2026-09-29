#!/usr/bin/env python3
"""Fail CI when YSuite stops being the single host for system-facing capabilities."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED_XPOSED_ENTRY = "com.yagay.YSuite.xposed.SuiteXposedModule"


def fail(message: str) -> None:
    print(f"[host-ownership] ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    entry_file = ROOT / "suite/src/main/resources/META-INF/xposed/java_init.list"
    entries = [
        line.strip()
        for line in entry_file.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if entries != [EXPECTED_XPOSED_ENTRY]:
        fail(f"YSuite must have exactly one Xposed entry; found {entries!r}")

    host_source = ROOT / "suite/src/main/java/com/yagay/YSuite/xposed/SuiteXposedModule.java"
    registry_source = ROOT / "suite/src/main/java/com/yagay/YSuite/xposed/SuiteHookRegistry.java"
    if not host_source.is_file() or not registry_source.is_file():
        fail("single Xposed host or shared hook registry is missing")

    gradle = (ROOT / "suite/build.gradle.kts").read_text(encoding="utf-8")
    if 'resources.merges += "META-INF/xposed/*"' in gradle:
        fail("dependency Xposed metadata must never be merged into the host")

    manifest = (ROOT / "suite/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
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

    # Standalone components may remain in feature manifests for independent APK builds, but the
    # combined host manifest must explicitly remove them and expose only YSuite-owned components.
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

    # The old integration model let plugins seize process-global state, then made the host reclaim
    # it. Sole-host architecture forbids that pattern in both cold-start and hot-enable paths.
    for source_path in (
        ROOT / "suite/src/main/java/com/yagay/YSuite/YSuiteApp.kt",
        ROOT / "suite/src/main/java/com/yagay/YSuite/MainActivity.kt",
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

    # YSuite owns the visual shell too. Plugins may supply their page content, but ordinary host
    # pages must use the shared YUI scaffold/layout primitives and normal feature themes are aliased
    # to Theme.YSuite in the combined APK.
    ui_framework = ROOT / "ui/src/main/java/com/yagay/yui/YPluginFramework.kt"
    host_ui = (ROOT / "suite/src/main/java/com/yagay/YSuite/MainActivity.kt").read_text(encoding="utf-8")
    if not ui_framework.is_file():
        fail("shared YUI plugin framework is missing")
    for primitive in ("YPluginScaffold", "YPluginList", "YPluginHeader", "YActionRow"):
        if primitive not in host_ui:
            fail(f"YSuite host UI must use shared YUI primitive: {primitive}")

    theme_overrides = (ROOT / "suite/src/main/res/values/feature_theme_overrides.xml").read_text(
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
    print("[host-ownership] Xposed dependency metadata merge disabled: OK")
    print("[host-ownership] sole Accessibility/Notification/Boot/IPC/Provider ownership: OK")
    print("[host-ownership] legacy reclaim/capture paths absent: OK")
    print("[host-ownership] shared YUI shell/theme ownership: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
