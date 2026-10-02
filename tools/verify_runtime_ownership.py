#!/usr/bin/env python3
"""Prevent split-brain standalone/YSuite runtime ownership regressions."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/StandaloneAppManager.kt"
GATE = ROOT / "libs/yapi/src/main/java/com/yagay/suite/api/RuntimeOwnerGate.java"

# These are hook communication/status channels, not duplicate Android runtime owners. They must
# remain registered in the standalone APK so already-loaded hooks can retire/read status cleanly.
FORBIDDEN_SUPPRESSION = (
    "com.yagay.YNFC.ConfigProvider",
    "com.yagay.YMiniGuard.EngineStatusProvider",
    "com.yagay.YNotify.collector.XposedEventReceiver",
    "com.yagay.YFloat.GoogleCtsBridgeProvider",
    "com.yagay.YFloat.GoogleCtsBridgeReceiver",
    "com.yagay.YFloat.GoogleCtsTraceReceiver",
)

# Modules with explicit owner-aware runtime behavior today. Add new behavioral LSPosed modules here
# when they become active; the check intentionally makes ownership an architectural requirement.
OWNER_AWARE_FILES = {
    "ynfc": ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/HookConfigStore.java",
    "yminiguard": ROOT / "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardConfig.java",
    "yfloat": ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/hook/YFloatModule.java",
    "yparam": ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/hook/YParamModule.java",
    "ynotify": ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/collector/XposedEventReceiver.java",
    "ydiag": ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/xposed/YDiagModule.kt",
    "ytaskmanager": ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/xposed/TaskManagerModule.java",
}


def fail(message: str) -> None:
    print(f"runtime ownership violation: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    if not MANAGER.is_file() or not GATE.is_file():
        fail("owner manager/gate is missing")

    manager = MANAGER.read_text(encoding="utf-8")
    gate = GATE.read_text(encoding="utf-8")

    if "settings put global" not in manager or "settings delete global" not in manager:
        fail("StandaloneAppManager no longer claims/releases the global runtime owner")
    if "FeatureRegistry.all" not in manager:
        fail("StandaloneAppManager must resolve package ownership from FeatureRegistry")
    if "OWNER_SUITE" not in gate or "KEY_PREFIX" not in gate:
        fail("RuntimeOwnerGate contract is incomplete")

    for component in FORBIDDEN_SUPPRESSION:
        if component in manager:
            fail(f"hook bridge/status channel must not be suppressed: {component}")

    for feature_id, path in OWNER_AWARE_FILES.items():
        if not path.is_file():
            fail(f"owner-aware source missing for {feature_id}: {path.relative_to(ROOT)}")
        source = path.read_text(encoding="utf-8")
        if "RuntimeOwnerGate" not in source or feature_id not in source:
            fail(f"{feature_id} no longer checks RuntimeOwnerGate")

    print(
        "runtime ownership OK: global owner transaction present, hook bridges preserved, "
        f"{len(OWNER_AWARE_FILES)} runtime paths owner-aware"
    )


if __name__ == "__main__":
    main()
