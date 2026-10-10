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
