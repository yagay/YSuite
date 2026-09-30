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
YENTRY = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainActivity.kt"
YPARAM = ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/ui/MainActivity.java"
YNFC_SCREEN = ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcAppScreen.kt"
YNFC_COMPONENTS = ROOT / "apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcComponents.kt"
YTASK = ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"
YDIAG = ROOT / "apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"

# Secondary/detail screens that have been migrated to the shared contract.
YPARAM_DETAIL = ROOT / "apps/YParam/feature/src/main/java/com/yagay/yparam/ui/AppDetailActivity.java"
YPOWER_DETAIL = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/AppDetailActivity.java"
YPOWER_RECOMMENDED = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/RecommendedAppsActivity.java"
YNOTIFY_HISTORY = ROOT / "apps/YNotify/feature/src/main/res/layout/activity_app_history.xml"
YNOTIFY_DETAIL = ROOT / "apps/YNotify/feature/src/main/res/layout/activity_event_detail.xml"
YENTRY_SCOPE = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/ScopeScreen.kt"


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
    yentry = text(YENTRY)
    yparam = text(YPARAM)
    ynfc_screen = text(YNFC_SCREEN)
    ynfc_components = text(YNFC_COMPONENTS)
    ytask = text(YTASK)
    ydiag = text(YDIAG)
    yparam_detail = text(YPARAM_DETAIL)
    ypower_detail = text(YPOWER_DETAIL)
    ypower_recommended = text(YPOWER_RECOMMENDED)
    ynotify_history = text(YNOTIFY_HISTORY)
    ynotify_detail = text(YNOTIFY_DETAIL)
    yentry_scope = text(YENTRY_SCOPE)

    for marker in (
        "fun YFeatureScaffold(",
        "fun YFeatureCustomScaffold(",
        "fun YFeatureSectionHeader(",
        "fun YFeatureCard(",
        "fun YFeatureEmpty(",
        "fun YFeatureStat(",
        "fun YStatusRow(",
        "fun YSettingSwitch(",
        "fun YSearchField(",
    ):
        require(compose, marker, "Compose YUI framework")

    for marker in (
        "fun install(",
        "fun installFixed(",
        "fun sectionHeader(",
        "fun card(",
        "fun statusLine(",
        "fun emptyState(",
        "fun keyValueRow(",
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
    require(yentry, "YFeatureCustomScaffold(", "YEntryCleaner main screen")
    require(yparam, "YViewLayout.installFixed(", "YParam main screen")
    require(yparam, "YViewLayout.searchField(", "YParam main screen")
    require(yparam, "YViewLayout.switchRow(", "YParam main screen")
    require(yparam, "YViewLayout.setStatus(", "YParam main screen")

    require(ynfc_screen, "YFeatureScaffold(", "YNFC main screen")
    require(ynfc_screen, "YFeatureList(", "YNFC main screen")
    require(ynfc_screen, "YFeatureEmpty(", "YNFC main screen")
    require(ynfc_components, "YFeatureCard(", "YNFC content components")
    require(ynfc_components, "YStatusRow(", "YNFC content components")

    require(ytask, "YFeatureScaffold(", "YTaskManager main screen")
    require(ytask, "YSearchField(", "YTaskManager filter section")
    require(ytask, "YStatusPill(", "YTaskManager status section")
    require(ytask, "YFeatureEmpty(", "YTaskManager empty states")
    require(ytask, "YStatusRow(", "YTaskManager resource metrics")

    require(ydiag, "YComposeActivity", "YDiag activity")
    require(ydiag, "YFeatureScaffold(", "YDiag main screen")
    require(ydiag, "YFeatureSectionHeader(", "YDiag sections")
    require(ydiag, "YFeatureStat(", "YDiag monitor stats")
    require(ydiag, "YFeatureEmpty(", "YDiag empty states")
    require(ydiag, "YSearchField(", "YDiag app picker")

    if "Widget.YUI." not in ynotify or "TextAppearance.YUI." not in ynotify:
        fail("YNotify XML main layout must consume shared Widget.YUI and TextAppearance.YUI styles")
    if "@dimen/yui_" not in ynotify:
        fail("YNotify XML main layout must consume shared YUI dimensions")

    # Secondary/detail screens.
    for source, label in (
        (yparam_detail, "YParam AppDetail"),
        (ypower_detail, "YPower AppDetail"),
        (ypower_recommended, "YPower RecommendedApps"),
    ):
        require(source, "YViewLayout.", label)
    require(yparam_detail, "YViewLayout.sectionHeader(", "YParam AppDetail")
    require(yparam_detail, "YViewLayout.setStatus(", "YParam AppDetail")
    require(ypower_detail, "YViewLayout.sectionHeader(", "YPower AppDetail")
    require(ypower_recommended, "YViewLayout.emptyState(", "YPower RecommendedApps")

    for source, label in (
        (ynotify_history, "YNotify AppHistory XML"),
        (ynotify_detail, "YNotify EventDetail XML"),
    ):
        if "@dimen/yui_" not in source:
            fail(f"{label} must consume shared YUI dimensions")
    if "Widget.YUI.Button.Outlined" not in ynotify_history:
        fail("YNotify AppHistory XML must use the shared outlined button")
    if "TextAppearance.YUI.SectionTitle" not in ynotify_detail or "Widget.YUI.Button.Tonal" not in ynotify_detail:
        fail("YNotify EventDetail XML must use shared section text and tonal button styles")

    require(yentry_scope, "YFeatureSectionHeader(", "YEntryCleaner scope screen")
    require(yentry_scope, "YFeatureCard(", "YEntryCleaner scope screen")
    require(yentry_scope, "YStatusRow(", "YEntryCleaner scope screen")
    require(yentry_scope, "YFeatureEmpty(", "YEntryCleaner scope screen")

    reject_hardcoded_normal_screen_colors(ypower, "YPower MainActivity")
    reject_hardcoded_normal_screen_colors(yminiguard, "YMiniGuard MainActivity")
    reject_hardcoded_normal_screen_colors(yparam, "YParam MainActivity")
    reject_hardcoded_normal_screen_colors(ynfc_components, "YNFC normal content components")
    reject_hardcoded_normal_screen_colors(ytask, "YTaskManager main screen")
    reject_hardcoded_normal_screen_colors(ydiag, "YDiag main screen")
    reject_hardcoded_normal_screen_colors(yparam_detail, "YParam AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_detail, "YPower AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_recommended, "YPower RecommendedApps")

    # YNFC's log console intentionally keeps a diagnostic high-contrast palette; normal cards and
    # status colors live in NfcComponents and are protected above.
    print(
        "ui-framework: OK compose=YFeature* view=YViewLayout xml=Widget.YUI "
        "primary=YSuite,YPower,YMiniGuard,YNotify,YEntryCleaner,YParam,YNFC,YTaskManager,YDiag "
        "secondary=YParam.AppDetail,YPower.AppDetail,YPower.RecommendedApps,YNotify.AppHistory,"
        "YNotify.EventDetail,YEntryCleaner.Scope"
    )


if __name__ == "__main__":
    main()
