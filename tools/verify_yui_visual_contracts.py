#!/usr/bin/env python3
"""Prevent regressions where feature UI silently overrides YUI appearance."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
checks = {
 "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt": ["strokeWidth = YView.cardOutlineWidth(context)"],
 "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/AppAdapter.java": ["YView.styleListIcon(h.b.appIcon)"],
 "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventAdapter.java": ["YView.styleListIcon(h.b.appIcon)"],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatActionMenu.java": ['YOverlayUi.actionHeightPx(app, "yfloat")', "ImageMenuSettings.primaryCount(app,actions.size())", "private static void attachOverlay("],
 "apps/YFloat/feature/src/main/java/com/yagay/YFloat/ImageActionMenu.java": ["FloatActionMenu.showImageAt(app,where,false,index ->"],
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

# Appearance persistence alone does not guarantee identical rendering:
# the same effective bounds must be calculated in one place for Compose,
# legacy Android Views and new product UI adapters.
def read_visual(path: str) -> str:
    return (root / path).read_text(encoding="utf-8")

rules = read_visual("libs/yui/src/main/java/com/yagay/yui/YDisplayRules.kt")
theme = read_visual("libs/yui/src/main/java/com/yagay/yui/YTheme.kt")
native = read_visual("libs/yui/src/main/java/com/yagay/yui/YView.kt")
geometry = read_visual("libs/yui/src/main/java/com/yagay/yui/YUiGeometry.kt")
spacing = read_visual("next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/theme/YSuiteTokens.kt")
icons = read_visual("libs/yui/src/main/java/com/yagay/yui/YMaterialIcons.kt")
required = {
    "YDisplayRules": (rules, ("fun YAppearance.displayRules(windowWidthDp: Int)",
                              "YDisplayMath.boundedHorizontalInset(", "val effective = copy(",
                              "button = effective.buttonGeometry()",
                              "switch = effective.switchGeometry()")),
    "Compose theme": (theme, ("configuredAppearance.displayRules(windowWidth)",
                              "val appearance = rules.appearance")),
    "View adapter": (native, ("appearance(context).displayRules(windowWidth)",
                             "displayRules(context).cardPaddingDp",
                             "displayRules(context).button.horizontalPaddingDp")),
    "Compose geometry": (geometry, ("fun displayRules(): YDisplayRules",
                                   "fun spacing(base: Dp): Dp", "displayRules().pageHorizontalPaddingDp")),
    "Product spacing": (spacing, ("YUiGeometry.spacing(YDimens.Spacing",)),
    "Standard icons": (icons, ("if (modifier == Modifier)", "yIconModifier(modifier)")),
}
for owner, (body, tokens) in required.items():
    for token in tokens:
        if token not in body:
            raise SystemExit(f"YUI duplicate display-rules regression in {owner}: {token}")
for name in ("sideAllowance", "contentAllowance", "rowAllowance",
             "val safeScreen =", "val safeCard ="):
    if name in theme:
        raise SystemExit(f"Compose theme reintroduced independent display fitting: {name}")
for name in ("val safeInset =", "val allowance = (windowPx -",
             "fun scale(): Float ="):
    if name in native or name in spacing:
        raise SystemExit(f"Independent display geometry formula was reintroduced: {name}")
print("YUI single display rules: OK Compose/View/product spacing/icons")
