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
    ROOT / "next/feature",
)
# Navigation structures and custom product workspaces may compose other Material3 primitives.
# Common controls instead pass through libs/yui to share geometry, defaults and accessibility.
CONTROL_IMPORT = re.compile(
    r"^import\s+androidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField)\b",
    re.MULTILINE,
)
PRODUCT_CONTROL_IMPORT = re.compile(
    r"^import\\s+androidx\\.compose\\.material3\\.(TextField|ListItem)\\b",
    re.MULTILINE,
)
PRODUCT_CONTROL_FQCN = re.compile(
    r"\\bandroidx\\.compose\\.material3\\.(TextField|ListItem)\\s*\\("
)
CONTROL_FQCN = re.compile(
    r"\bandroidx\.compose\.material3\."
    r"(Button|OutlinedButton|TextButton|IconButton|Checkbox|OutlinedTextField)\s*\("
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
            elif (
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
