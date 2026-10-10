#!/usr/bin/env python3
"""Prevent regressions where feature UI silently overrides YUI appearance."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
checks = {
 "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt": ["strokeWidth = YView.cardOutlineWidth(context)"],
 "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/AppAdapter.java": ["YView.styleListIcon(h.b.appIcon)"],
 "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventAdapter.java": ["YView.styleListIcon(h.b.appIcon)"],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ImageActionMenu.java": ['YOverlayUi.actionHeightPx(app, "yfloat")'],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ResultUi.java": ["static int actionHeight(Context c)"],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ViewHoverOverlay.java": ["SelectionVisuals.drawFittedText("],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/EditableRegionOverlay.java": ["SelectionVisuals.drawFittedText("],
}
for path, snippets in checks.items():
 source = (root / path).read_text(encoding="utf-8")
 for snippet in snippets:
  if snippet not in source:
   raise SystemExit(f"Missing shared YUI visual contract in {path}: {snippet}")
print("Live YUI geometry check: OK")

# Once a shared UI surface is migrated it must never reintroduce numeric dp literals.
# Zero-size placeholders are structural rather than visual geometry.
import re

UI_MIGRATED_KOTLIN = [
    "libs/yui/src/main/java/com/yagay/yui/YComponents.kt",
    "libs/yui/src/main/java/com/yagay/yui/YForms.kt",
    "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt",
    "libs/yui/src/main/java/com/yagay/yui/YTheme.kt",
    "next/core/productui/src/main/java/com/yagay/ysuite/productui/filemanager/YFileManagerComponents.kt",
    "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainTabs.kt",
    "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/AppListRows.kt",
    "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/HistoryChart.kt",
]
visual_literal = re.compile(r"(?<![\w.])(?<!0)([1-9][0-9]*(?:\.[0-9]+)?)\.dp\b")
for path in UI_MIGRATED_KOTLIN:
    source = (root / path).read_text(encoding="utf-8")
    matches = visual_literal.findall(source)
    if matches:
        raise SystemExit(f"YUI fixed-size regression in {path}: {matches[:10]}")
for path in [
    "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ResultUi.java",
    "apps/YFloat/feature/src/main/java/com/yagay/YFloat/UnifiedResultPanel.java",
]:
    source = (root / path).read_text(encoding="utf-8")
    if re.search(r"(?:ResultUi\.)?dp\(\s*(?:context|c|app|this)\s*,\s*\d+", source):
        raise SystemExit(f"YUI overlay geometry regression in {path}")
print("YUI migrated visual constants: OK")
