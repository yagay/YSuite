#!/usr/bin/env python3
"""Whole-repository inventory of platform package scans and shared UI data.

Fail ordinary feature-owned package-list reimplementations. Special-purpose
component/intent/Root queries are reported but intentionally not rewritten.
"""
from __future__ import annotations

import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCAN_ROOTS = [ROOT / "apps", ROOT / "next", ROOT / "suite", ROOT / "libs"]
PATTERNS = {
    "installed_apps": re.compile(r"\bgetInstalledApplications\s*\("),
    "installed_packages": re.compile(r"\bgetInstalledPackages\s*\("),
    "app_label": re.compile(r"\bgetApplicationLabel\s*\("),
    "app_icon": re.compile(r"\bgetApplicationIcon\s*\("),
    "package_details": re.compile(r"\bgetPackageInfo\s*\("),
    "intent_resolvers": re.compile(r"\bqueryIntentActivities\s*\("),
    "app_components": re.compile(r"\b(getActivityInfo|getServiceInfo|getReceiverInfo|getProviderInfo)\s*\("),
}
CATALOG = "libs/yapi/src/main/java/com/yagay/suite/api/InstalledAppCatalog.java"
# Security hooks may observe/intercept installed-app enumeration in a target
# process; that is different from *loading a picker*. Keep their interception
# untouched. All UI/data-model callers must use the shared inventory.
EXEMPT = (
    "/xposed/", "/hook/", "/hooks/", "/src/test/", "/src/androidTest/",
)
records = []
issues = []
for folder in SCAN_ROOTS:
    if not folder.exists():
        continue
    for path in sorted(list(folder.rglob("*.kt")) + list(folder.rglob("*.java"))):
        relative = path.relative_to(ROOT).as_posix()
        if "/src/main/" not in relative:
            continue
        if relative == CATALOG:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        for line, text in enumerate(source.splitlines(), start=1):
            for category, pattern in PATTERNS.items():
                if pattern.search(text):
                    records.append({"path": relative, "line": line, "kind": category})
                    if category == "installed_apps" and not any(key in relative for key in EXEMPT):
                        issues.append(f"{relative}:{line} directly scans apps instead of InstalledAppCatalog")
                    break

report = ROOT / "build/reports/shared-platform-read-audit.json"
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps({
    "patterns": dict(Counter(item["kind"] for item in records)),
    "locations": records,
    "violations": issues,
}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
for name, count in sorted(Counter(item["kind"] for item in records).items()):
    print(f"platform-read {name}: {count}")
print("Platform source audit:", report.relative_to(ROOT))
if issues:
    for item in issues:
        print("DIRECT APP SCAN:", item)
    raise SystemExit(1)
print("PASS: ordinary app-list enumeration is owned only by InstalledAppCatalog")
