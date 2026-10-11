#!/usr/bin/env python3
"""Keep the standard installed-app inventory, labels and icons shared."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
catalog = ROOT / "libs/yapi/src/main/java/com/yagay/suite/api/InstalledAppCatalog.java"
consumers = [
    "apps/YDiag/feature/src/main/java/com/yagay/ydiag/data/AppRepository.kt",
    "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/AppScopePicker.kt",
    "apps/YParam/feature/src/main/java/com/yagay/yparam/ui/MainActivity.java",
    "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java",
    "apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java",
    "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/TargetAppsActivity.java",
    "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/data/ProcessRepository.kt",
    "next/feature/yfiles/impl/src/main/java/com/yagay/ysuite/feature/yfiles/YFilesAppsService.kt",
]
issues = []
if not catalog.is_file():
    issues.append("Missing InstalledAppCatalog in shared :api")
else:
    source = catalog.read_text(encoding="utf-8")
    for part in ("getInstalledApplications(", "ACTION_PACKAGE_ADDED", "ACTION_PACKAGE_REMOVED", "ACTION_PACKAGE_CHANGED",
                 "FRESH_MS", "public static List<Entry> list(", "iconBitmap(", "invalidate()"):
        if part not in source:
            issues.append("Catalog missing " + part)
for rel in consumers:
    path = ROOT / rel
    if not path.is_file():
        issues.append("Missing " + rel)
        continue
    src = path.read_text(encoding="utf-8")
    if not re.search(r"InstalledAppCatalog\s*\.\s*list\s*\(", src):
        issues.append(rel + " bypasses the central installed-app inventory")
    if re.search(r"\bgetInstalledApplications\s*\(", src):
        issues.append(rel + " calls PackageManager.getInstalledApplications directly")
    if re.search(r"\bgetApplicationLabel\s*\(", src):
        issues.append(rel + " duplicates shared label decoding")
notification = (ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/util/AppInfoUtil.java").read_text(encoding="utf-8")
if "InstalledAppCatalog.label(" not in notification or "InstalledAppCatalog.icon(" not in notification:
    issues.append("YNotify must reuse the shared label and icon data source")
if issues:
    print("Shared installed-app catalog FAILED:")
    for issue in issues:
        print(" - " + issue)
    sys.exit(1)
print("Shared installed-app catalog PASS")

# Full-source scanning is performed by audit_shared_platform_reads.py in architecture CI.
