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
    required_services = (
        ".accessibility.SuiteAccessibilityService",
        ".notification.SuiteNotificationListenerService",
    )
    for service in required_services:
        if service not in manifest:
            fail(f"shared host service missing: {service}")

    forbidden_live_services = (
        'android:name="com.yagay.YFloat.LensAccessibilityService"\n            android:',
        'android:name="com.yagay.YNotify.collector.UiAccessibilityService"\n            android:',
        'android:name="com.yagay.YNotify.collector.NotificationCaptureService"\n            android:',
    )
    for marker in forbidden_live_services:
        if marker in manifest:
            fail("standalone special-access service is active in YSuite manifest")

    print("[host-ownership] single Xposed entry: OK")
    print("[host-ownership] Xposed dependency metadata merge disabled: OK")
    print("[host-ownership] shared Accessibility/NotificationListener ownership: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
