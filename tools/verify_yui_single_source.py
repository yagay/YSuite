#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPS = ROOT / "apps"
YUI = ROOT / "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt"
NEW_FEATURE = ROOT / "tools/new_feature.py"
YFLOAT_APP_UI = ROOT / "apps/YFloat/feature/src/main/java/com/yagay/YFloat/AppUi.java"

LEGACY_ADAPTERS = {
    Path("apps/YFloat/feature/src/main/java/com/yagay/YFloat/AppUi.java"),
    Path("apps/YFloat/feature/src/main/java/com/yagay/YFloat/UiTokens.java"),
}
FORBIDDEN_NAMES = {
    "AppUi.java", "AppUi.kt", "UiTokens.java", "UiTokens.kt", "DesignTokens.java", "DesignTokens.kt",
    "FeatureTheme.kt", "FeatureTheme.java", "AppTheme.kt", "AppTheme.java",
}
REQUIRED_YUI_MARKERS = (
    "enum class YPageRole", "fun YPageScaffold(", "fun YPageList(", "fun YSectionHeader(",
    "fun YListItem(", "fun YNavigationItem(", "fun YSwitchItem(", "fun YCheckboxItem(",
    "fun YStatusItem(", "fun YStatusStrip(", "fun YProgressItem(", "fun YActionGroup(",
    "fun YFilterBar(", "fun YTabBar(", "fun YSelectionBar(", "fun YLogPanel(",
    "fun YSettingsScaffold(", "fun YListScaffold(", "fun YManagerScaffold(",
    "fun YBrowserScaffold(", "fun YDashboardScaffold(", "fun YDetailScaffold(",
    "fun YTimelineScaffold(", "fun YLogScaffold(", "fun YEditorScaffold(", "fun YWizardScaffold(",
)

# Every normal-screen module must consume the shared visual system. Specialized overlays are not
# listed here because they may need their own window semantics while still sharing normal settings UI.
MODULE_UI = {
    "YDiag": (Path("apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"), ("com.yagay.yui",)),
    "YDownload": (Path("apps/YDownload/feature/src/main/java/com/yagay/ydownload/MainActivity.kt"), ("YManagerScaffold",)),
    "YEntryCleaner": (Path("apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainActivity.kt"), ("com.yagay.yui",)),
    "YFiles": (Path("apps/YFiles/feature/src/main/java/com/yagay/yfiles/MainActivity.kt"), ("com.yagay.yui",)),
    "YFloat": (Path("apps/YFloat/feature/src/main/java/com/yagay/YFloat/AppUi.java"), ("com.yagay.yui", "YViewLayout")),
    "YMiniGuard": (Path("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java"), ("YViewLayout",)),
    "YNFC": (Path("apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcAppScreen.kt"), ("com.yagay.yui",)),
    "YNotify": (Path("apps/YNotify/feature/src/main/res/layout/activity_main.xml"), ("Widget.YUI.", "TextAppearance.YUI.")),
    "YParam": (Path("apps/YParam/feature/src/main/java/com/yagay/yparam/ui/MainActivity.java"), ("YViewLayout",)),
    "YPower": (Path("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"), ("YViewLayout",)),
    "YTaskManager": (Path("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"), ("com.yagay.yui",)),
}


def fail(message: str) -> None:
    print(f"yui-single-source: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required UI file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def main() -> None:
    yui = read(YUI)
    for marker in REQUIRED_YUI_MARKERS:
        if marker not in yui:
            fail(f"shared YUI API missing {marker!r}")

    generator = read(NEW_FEATURE)
    for marker in ("YPageScaffold", "YPageList", "YPageRole", "YListItem", "src/main/res/values/strings.xml"):
        if marker not in generator:
            fail(f"new feature generator must use shared YUI 2.0 marker {marker!r}")
    for legacy in ("YFeatureScaffold", "YFeatureCard", "YFeatureList"):
        if legacy in generator:
            fail(f"new feature generator must not emit legacy UI marker {legacy!r}")

    offenders: list[str] = []
    for path in APPS.glob("*/feature/src/main/**/*"):
        if not path.is_file() or path.name not in FORBIDDEN_NAMES:
            continue
        relative = path.relative_to(ROOT)
        if relative not in LEGACY_ADAPTERS:
            offenders.append(str(relative))
    if offenders:
        fail("feature-local UI systems are forbidden; use libs/yui instead: " + ", ".join(sorted(offenders)))

    for module, (path, markers) in MODULE_UI.items():
        source = read(path)
        for marker in markers:
            if marker not in source:
                fail(f"{module} normal screen must consume shared YUI marker {marker!r}")

    yfloat = read(YFLOAT_APP_UI)
    if "YViewLayout.fixedScreen" not in yfloat or "YView." not in yfloat:
        fail("YFloat AppUi must remain a thin shared-YUI compatibility adapter")

    print("yui-single-source: OK modules=11 shared=libs/yui generator=YUI2 legacy-adapter=YFloat-only")


if __name__ == "__main__":
    main()
