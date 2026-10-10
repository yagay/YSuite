#!/usr/bin/env python3
"""Guard YUI content compatibility: normal content, actions and wide/narrow layouts."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
YUI = ROOT / "libs/yui/src/main/java/com/yagay/yui"

def require(path: Path, *markers: str) -> list[str]:
    if not path.is_file():
        return [f"missing {path.relative_to(ROOT)}"]
    text = path.read_text(encoding="utf-8")
    return [
        f"{path.relative_to(ROOT)}: missing {marker}"
        for marker in markers if marker not in text
    ]

def main() -> int:
    errors: list[str] = []
    errors += require(YUI / "YViewFramework.kt",
                      "setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp * YView.fontPercent(context) / 100f)",
                      "ViewGroup.LayoutParams.WRAP_CONTENT,",
                      "maxOf(content.paddingBottom, sectionGap(context) + YView.controlGap(context))")
    errors += require(YUI / "YAdaptive.kt",
                      "BoxWithConstraints(modifier.fillMaxSize())",
                      "NavigationSuiteType.NavigationBar",
                      "NavigationSuiteType.NavigationRail",
                      "emphasizeCards = true")
    errors += require(YUI / "YUnifiedDesign.kt",
                      "titleMaxLines: Int = 2",
                      "subtitleMaxLines: Int = 2",
                      "detailMaxLines: Int = 2",
                      "val effectiveKey = tabs[selectedIndex].key")
    errors += require(YUI / "YResponsiveForms.kt",
                      "fun YResponsiveFieldAction(",
                      "fun YResponsiveFieldPair(",
                      "fun YResponsiveActionBar(",
                      "maxWidth < stackedBelow")
    errors += require(ROOT / "next/core/productui/src/main/java/com/yagay/ysuite/productui/download/QdmDownloadComponents.kt",
                      "YResponsiveFieldAction(",
                      "YResponsiveFieldPair(",
                      "YResponsiveActionBar {",
                      "BoxWithConstraints(Modifier.fillMaxSize().imePadding())",
                      "speedLimitDraft",
                      "YUiAlertDialog(")
    errors += require(ROOT / "next/core/productui/src/main/java/com/yagay/ysuite/productui/download/QdmDownloadWorkspace.kt",
                      "val effectiveTabId =",
                      "selectedKey = effectiveTabId",
                      "content(adaptive, effectiveTabId)")
    errors += require(ROOT / "next/core/productui/src/main/java/com/yagay/ysuite/productui/filemanager/FileExplorerAdvancedComponents.kt",
                      "modifier = Modifier.widthIn(max = 120.dp)",
                      "tabs.size > 1 && tab.id == tabs[selected].id")
    errors += require(ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java",
                      "screenWidthDp < 480",
                      "PopupMenu popup",
                      "YView.touchTarget(this)",
                      "titleView.setMaxLines(Integer.MAX_VALUE)",
                      "sub.setMaxLines(Integer.MAX_VALUE)")
    if errors:
        print("yui-content: incompatible or missing UI contracts:", file=sys.stderr)
        for error in errors:
            print(" - " + error, file=sys.stderr)
        return 1
    print("yui-content: OK adaptive text, navigation, file/download forms, and touch targets")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
