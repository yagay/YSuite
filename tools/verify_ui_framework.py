#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

YUI_COMPOSE = ROOT / "libs/yui/src/main/java/com/yagay/yui/YFeatureFramework.kt"
YUI_VIEW = ROOT / "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt"
YUI_RES = ROOT / "libs/yui/src/main/res/values/yui.xml"
YSUITE = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt"
YPOWER = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"
YMINIGUARD = ROOT / "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java"
YNOTIFY_XML = ROOT / "apps/YNotify/feature/src/main/res/layout/activity_main.xml"


def fail(message: str) -> None:
    print(f"ui-framework: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required UI framework file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(source: str, marker: str, label: str) -> None:
    if marker not in source:
        fail(f"{label} must contain {marker!r}")


def reject_hardcoded_normal_screen_colors(source: str, label: str) -> None:
    # Migrated normal screens must consume theme/status colors from YUI instead of owning a local
    # palette. This intentionally ignores integer constants outside normal entry screens.
    matches = sorted(set(re.findall(r"0x(?:FF|ff)[0-9A-Fa-f]{6}", source)))
    if matches:
        fail(f"{label} reintroduced hard-coded ARGB colors: {matches[:8]}")


def main() -> None:
    compose = text(YUI_COMPOSE)
    view = text(YUI_VIEW)
    resources = text(YUI_RES)
    ysuite = text(YSUITE)
    ypower = text(YPOWER)
    yminiguard = text(YMINIGUARD)
    ynotify = text(YNOTIFY_XML)

    for marker in (
        "fun YFeatureScaffold(",
        "fun YFeatureCard(",
        "fun YStatusRow(",
        "fun YSettingSwitch(",
        "fun YSearchField(",
    ):
        require(compose, marker, "Compose YUI framework")

    for marker in (
        "fun install(",
        "fun card(",
        "fun statusLine(",
        "fun switchRow(",
        "fun searchField(",
    ):
        require(view, marker, "View YUI framework")

    for marker in (
        'name="Widget.YUI.Button.Tonal"',
        'name="Widget.YUI.Button.Outlined"',
        'name="Widget.YUI.TextInput"',
        'name="TextAppearance.YUI.SectionTitle"',
        'name="TextAppearance.YUI.Body"',
        'name="TextAppearance.YUI.Caption"',
    ):
        require(resources, marker, "XML YUI framework")

    require(ysuite, "YFeatureScaffold(", "YSuite main screen")
    require(ysuite, "YFeatureCard(", "YSuite main screen")
    require(ypower, "YViewLayout.install(", "YPower main screen")
    require(ypower, "YViewLayout.card(", "YPower main screen")
    require(yminiguard, "YViewLayout.install(", "YMiniGuard main screen")
    require(yminiguard, "YViewLayout.switchRow(", "YMiniGuard main screen")

    if "Widget.YUI." not in ynotify or "TextAppearance.YUI." not in ynotify:
        fail("YNotify XML main layout must consume shared Widget.YUI and TextAppearance.YUI styles")
    if "@dimen/yui_" not in ynotify:
        fail("YNotify XML main layout must consume shared YUI dimensions")

    reject_hardcoded_normal_screen_colors(ypower, "YPower MainActivity")
    reject_hardcoded_normal_screen_colors(yminiguard, "YMiniGuard MainActivity")

    print(
        "ui-framework: OK compose=YFeature* view=YViewLayout xml=Widget.YUI "
        "migrated=YSuite,YPower,YMiniGuard,YNotify"
    )


if __name__ == "__main__":
    main()
