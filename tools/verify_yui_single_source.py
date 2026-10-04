#!/usr/bin/env python3
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPS = ROOT / "apps"
YUI_ROOT = ROOT / "libs/yui/src/main/java/com/yagay/yui"
YUI = YUI_ROOT / "YUnifiedDesign.kt"
YUI_ADAPTIVE = YUI_ROOT / "YAdaptive.kt"
YUI_FORMS = YUI_ROOT / "YForms.kt"
YUI_TOKENS = YUI_ROOT / "YTokens.kt"
YUI_ICONS = YUI_ROOT / "YIcons.kt"
YUI_INTERACTIONS = YUI_ROOT / "YInteractions.kt"
YUI_CATALOG = YUI_ROOT / "YCatalog.kt"
GENERATED_TOKENS = YUI_ROOT / "YGeneratedTokens.kt"
NEW_FEATURE = ROOT / "tools/new_feature.py"
TOKEN_GENERATOR = ROOT / "tools/generate_yui_tokens.py"

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
REQUIRED_ADAPTIVE_MARKERS = (
    "enum class YWindowWidthClass", "data class YPageTemplate", "fun YAdaptiveBox(",
    "fun YAppShell(", "fun YNavigationSuite(", "fun YListDetailScaffold(",
    "fun YSupportingPaneScaffold(", "fun YResponsiveGrid(", "fun YBreadcrumbBar(",
)
REQUIRED_FORM_MARKERS = (
    "fun YIconAction(", "fun YTextField(", "fun YNumberField(", "fun YPasswordField(",
    "fun <T> YRadioGroup(", "fun YSliderField(", "fun <T> YSegmentedControl(",
    "fun <T> YDropdownField(", "fun YPickerItem(", "fun YConfirmDialog(",
    "fun YBottomSheet(", "fun YOverflowMenu(", "fun YMessageHost(",
)
REQUIRED_TOKEN_MARKERS = (
    "data class YSemanticColors", "object YMotion", "object YAccessibility", "fun ySemanticColors()",
)
REQUIRED_INTERACTION_MARKERS = (
    "fun YExpandableItem(", "fun YTreeItem(", "fun YGridCard(", "fun YPagerActions(",
    "fun YReorderActions(", "fun YListSkeleton(",
)

MODULE_UI = {
    "YDiag": (Path("apps/YDiag/feature/src/main/java/com/yagay/ydiag/ui/MainActivity.kt"), ("YPageScaffold", "YAppShell")),
    "YDownload": (
        Path("apps/YDownload/feature/src/main/java/com/yagay/ydownload/MainActivity.kt"),
        ("YPageScaffold", "YPageRole.MANAGER", "YPageRole.SETTINGS"),
    ),
    "YEntryCleaner": (Path("apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainActivity.kt"), ("com.yagay.yui", "YAppShell")),
    "YFiles": (
        Path("apps/YFiles/feature/src/main/java/com/yagay/yfiles/MainActivity.kt"),
        ("YPageScaffold", "YPageRole.BROWSER", "YPageRole.MANAGER", "YPageRole.SETTINGS", "YPageList"),
    ),
    "YFloat": (Path("apps/YFloat/feature/src/main/java/com/yagay/YFloat/MainActivity.java"), ("com.yagay.yui.YViewLayout",)),
    "YMiniGuard": (Path("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java"), ("YViewLayout",)),
    "YNFC": (Path("apps/YNFC/feature/src/main/java/com/yagay/YNFC/ui/NfcAppScreen.kt"), ("YManagerScaffold", "YPageList")),
    "YNotify": (Path("apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/MainActivity.java"), ("YViewLayout", "YViewPage")),
    "YParam": (Path("apps/YParam/feature/src/main/java/com/yagay/yparam/ui/MainActivity.java"), ("YViewLayout",)),
    "YPower": (Path("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java"), ("YViewLayout",)),
    "YTaskManager": (Path("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"), ("YManagerScaffold", "YAppShell")),
}


