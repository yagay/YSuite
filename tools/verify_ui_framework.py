#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

YUI_COMPOSE = ROOT / "libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt"
YUI_VIEW = ROOT / "libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt"
YUI_RES = ROOT / "libs/yui/src/main/res/values/yui.xml"
YSUITE = ROOT / "suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt"
YPOWER = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"
YMINIGUARD = ROOT / "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java"
YNOTIFY = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/MainActivity.java"
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
YPOWER_DIAG = ROOT / "apps/YPower/feature/src/main/java/com/yagay/ypower/ui/DiagnosticActivity.java"
YNOTIFY_HISTORY = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/AppHistoryActivity.java"
YNOTIFY_DETAIL = ROOT / "apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventDetailActivity.java"
YENTRY_SCOPE = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/ScopeScreen.kt"
YENTRY_ROOT = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/RootComponentsScreen.kt"
YENTRY_TABS = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainTabs.kt"
YENTRY_PRIORITY = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/PriorityDialog.kt"
YENTRY_ROWS = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/AppListRows.kt"


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
    ynotify = text(YNOTIFY)
    yentry = text(YENTRY)
    yparam = text(YPARAM)
    ynfc_screen = text(YNFC_SCREEN)
    ynfc_components = text(YNFC_COMPONENTS)
    ytask = text(YTASK)
    ydiag = text(YDIAG)
    yparam_detail = text(YPARAM_DETAIL)
    ypower_detail = text(YPOWER_DETAIL)
    ypower_recommended = text(YPOWER_RECOMMENDED)
    ypower_diag = text(YPOWER_DIAG)
    ynotify_history = text(YNOTIFY_HISTORY)
    ynotify_detail = text(YNOTIFY_DETAIL)
    yentry_scope = text(YENTRY_SCOPE)
    yentry_root = text(YENTRY_ROOT)
    yentry_tabs = text(YENTRY_TABS)
    yentry_priority = text(YENTRY_PRIORITY)
    yentry_rows = text(YENTRY_ROWS)

    for marker in (
        "fun YPageScaffold(",
        "fun YSectionHeader(",
        "fun YSection(",
        "fun YEmptyMessage(",
        "fun YMetricCard(",
        "fun YStatusLine(",
        "fun YSwitchItem(",
        "fun YActionGroup(",
        "fun YHorizontalActions(",
    ):
        require(compose, marker, "Compose YUI framework")

    for marker in (
        "fun install(",
        "fun installPage(",
        "fun page(",
        "fun installFixed(",
        "fun filterBar(",
        "fun bottomNavigation(",
        "fun sectionHeader(",
        "fun card(",
        "fun statusLine(",
        "fun emptyState(",
        "fun keyValueRow(",
        "fun switchRow(",
        "fun searchField(",
        "fun listRow(",
        "fun navigationRow(",
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

    require(ysuite, "YDashboardScaffold(", "YSuite main screen")
    require(ysuite, "YPageList(", "YSuite main screen")
    require(ysuite, "YSection(", "YSuite main screen")
    require(ypower, "YViewLayout.install(", "YPower main screen")
    require(ypower, "YViewLayout.card(", "YPower main screen")
    require(yminiguard, "YViewLayout.install(", "YMiniGuard main screen")
    require(yminiguard, "YViewLayout.switchRow(", "YMiniGuard main screen")
    require(yminiguard, "YViewLayout.navigationRow(", "YMiniGuard app-list navigation")
    require(yentry, "YCustomScaffold(", "YEntryCleaner main screen")
    require(yentry, "YAppShell(", "YEntryCleaner main navigation")
    require(yparam, "YViewLayout.installFixed(", "YParam main screen")
    require(yparam, "YViewLayout.searchField(", "YParam main screen")
    require(yparam, "YViewLayout.switchRow(", "YParam main screen")
    require(yparam, "YViewLayout.setStatus(", "YParam main screen")

    require(ynfc_screen, "YManagerScaffold(", "YNFC main screen")
    require(ynfc_screen, "YPageList(", "YNFC main screen")
    require(ynfc_screen, "YEmptyMessage(", "YNFC main screen")
    require(ynfc_components, "YSection(", "YNFC content components")
    require(ynfc_components, "YStatusLine(", "YNFC content components")

    require(ytask, "YManagerScaffold(", "YTaskManager main screen")
    require(ytask, "YAppShell(", "YTaskManager main navigation")
    require(ytask, "YSearchField(", "YTaskManager filter section")
    require(ytask, "YStatusStrip(", "YTaskManager status section")
    require(ytask, "YToggleFilterBar(", "YTaskManager type filters")
    require(ytask, "YFilterBar(", "YTaskManager sort filter")
    require(ytask, "YListItem(", "YTaskManager process rows")
    require(ytask, "YPageList(", "YTaskManager resource/network pages")
    require(ytask, "YEmptyMessage(", "YTaskManager empty states")
    require(ytask, "YStatusLine(", "YTaskManager resource metrics")

    require(ydiag, "YComposeActivity", "YDiag activity")
    require(ydiag, "YPageScaffold(", "YDiag main screen")
    require(ydiag, "YAppShell(", "YDiag main navigation")
    require(ydiag, "YPageList(", "YDiag page bodies")
    require(ydiag, "YSectionHeader(", "YDiag sections")
    require(ydiag, "YListItem(", "YDiag list rows")
    require(ydiag, "YFilterBar(", "YDiag filters")
    require(ydiag, "YCheckboxItem(", "YDiag app picker rows")
    require(ydiag, "YMetricCard(", "YDiag monitor stats")
    require(ydiag, "YEmptyMessage(", "YDiag empty states")
    require(ydiag, "YSearchField(", "YDiag app picker")

    require(ynotify, "YViewLayout.installPage(", "YNotify main screen")
    require(ynotify, "YViewLayout.filterBar(", "YNotify timeline filters")
    require(ynotify, "YViewLayout.bottomNavigation(", "YNotify main navigation")
    require(ynotify, "YViewLayout.section(", "YNotify settings sections")

    # Secondary/detail screens.
    for source, label in (
        (yparam_detail, "YParam AppDetail"),
        (ypower_detail, "YPower AppDetail"),
        (ypower_recommended, "YPower RecommendedApps"),
        (ypower_diag, "YPower Diagnostic"),
    ):
        require(source, "YViewLayout.", label)
    require(yparam_detail, "YViewLayout.sectionHeader(", "YParam AppDetail")
    require(yparam_detail, "YViewLayout.setStatus(", "YParam AppDetail")
    require(ypower_detail, "YViewLayout.sectionHeader(", "YPower AppDetail")
    require(ypower_detail, "YViewLayout.listRow(", "YPower AppDetail toggles")
    require(ypower_recommended, "YViewLayout.emptyState(", "YPower RecommendedApps")
    require(ypower_diag, "YViewLayout.install(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.card(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.actionRow(", "YPower Diagnostic")
    require(ypower_diag, "YViewLayout.setStatus(", "YPower Diagnostic")

    require(ynotify_history, "YViewLayout.installPage(", "YNotify AppHistory")
    require(ynotify_history, "YViewLayout.switchRow(", "YNotify AppHistory settings")
    require(ynotify_history, "YViewLayout.filterBar(", "YNotify AppHistory filters")
    require(ynotify_detail, "YViewLayout.installPage(", "YNotify EventDetail")
    require(ynotify_detail, "YViewLayout.sectionHeader(", "YNotify EventDetail sections")

    require(yentry_scope, "YFullScreenDialog(", "YEntryCleaner scope screen")
    require(yentry_scope, "YSectionHeader(", "YEntryCleaner scope screen")
    require(yentry_scope, "YListItem(", "YEntryCleaner scope screen")
    require(yentry_scope, "YStatusItem(", "YEntryCleaner scope screen")
    require(yentry_scope, "YNotice(", "YEntryCleaner scope screen")
    require(yentry_root, "YSectionHeader(", "YEntryCleaner component screen")
    require(yentry_root, "YSection(", "YEntryCleaner component screen")
    require(yentry_root, "YStatusLine(", "YEntryCleaner component screen")
    require(yentry_root, "YEmptyMessage(", "YEntryCleaner component screen")

    # Rules/Priority keep their specialized interaction surfaces, but normal summary/status/empty
    # presentation must stay on YUI. The interaction markers below intentionally protect drag,
    # lock swipe, category filtering and search from being lost during visual refactors.
    for source, label in (
        (yentry_tabs, "YEntryCleaner RulesTab"),
        (yentry_priority, "YEntryCleaner PriorityDialog"),
    ):
        require(source, "YSection(", label)
        require(source, "YStatusLine(", label)
        require(source, "YEmptyMessage(", label)
    require(yentry_tabs, "AppRow(", "YEntryCleaner RulesTab app rows")
    require(yentry_tabs, "ListControls(", "YEntryCleaner RulesTab filters")
    require(yentry_rows, "bulkLockSwipe", "YEntryCleaner RulesTab lock interaction")
    require(yentry_priority, "detectDragGesturesAfterLongPress", "YEntryCleaner Priority drag ordering")
    require(yentry_priority, "bulkLockSwipe", "YEntryCleaner Priority lock interaction")
    require(yentry_priority, "ListControls(", "YEntryCleaner Priority filters")
    require(yentry_priority, "movePriority(kind, packageName", "YEntryCleaner Priority move buttons")

    reject_hardcoded_normal_screen_colors(ypower, "YPower MainActivity")
    reject_hardcoded_normal_screen_colors(yminiguard, "YMiniGuard MainActivity")
    reject_hardcoded_normal_screen_colors(yparam, "YParam MainActivity")
    reject_hardcoded_normal_screen_colors(ynfc_components, "YNFC normal content components")
    reject_hardcoded_normal_screen_colors(ytask, "YTaskManager main screen")
    reject_hardcoded_normal_screen_colors(ydiag, "YDiag main screen")
    reject_hardcoded_normal_screen_colors(yparam_detail, "YParam AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_detail, "YPower AppDetail")
    reject_hardcoded_normal_screen_colors(ypower_recommended, "YPower RecommendedApps")

    # Specialized diagnostic text highlighting in YPower Diagnostic and YNFC's log console may
    # keep semantic high-contrast colors. Normal cards/status/chrome remain YUI-owned.
    print(
        "ui-framework: OK compose=YUI-v2 view=YViewPage/YViewLayout page-xml=forbidden "
        "primary=YSuite,YPower,YMiniGuard,YNotify,YEntryCleaner,YParam,YNFC,YTaskManager,YDiag "
        "secondary=YParam.AppDetail,YPower.AppDetail,YPower.RecommendedApps,YPower.Diagnostic,"
        "YNotify.AppHistory,YNotify.EventDetail,YEntryCleaner.Scope,YEntryCleaner.Components,"
        "YEntryCleaner.Rules,YEntryCleaner.Priority"
    )


if __name__ == "__main__":
    main()
