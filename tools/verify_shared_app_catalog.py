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
    if "InstalledAppCatalog.list(" not in src:
        issues.append(rel + " bypasses the central installed-app inventory")
    if re.search(r"\bgetInstalledApplications\s*\(", src):
        issues.append(rel + " calls PackageManager.getInstalledApplications directly")
    if re.search(r"\bgetApplicationLabel\s*\(", src):
        issues.append(rel + " duplicates shared label decoding")
if issues:
    print("Shared installed-app catalog FAILED:")
    for issue in issues:
        print(" - " + issue)
    sys.exit(1)
print("Shared installed-app catalog PASS")