def fail(message: str) -> None:
    print(f"yui-single-source: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing required UI file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require_markers(path: Path, markers: tuple[str, ...]) -> None:
    source = read(path)
    for marker in markers:
        if marker not in source:
            fail(f"{path.relative_to(ROOT)} missing shared YUI marker {marker!r}")


def main() -> None:
    subprocess.run([sys.executable, str(TOKEN_GENERATOR), "--check"], cwd=ROOT, check=True)

    require_markers(YUI, REQUIRED_YUI_MARKERS)
    require_markers(YUI_ADAPTIVE, REQUIRED_ADAPTIVE_MARKERS)
    require_markers(YUI_FORMS, REQUIRED_FORM_MARKERS)
    require_markers(YUI_TOKENS, REQUIRED_TOKEN_MARKERS)
    require_markers(YUI_INTERACTIONS, REQUIRED_INTERACTION_MARKERS)
    if "object YIcons" not in read(YUI_ICONS):
        fail("shared YUI icon vocabulary is missing")

    yui = read(YUI)
    if '@Suppress("UNUSED_PARAMETER") role' in yui or "role = role" not in yui:
        fail("YPageRole must actively drive shared layout behavior")

    generated = read(GENERATED_TOKENS)
    for marker in ("ButtonHeight = 48.dp", "TouchTarget = 48.dp", "ExpandedBreakpoint = 840.dp", "ScreenHorizontalMedium = 24.dp", "ScreenHorizontalExpanded = 32.dp"):
        if marker not in generated:
            fail(f"generated YUI tokens missing accessibility/adaptive marker {marker!r}")

    catalog = read(YUI_CATALOG)
    if "fun YComponentCatalogScreen(" not in catalog or catalog.count("@Preview") < 3:
        fail("YUI catalog must include the living screen plus compact/dark/large-font previews")

    generator = read(NEW_FEATURE)
    for marker in ("YPageScaffold", "YPageList", "YPageRole", "YListItem", "src/main/res/values/strings.xml"):
        if marker not in generator:
            fail(f"new feature generator must use shared YUI marker {marker!r}")
    for legacy in ("YFeatureScaffold", "YFeatureCard", "YFeatureList"):
        if legacy in generator:
            fail(f"new feature generator must not emit legacy UI marker {legacy!r}")

    offenders: list[str] = []
    for path in APPS.glob("*/feature/src/main/**/*"):
        if not path.is_file() or path.name not in FORBIDDEN_NAMES:
            continue
        relative = path.relative_to(ROOT)
        offenders.append(str(relative))
    if offenders:
        fail("feature-local UI systems are forbidden; use libs/yui instead: " + ", ".join(sorted(offenders)))

    page_layouts = sorted(
        str(path.relative_to(ROOT))
        for path in APPS.glob("*/feature/src/main/res/layout/activity_*.xml")
        if path.is_file()
    )
    if page_layouts:
        fail(
            "feature-owned page XML is forbidden; build normal screens from shared YUI shells: "
            + ", ".join(page_layouts)
        )

    activity_offenders: list[str] = []
    for path in APPS.glob("*/feature/src/main/java/**/*Activity.*"):
        if not path.is_file() or path.suffix not in {".kt", ".java"}:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        if any(
            marker in source
            for marker in (
                "YComposeActivity",
                "YViewLayout",
                "YViewPage",
                "YUiWindowOptOut",
                "com.yagay.yui",
            )
        ):
            continue
        activity_offenders.append(str(path.relative_to(ROOT)))
    if activity_offenders:
        fail(
            "normal feature Activities must use the shared YUI activity/page framework: "
            + ", ".join(sorted(activity_offenders))
        )

    for module, (path, markers) in MODULE_UI.items():
        source = read(path)
        for marker in markers:
            if marker not in source:
                fail(f"{module} normal screen must consume shared YUI marker {marker!r}")

    raw_navigation: list[str] = []
    for path in APPS.glob("*/feature/src/main/**/*"):
        if not path.is_file() or path.suffix not in {".kt", ".java"}:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        owns_material_nav = (
            ("import androidx.compose.material3.NavigationBar" in source and "NavigationBar(" in source)
            or ("import androidx.compose.material3.NavigationRail" in source and "NavigationRail(" in source)
            or "androidx.compose.material3.NavigationBar(" in source
            or "androidx.compose.material3.NavigationRail(" in source
        )
        if owns_material_nav:
            raw_navigation.append(str(path.relative_to(ROOT)))
    if raw_navigation:
        fail(
            "feature-owned top-level Material navigation is forbidden; use YAppShell: "
            + ", ".join(sorted(raw_navigation))
        )

    legacy_shells: list[str] = []
    for path in APPS.glob("*/feature/src/main/**/*"):
        if not path.is_file() or path.suffix not in {".kt", ".java"}:
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        if "YNavigationSuite(" in source:
            legacy_shells.append(str(path.relative_to(ROOT)))
    if legacy_shells:
        fail(
            "feature modules must use YAppShell instead of the compatibility YNavigationSuite alias: "
            + ", ".join(sorted(legacy_shells))
        )



    print("yui-single-source: OK modules=11 roles=active adaptive=yes forms=yes interactions=yes catalog=yes tokens=generated local-ui-frameworks=forbidden")


if __name__ == "__main__":
    main()
