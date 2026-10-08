#!/usr/bin/env python3
"""Fail fast when a migrated feature loses a required UI/runtime entrypoint.

This is a structural contract, NOT a claim of behavioral parity with main.
Real Android, root, vendor-NFC and LSPosed behavior still needs device tests.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Each contract requires the feature to expose both its user-facing action
# and the actual implementation path (not just a label in a menu).
REQUIRED = {
    "ydiag": [
        ("impl/src/main/java/com/yagay/ysuite/feature/ydiag/YDiagFeatureScreen.kt", "YDiagFeatureScreen("),
        ("impl/src/main/java/com/yagay/ysuite/feature/ydiag/YDiagRepository.kt", '"perfetto"'),
        ("impl/src/main/java/com/yagay/ysuite/feature/ydiag/YDiagRepository.kt", "startLiveSession("),
    ],
    "ydownload": [
        ("impl/src/main/java/com/yagay/ysuite/feature/ydownload/YDownloadViewModel.kt", "fun retryLoad()"),
        ("impl/src/main/java/com/yagay/ysuite/feature/ydownload/YDownloadService.kt", "class YDownloadService"),
        ("impl/src/main/java/com/yagay/ysuite/feature/ydownload/YDownloadEngine.kt", "class YDownloadEngine"),
    ],
    "yentrycleaner": [
        ("impl/src/main/java/com/yagay/ysuite/feature/yentrycleaner/YEntryCleanerRepository.kt", "fun managedComponents()"),
        ("impl/src/main/java/com/yagay/ysuite/feature/yentrycleaner/YEntryCleanerViewModel.kt", "fun requestRecovery()"),
        ("runtime/src/main/java/com/yagay/ysuite/feature/yentrycleaner/runtime/YEntryComponentReconcileJobService.kt", "getProviderInfo("),
    ],
    "yfiles": [
        ("impl/src/main/java/com/yagay/ysuite/feature/yfiles/YFilesViewModel.kt", "fun refresh()"),
        ("impl/src/main/java/com/yagay/ysuite/feature/yfiles/YFilesFeatureScreen.kt", "YFilesFeatureScreen("),
        ("impl/src/main/java/com/explorer/fileexplorer/core/network/sftp/SftpFileRepository.kt", "class SftpFileRepository"),
    ],
    "yfloat": [
        ("impl/src/main/java/com/yagay/ysuite/feature/yfloat/YFloatFeatureScreen.kt", "YFloatFeatureScreen("),
        ("runtime/src/main/java/com/yagay/YFloat/FloatService.java", "class FloatService"),
        ("runtime/src/main/java/com/yagay/YFloat/LensAccessibilityService.java", "class LensAccessibilityService"),
    ],
    "yminiguard": [
        ("impl/src/main/java/com/yagay/ysuite/feature/yminiguard/YMiniGuardViewModel.kt", "fun reloadEngine()"),
        ("impl/src/main/java/com/yagay/ysuite/feature/yminiguard/YMiniGuardRepository.kt", "class YMiniGuardRepository"),
        ("runtime/src/main/java/com/yagay/ysuite/feature/yminiguard/runtime/GuardModule.java", "class GuardModule"),
    ],
    "ynfc": [
        ("impl/src/main/java/com/yagay/ysuite/feature/ynfc/YNfcRepository.kt", "suspend fun apply("),
        ("impl/src/main/java/com/yagay/ysuite/feature/ynfc/YNfcRepository.kt", "suspend fun stop()"),
        ("runtime/src/main/java/com/yagay/YNFC/xposed/NfcInjectionModule.java", "class NfcInjectionModule"),
    ],
    "ynotify": [
        ("runtime/src/main/java/com/yagay/ysuite/feature/ynotify/runtime/YNotifyDatabase.kt", "fun correlateHistoricalBanners("),
        ("runtime/src/main/java/com/yagay/ysuite/feature/ynotify/runtime/YNotifyDatabase.kt", "classification_locked"),
        ("impl/src/main/java/com/yagay/ysuite/feature/ynotify/YNotifyFeatureScreen.kt", "EventDetail("),
    ],
    "yparam": [
        ("impl/src/main/java/com/yagay/ysuite/feature/yparam/YParamFeatureScreen.kt", "YParamFeatureScreen("),
        ("runtime/src/main/java/com/yagay/ysuite/feature/yparam/runtime/YParamXposedModule.java", "onPackageReady("),
    ],
    "ypower": [
        ("impl/src/main/java/com/yagay/ysuite/feature/ypower/YPowerRepository.kt", "simulatedPermissions"),
        ("runtime/src/main/java/com/yagay/ysuite/feature/ypower/runtime/YPowerModule.kt", "shouldSimulatePermission("),
        ("impl/src/main/java/com/yagay/ysuite/feature/ypower/YPowerFeatureScreen.kt", "YPowerFeatureScreen("),
    ],
    "ytaskmanager": [
        ("impl/src/main/java/com/yagay/ysuite/feature/ytaskmanager/YTaskManagerFeatureScreen.kt", "YTaskManagerFeatureScreen("),
        ("impl/src/main/java/com/yagay/ysuite/feature/ytaskmanager/YTaskManagerRepository.kt", "suspend fun snapshot()"),
        ("impl/src/main/java/com/yagay/ysuite/feature/ytaskmanager/YTaskManagerViewModel.kt", "refreshInternal()"),
    ],
}

def main() -> int:
    errors = []
    for feature, checks in REQUIRED.items():
        for relative_path, symbol in checks:
            path = ROOT / "feature" / feature / relative_path
            if not path.is_file():
                errors.append(f"{feature}: missing {path.relative_to(ROOT)}")
            elif symbol not in path.read_text(encoding="utf-8"):
                errors.append(f"{feature}: {path.relative_to(ROOT)} lacks {symbol!r}")
    for message in errors:
        print("FAIL:", message)
    if errors:
        return 1
    print(f"PASS: {len(REQUIRED)} feature entrypoint contracts / "
          f"{sum(map(len, REQUIRED.values()))} checks")
    print("NOTE: compilation, interaction parity and device tests are separate gates")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
