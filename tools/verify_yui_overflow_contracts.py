#!/usr/bin/env python3
"""Regression guard for YUI layouts under extreme preferences and available widths.

Source-level checks and deterministic arithmetic cases: NOT a device screenshot test.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str, *need: str) -> str:
    text = (ROOT / path).read_text(encoding="utf-8")
    for value in need:
        if value not in text:
            raise AssertionError(f"{path}: missing adaptive-layout contract: {value}")
    return text


def check_view() -> None:
    source("libs/yui/src/main/java/com/yagay/yui/YView.kt",
           "fun fitHorizontalInset(", "minContentDp", "widthPixels",
           "fun navigationAccessoryWidth(", "fun buttonHorizontalPadding(",
           "fun switchSlotWidth(")
    shared = source("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt",
                    "class YAdaptiveActionLayout(", "required > available",
                    "orientation = if (stack) VERTICAL else HORIZONTAL",
                    "YAdaptiveActionLayout(parent.context)",
                    "YAdaptiveActionLayout(context)",
                    "ColorUtils.blendARGB(", "android.R.attr.state_enabled",
                    "YView.navigationAccessoryWidth(context)",
                    "val needsVertical = vertical")
    if "LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)" not in shared:
        raise AssertionError("View action buttons lost their equal-width layout where space allows")
    source("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/DiagnosticActivity.java",
           "getWindow().getDecorView().addOnLayoutChangeListener(", "windowHeight * 2 / 3")


def check_compose() -> None:
    source("libs/yui/src/main/java/com/yagay/yui/YTheme.kt",
           "LocalConfiguration.current.screenWidthDp", "configuredAppearance.copy(",
           "screenPaddingDp = configuredAppearance.screenPaddingDp.coerceAtMost(",
           "BoxWithConstraints {", "val fittedInset = minOf(")
    source("libs/yui/src/main/java/com/yagay/yui/YUnifiedDesign.kt",
           "val fittedInset = minOf(", "maxWidth - 144.dp",
           "titleMaxLines: Int = Int.MAX_VALUE",
           "subtitleMaxLines: Int = Int.MAX_VALUE")
    source("libs/yui/src/main/java/com/yagay/yui/YResponsiveForms.kt",
           "LocalYAppearance.current.fontPercent / 100f")
    core = source("next/core/productui/src/main/java/com/yagay/ysuite/productui/ProductAdaptive.kt",
                  "val availableWidth: Dp", "availableWidth = maxWidth",
                  "fun ProductAdaptiveInfo.fitPanes(", "minimumMain: Dp = 320.dp",
                  "val widthBudget =", "fittedLeading.takeIf", "fittedTrailing.takeIf")
    if core.count("availableWidth = maxWidth") < 2:
        raise AssertionError("nested product panes must track local available width")

    for module in ("filemanager/FileExplorerWorkspace.kt", "entity/LibCheckerWorkspace.kt",
                   "nfc/NfcGateWorkspace.kt", "task/AndroidTaskManagerWorkspace.kt",
                   "logs/LogcatReaderWorkspace.kt", "settings/ComposeSettingsSurface.kt"):
        path = "next/core/productui/src/main/java/com/yagay/ysuite/productui/" + module
        code = source(path, "adaptive.fitPanes(", ".width(panes.")
        if ".width(ProductLayoutTokens." in code:
            raise AssertionError(f"{path}: expanded panes bypass the shared fitting policy")


def check_extreme_geometry() -> None:
    # Mirrors the deliberately simple upper-bound math used by View and Compose.
    for width in (320, 360, 480, 840, 1280):
        for configured in (0, 16, 128, 320, 512):
            screen_inset = min(configured, max(0, width - 144) / 2)
            card_inset = min(configured, max(0, width - 176) / 2)
            assert 2 * screen_inset + 144 <= width
            assert 2 * card_inset + 176 <= width

    # Both panes at the largest 250% preference must retain a central surface.
    for width in (840, 900, 1280, 1920):
        for scale in (0.25, 1.0, 2.5):
            first, second = 280 * scale, 360 * scale
            budget = max(0, width - 320)
            ratio = min(1, budget / (first + second))
            left, right = first * ratio, second * ratio
            if left < 160: left = 0
            if right < 200: right = 0
            assert width - left - right >= 320


def main() -> None:
    check_view()
    check_compose()
    check_extreme_geometry()
    print("YUI overflow contracts: OK compact, font-scaled, extreme spacing, 250% panes")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, FileNotFoundError) as error:
        print(f"YUI overflow contracts: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
