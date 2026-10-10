#!/usr/bin/env python3
from __future__ import annotations

import re
import json
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
        ("YComposeActivity", "YDownloadFeatureScreen"),
    ),
    "YEntryCleaner": (Path("apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui/MainActivity.kt"), ("com.yagay.yui", "YAppShell")),
    "YFiles": (
        Path("apps/YFiles/feature/src/main/java/com/yagay/yfiles/MainActivity.kt"),
        ("YComposeActivity", "YFilesFeatureScreen"),
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

    # Rebuilt feature libraries must never override the original host's Material3 theme
    # through a more-specific values-night resource qualifier.
    for qualifier in ("values", "values-night"):
        rebuilt_theme = ROOT / "next/core/designsystem/src/main/res" / qualifier / "themes.xml"
        if rebuilt_theme.is_file() and re.search(
            r'<style\s+name="Theme\.YSuite"', read(rebuilt_theme)
        ):
            fail(f"{rebuilt_theme.relative_to(ROOT)} redefines the main host theme")

    # All ordinary YFloat screens create Material components and require Theme.YFloat,
    # not an inherited theme from an unrelated host module.
    yfloat_manifest = read(APPS / "YFloat/feature/src/main/AndroidManifest.xml")
    for activity in (
        "MainActivity", "SettingsActivity", "AppearanceSettingsActivity",
        "GestureHubActivity", "DiagnosticsActivity", "MenuPickerActivity",
        "MenuLabelEditorActivity",
    ):
        pattern = (
            r'<activity\s+android:name="com\.yagay\.YFloat\.'
            + activity
            + r'"[^>]*android:theme="@style/Theme\.YFloat"'
        )
        if not re.search(pattern, yfloat_manifest):
            fail(f"YFloat {activity} must explicitly use Theme.YFloat")

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
    tokens = json.loads(read(ROOT / "libs/yui/yui_tokens.json"))
    # A Material default-size switch is wider/taller than most setting labels. Keep
    # its visual geometry separate from the 48dp minimum row/touch target.
    for key in (
        "switch_track_width", "switch_track_height", "switch_thumb_size",
        "switch_slot_width", "switch_slot_height",
    ):
        if key not in tokens:
            fail(f"missing canonical compact-switch geometry: {key}")
    if not (tokens["switch_track_width"] < tokens["switch_slot_width"]
            and tokens["switch_track_height"] < tokens["switch_slot_height"]
            and tokens["switch_thumb_size"] < tokens["switch_track_height"]):
        fail("compact switch geometry must fit inside its touch slot")
    if tokens["switch_slot_height"] >= tokens["option_row_height"]:
        fail("switch touch slot must not force an oversized settings row")
    if tokens["switch_slot_width"] < tokens["touch_target"]:
        fail("switch touch slot must meet the minimum horizontal touch size")
    compact_switch = read(YUI_ROOT / "YCompactSwitch.kt")
    if "Role.Switch" not in compact_switch or "YDimens.SwitchTrackWidth" not in compact_switch:
        fail("canonical Compose compact switch must have switch semantics and generated sizing")
    for shared_source in (YUI, YUI_ROOT / "YComponents.kt"):
        source = read(shared_source)
        if "YCompactSwitch(" not in source:
            fail(f"{shared_source.relative_to(ROOT)} must use the shared compact switch")
        if re.search(r"(?<![A-Za-z0-9_])Switch\(checked\s*=\s*checked", source):
            fail(f"{shared_source.relative_to(ROOT)} still uses the oversized Material switch")
    view_switches = read(YUI_ROOT / "YViewFramework.kt")
    if "compactToggle(this)" not in view_switches or "switchSlot(" not in view_switches:
        fail("Java/View switch rows must use the shared compact geometry")

    # Density may evolve, but the system still needs accessible hit targets and
    # ordered compact/medium/expanded breakpoints.
    if tokens["button_height"] < 48 or tokens["touch_target"] < 48:
        fail("YUI buttons and touch targets must remain at least 48dp")
    if not (
        tokens["screen_horizontal"] <= tokens["screen_horizontal_medium"]
        <= tokens["screen_horizontal_expanded"]
    ):
        fail("YUI horizontal padding must grow with the viewport")
    if not (
        tokens["compact_breakpoint"] < tokens["medium_breakpoint"]
        < tokens["expanded_breakpoint"]
    ):
        fail("YUI adaptive breakpoints must be ordered")
    for key, marker in (
        ("button_height", "ButtonHeight"),
        ("touch_target", "TouchTarget"),
        ("screen_horizontal_medium", "ScreenHorizontalMedium"),
        ("screen_horizontal_expanded", "ScreenHorizontalExpanded"),
    ):
        expected = f"{marker} = {tokens[key]}.dp"
        if expected not in generated:
            fail(f"generated YUI token missing source-matched marker {expected!r}")

    # Rebuilt feature compatibility layer must forward reusable UI into YUI.
    # Stop future local theme/button/form copies from creeping back in.
    for source, required in (
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/theme/YSuiteTheme.kt", ("import com.yagay.yui.YTheme", "YTheme(")),
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/theme/YSuiteTokens.kt", ("import com.yagay.yui.YDimens",)),
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/component/YSuiteComponents.kt", ("import com.yagay.yui.YPrimaryButton", "import com.yagay.yui.YListItem", "import com.yagay.yui.YCard")),
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/component/YSuiteControls.kt", ("import com.yagay.yui.YTextField", "import com.yagay.yui.YFilterBar")),
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/component/YSuiteStatus.kt", ("import com.yagay.yui.YStatusPill",)),
        (ROOT / "next/core/designsystem/src/main/java/com/yagay/ysuite/designsystem/component/YSuiteDialogs.kt", ("import com.yagay.yui.YConfirmDialog", "import com.yagay.yui.YTextField")),
    ):
        content = read(source)
        for token in required:
            if token not in content:
                fail(f"reusable feature component in {source.relative_to(ROOT)} must delegate to shared YUI: {token}")

    # One official Material 3 component set must own all normal control metrics.
    material_controls = read(YUI_ROOT / "YMaterialControls.kt")
    for name in ("YMaterialButton(", "YMaterialFilterChip(", "YButtonVariant.DANGER"):
        if name not in material_controls:
            fail(f"YUI Material 3 kit is missing {name}")
    yui_theme = read(YUI_ROOT / "YTheme.kt")
    if "dynamicColor: Boolean = false" not in yui_theme:
        fail("YUI should use the shared brand palette by default (dynamic color opt-in only)")
    if "YMaterialButton(" not in yui_theme:
        fail("YUI primary/secondary buttons must delegate to the canonical Material 3 kit")
    if "YMaterialButton(" not in read(YUI_FORMS):
        fail("YUI form dialogs must use canonical action buttons")
    view_styles = read(ROOT / "libs/yui/src/main/res/values/yui.xml")
    for style in ("Widget.YUI.Button.Dialog", "Widget.YUI.Switch", "Widget.YUI.MaterialSwitch", "Widget.YUI.Checkbox"):
        if style not in view_styles:
            fail(f"YUI View theme is missing shared Material 3 component {style}")
    for token in ("field_radius", "dialog_radius", "chip_radius"):
        if token not in tokens:
            fail(f"YUI is missing shared component shape token: {token}")

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

    local_color_files = sorted(
        path.relative_to(ROOT).as_posix()
        for path in APPS.glob("*/feature/src/main/res/values*/colors.xml")
        if path.is_file()
        and path.relative_to(ROOT).as_posix() != "apps/YFloat/feature/src/main/res/values/colors.xml"
    )
    if local_color_files:
        fail(
            "feature-local color palettes are forbidden; use Theme.YUI/YUI semantic tokens: "
            + ", ".join(local_color_files)
        )
    yfloat_colors = ROOT / "apps/YFloat/feature/src/main/res/values/colors.xml"
    if yfloat_colors.is_file():
        color_source = yfloat_colors.read_text(encoding="utf-8", errors="replace")
        if "transparent" not in color_source or "#" in color_source.replace("#00000000", ""):
            fail("YFloat colors.xml may only keep the specialized transparent window color")

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

    legacy_api_offenders: list[str] = []
    legacy_apis = (
        "YFeatureScaffold(", "YFeatureCustomScaffold(", "YFeatureCard(", "YFeatureSectionHeader(",
        "YFeatureEmpty(", "YFeatureStat(", "YStatusRow(", "YSettingSwitch(", "YActionRow(", "YFeatureList(",
    )
    for root in (APPS, ROOT / "suite"):
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix not in {".kt", ".java"} or "/build/" in path.as_posix():
                continue
            source = path.read_text(encoding="utf-8", errors="replace")
            used = [api for api in legacy_apis if api in source]
            if used:
                legacy_api_offenders.append(f"{path.relative_to(ROOT)}: {', '.join(used)}")
    if legacy_api_offenders:
        fail("legacy YUI APIs are forbidden in feature/host sources: " + "; ".join(sorted(legacy_api_offenders)))

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

    raw_view_controls: list[str] = []
    forbidden_view_constructors = (
        "new MaterialCheckBox(",
        "new Slider(",
        "new Spinner(",
        "new MaterialButton(",
        "new BottomNavigationView(",
        "new MaterialToolbar(",
    )
    for path in APPS.glob("*/feature/src/main/java/**/*.java"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT).as_posix()
        if relative.startswith("apps/YFloat/feature/src/main/java/com/yagay/YFloat/") and any(
            name in relative
            for name in (
                "FloatingMenuUi.java",
                "ResultUi.java",
            )
        ):
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        used = [item for item in forbidden_view_constructors if item in source]
        if used:
            raw_view_controls.append(f"{relative}: {', '.join(used)}")
    if raw_view_controls:
        fail(
            "normal Java/View screens must construct shared controls through YViewLayout: "
            + "; ".join(sorted(raw_view_controls))
        )

    raw_compose_controls: list[str] = []
    forbidden_compose_imports = (
        "androidx.compose.material3.Scaffold",
        "androidx.compose.material3.TopAppBar",
        "androidx.compose.material3.MediumTopAppBar",
        "androidx.compose.material3.LargeTopAppBar",
        "androidx.compose.material3.NavigationBar",
        "androidx.compose.material3.NavigationRail",
        "androidx.compose.material3.ScrollableTabRow",
        "androidx.compose.material3.Tab",
        "androidx.compose.material3.Button",
        "androidx.compose.material3.OutlinedButton",
        "androidx.compose.material3.OutlinedTextField",
        "androidx.compose.material3.TextField",
        "androidx.compose.material3.Switch",
        "androidx.compose.material3.Checkbox",
        "androidx.compose.material3.FilterChip",
        "androidx.compose.material3.AlertDialog",
        "androidx.compose.material3.RadioButton",
    )
    for path in APPS.glob("*/feature/src/main/java/**/*.kt"):
        if not path.is_file():
            continue
        source = path.read_text(encoding="utf-8", errors="replace")
        used = [
            item.rsplit(".", 1)[-1]
            for item in forbidden_compose_imports
            if f"import {item}\n" in source
        ]
        if "import androidx.compose.material3.*" in source:
            used.append("material3.*")
        if used:
            raw_compose_controls.append(f"{path.relative_to(ROOT)}: {', '.join(sorted(set(used)))}")
    if raw_compose_controls:
        fail(
            "feature Compose UI must use shared YUI interaction primitives instead of raw Material3 controls: "
            + "; ".join(sorted(raw_compose_controls))
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
