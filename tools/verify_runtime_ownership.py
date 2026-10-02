#!/usr/bin/env python3
"""Prevent split-brain standalone/YSuite runtime ownership regressions."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
MANAGER = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/StandaloneAppManager.kt"
GATE = ROOT / "libs/yapi/src/main/java/com/yagay/suite/api/RuntimeOwnerGate.java"
HANDOFF_GATE = ROOT / "libs/yapi/src/main/java/com/yagay/suite/api/RuntimeHandoffGate.java"
HANDOFF_PROVIDER = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/ipc/SuiteRuntimeHandoffProvider.kt"
SUITE_MANIFEST = ROOT / "suite/YSuite/src/main/AndroidManifest.xml"

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

# One authoritative owner-aware behavior/config path per feature. Large hook implementations should
# preferably consume one of these central policy paths instead of duplicating owner checks.
OWNER_AWARE_FILES = {
    "yentrycleaner": ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/xposed/RuntimeRuleSnapshot.kt",
    "ynfc": ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/HookConfigStore.java",
    "yminiguard": ROOT / "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardConfig.java",
    "yfloat": ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/hook/YFloatModule.java",
    "yparam": ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/hook/YParamModule.java",
    "ynotify": ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/collector/XposedEventReceiver.java",
    "ypower": ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/model/AppProfile.java",
    "ydiag": ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/xposed/YDiagModule.kt",
    "ytaskmanager": ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/xposed/TaskManagerModule.java",
}


def fail(message: str) -> None:
    print(f"runtime ownership violation: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    required = (MANAGER, GATE, HANDOFF_GATE, HANDOFF_PROVIDER, SUITE_MANIFEST)
    if any(not path.is_file() for path in required):
        fail("owner manager/gate/handoff provider is missing")

    manager = MANAGER.read_text(encoding="utf-8")
    gate = GATE.read_text(encoding="utf-8")
    handoff = HANDOFF_GATE.read_text(encoding="utf-8")
    provider = HANDOFF_PROVIDER.read_text(encoding="utf-8")
    manifest = SUITE_MANIFEST.read_text(encoding="utf-8")

    if "settings put global" not in manager or "settings delete global" not in manager:
        fail("StandaloneAppManager no longer claims/releases the global runtime owner")
    if "FeatureRegistry.all" not in manager:
        fail("StandaloneAppManager must resolve package ownership from FeatureRegistry")
    if "OWNER_SUITE" not in gate or "KEY_PREFIX" not in gate:
        fail("RuntimeOwnerGate contract is incomplete")

    if "METHOD_MARK_ACTIVE" not in handoff or "shouldStandaloneFallback" not in handoff:
        fail("RuntimeHandoffGate no longer provides positive target-process acknowledgement")
    if "processStartToken" not in provider or "Binder.getCallingPid" not in provider:
        fail("SuiteRuntimeHandoffProvider must bind acknowledgements to the live target process")
    if "SuiteRuntimeHandoffProvider" not in manifest or "com.yagay.YSuite.runtime_handoff" not in manifest:
        fail("YSuite manifest no longer exposes the runtime handoff provider")

    for component in FORBIDDEN_SUPPRESSION:
        if component in manager:
            fail(f"hook bridge/status channel must not be suppressed: {component}")

    for feature_id, path in OWNER_AWARE_FILES.items():
        if not path.is_file():
            fail(f"owner-aware source missing for {feature_id}: {path.relative_to(ROOT)}")
        source = path.read_text(encoding="utf-8")
        if "RuntimeOwnerGate" not in source or feature_id not in source:
            fail(f"{feature_id} no longer checks RuntimeOwnerGate")

    # NFC and Google Circle are timing-sensitive: owner claim alone must never retire their
    # standalone compatibility hook. Positive live-process acknowledgement is mandatory.
    for feature_id in ("ynfc", "yfloat"):
        source = OWNER_AWARE_FILES[feature_id].read_text(encoding="utf-8")
        if "RuntimeHandoffGate" not in source:
            fail(f"{feature_id} no longer requires live target-process handoff acknowledgement")

    # YEntryCleaner component-state/discovery guards share a second centralized policy path.
    entry_component_policy = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/xposed/RuntimeComponentPolicy.kt"
    component_source = entry_component_policy.read_text(encoding="utf-8")
    if "RuntimeOwnerGate" not in component_source or "yentrycleaner" not in component_source:
        fail("YEntryCleaner component policy no longer honors runtime ownership")

    print(
        "runtime ownership OK: global owner transaction present, hook bridges preserved, "
        "YNFC/YFloat use live-process handoff acknowledgement, "
        f"{len(OWNER_AWARE_FILES)} feature runtime paths owner-aware"
    )


if __name__ == "__main__":
    main()
