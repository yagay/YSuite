#!/usr/bin/env python3
"""Prevent ordinary feature UI from silently introducing a second set of Material3 controls."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = (
    ROOT / "apps",
    ROOT / "suite",
    ROOT / "next/core/ui",
    ROOT / "next/core/productui",
    ROOT / "next/core/designsystem",
    ROOT / "next/feature",
)
# Navigation structures and custom product workspaces may compose other Material3 primitives.
# Common controls instead pass through libs/yui to share geometry, defaults and accessibility.
CONTROL_IMPORT = re.compile(
    r"^import\s+androidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField|AlertDialog|Slider|RadioButton|ScrollableTabRow|Switch)\b",
    re.MULTILINE,
)
PRODUCT_CONTROL_IMPORT = re.compile(
    r"^import\s+androidx\.compose\.material3\.(TextField|ListItem)\b",
    re.MULTILINE,
)
PRODUCT_CONTROL_FQCN = re.compile(
    r"\bandroidx\.compose\.material3\.(TextField|ListItem)\s*\("
)
CONTROL_FQCN = re.compile(
    r"\bandroidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField|AlertDialog|Slider|RadioButton|ScrollableTabRow|Switch)\s*\("
)

def main() -> None:
    failures: list[str] = []
    scanned = 0
    for directory in SOURCES:
        if not directory.is_dir():
            continue
        for path in directory.rglob("*"):
            if path.suffix not in (".kt", ".java") or not path.is_file():
                continue
            if any(x in path.parts for x in ("build", "test", "androidTest")):
                continue
            source = path.read_text(encoding="utf-8", errors="replace")
            scanned += 1
            if path.suffix == ".kt":
                bad = sorted(set(CONTROL_IMPORT.findall(source) + CONTROL_FQCN.findall(source)))
                if path.is_relative_to(ROOT / "next/core/productui"):
                    bad.extend(PRODUCT_CONTROL_IMPORT.findall(source))
                    bad.extend(PRODUCT_CONTROL_FQCN.findall(source))
                if "import androidx.compose.material3.*" in source:
                    bad.append("material3 wildcard import")
                if bad:
                    failures.append(f"{path.relative_to(ROOT)}: {', '.join(bad)}")
            elif path.suffix == ".java":
                # Non-YUI screen modules may hold SwitchCompat references, but cannot
                # instantiate a second switch style or change the track/thumb locally.
                if re.search(
                    r"\\bnew\\s+(?:SwitchMaterial|MaterialSwitch|SwitchCompat|Switch)\\s*\\(",
                    source,
                ):
                    failures.append(f"{path.relative_to(ROOT)}: construct switches using YViewLayout.switchRow")
                if re.search(r"\\.set(?:Thumb|Track|TrackDecoration)Tint(?:List|Mode)?\\s*\\(", source):
                    failures.append(f"{path.relative_to(ROOT)}: only YUI may style switch colors")
                if (
                    "import androidx.appcompat.app.AlertDialog;" in source
                    and "new AlertDialog.Builder(" in source
                ):
                    failures.append(f"{path.relative_to(ROOT)}: use YViewDialogs.builder()")
    # Both modern Compose features and legacy compatibility facades must share one
    # implementation for normal-screen controls. Catch accidental future duplication.
    core = ROOT / "libs/yui/src/main/java/com/yagay/yui"
    expected_controls = {
        "YMaterialControls.kt": (
            "fun YUiButton(",
            "fun YUiOutlinedButton(",
            "fun YUiTextButton(",
            "fun YUiIconButton(",
            "fun YUiCheckbox(",
        ),
        "YMaterialInputs.kt": ("fun YUiOutlinedTextField(",),
        "YMaterialDialogs.kt": ("fun YUiAlertDialog(",),
        "YMaterialSelectors.kt": ("fun YUiSlider(", "fun YUiRadioButton(", "fun YUiScrollableTabRow("),
        "YMaterialNavigation.kt": ("fun YUiNavigationDrawerItem(",),
        "YViewDialogs.kt": ("fun builder(context: Context)",),
        "YTheme.kt": ("YUiButton(", "YUiOutlinedButton(", "YUiScaffold("),
        "YFeatureFramework.kt": ("YUiOutlinedTextField as OutlinedTextField",),
    }
    for filename, markers in expected_controls.items():
        path = core / filename
        if not path.is_file():
            failures.append(f"missing YUI implementation: {filename}")
            continue
        implementation = path.read_text(encoding="utf-8")
        for required in markers:
            if required not in implementation:
                failures.append(f"YUI {filename} must delegate using {required}")

    # Detect duplicate YUI implementations that import-only checks cannot find.
    section_definitions = []
    for path in core.glob("*.kt"):
        code = path.read_text(encoding="utf-8")
        for match in re.finditer(r"^fun YSection\s*\(", code, re.MULTILINE):
            section_definitions.append(path.name)
        if path.name != "YTheme.kt" and re.search(r"^import androidx\.compose\.material3\.Scaffold\s*$", code, re.MULTILINE):
            failures.append(f"YUI {path.name} reintroduced a standalone Scaffold")
        if path.name != "YMaterialDialogs.kt" and re.search(r"^import androidx\.compose\.material3\.AlertDialog\s*$", code, re.MULTILINE):
            failures.append(f"YUI {path.name} reintroduced direct Material3 dialog rendering")
    switch_facade = (core / "YCompactSwitch.kt").read_text(encoding="utf-8")
    view_facade = (core / "YViewFramework.kt").read_text(encoding="utf-8")
    if switch_facade.count("Switch(") != 1:
        failures.append("Compose switch must be rendered exactly once in YStandardSwitch")
    if "YDimens.SwitchSlot" in switch_facade:
        failures.append("Compose switch slot may not use fixed YDimens")
    if view_facade.count("MaterialSwitch(context).apply") != 1:
        failures.append("View switch must have one MaterialSwitch implementation")
    if "SwitchMaterial(" in view_facade:
        failures.append("View switch may not resurrect the older SwitchMaterial renderer")
    if section_definitions != ["YUnifiedDesign.kt"]:
        failures.append(f"YSection must have exactly one implementation: {section_definitions}")
    yui_view = core / "YView.kt"
    yui_tokens = core / "YTokens.kt"
    if yui_view.is_file():
        view_source = yui_view.read_text(encoding="utf-8")
        if "YUiPalette.LightSuccess" not in view_source or "YUiPalette.DarkSuccess" not in view_source:
            failures.append("legacy View semantic colors must resolve both themes from the shared YUI palette")
    if yui_tokens.is_file() and "YUiPalette.LightSuccess" not in yui_tokens.read_text(encoding="utf-8"):
        failures.append("Compose semantic colors must be generated from the shared palette")

    chrome_path = ROOT / "next/core/productui/src/main/java/com/yagay/ysuite/productui/ProductChrome.kt"
    yui_theme_path = ROOT / "libs/yui/src/main/java/com/yagay/yui/YTheme.kt"
    if not chrome_path.is_file() or not yui_theme_path.is_file():
        failures.append("missing shared page chrome")
    else:
        chrome = chrome_path.read_text(encoding="utf-8")
        theme = yui_theme_path.read_text(encoding="utf-8")
        if "YUiScaffold(" not in chrome or "YCustomTopBar(" not in chrome:
            failures.append("ProductChrome must delegate both page Scaffold and TopAppBar to YUI")
        if "import androidx.compose.material3.Scaffold" in chrome or "import androidx.compose.material3.TopAppBar" in chrome:
            failures.append("ProductChrome cannot own independent Material3 page chrome")
        if "fun YUiScaffold(" not in theme or "fun YCustomTopBar(" not in theme:
            failures.append("YUI common Scaffold/TopAppBar entry points missing")
    if failures:
        print("yui-controls: independent Material3 controls found:", file=sys.stderr)
        for failure in failures:
            print(" - " + failure, file=sys.stderr)
        return 1
    print(f"yui-controls: OK scanned={scanned} shared controls=YUI")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
