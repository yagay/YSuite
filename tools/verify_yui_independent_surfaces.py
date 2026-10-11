#!/usr/bin/env python3
"""Enforce regressions discovered during the October 2026 cross-feature UI audit.

This gate checks actual renderer/data ownership, not merely an import of a YUI token.
Specialized Canvas overlays are allowed but appearance persistence remains YUI-owned.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, *tokens: str) -> str:
    body = source(path)
    for token in tokens:
        if token not in body:
            raise AssertionError(f"{path} lacks shared contract {token}")
    return body


def check() -> None:
    store_path = "libs/yui/src/main/java/com/yagay/yui/YAppearanceStore.kt"
    registry_path = "libs/yui/src/main/java/com/yagay/yui/YUiControlRegistry.kt"
    screen_path = "suite/YSuite/src/main/java/com/yagay/YSuite/SuiteSettingsScreen.kt"
    store = source(store_path)
    registry = source(registry_path)
    screen = source(screen_path)
    bridge = source("apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatVisualSettings.java")
    floats = source("apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatSettings.java")
    keys = (
        ("GOOGLE_FRAME_RADIUS", "K_GOOGLE_FRAME_RADIUS_DP", "googleFrameRadiusDp"),
        ("GOOGLE_FRAME_OUTLINE", "K_GOOGLE_FRAME_OUTLINE_DP", "googleFrameOutlineDp"),
        ("GOOGLE_FRAME_GLOW_WIDTH", "K_GOOGLE_FRAME_GLOW_WIDTH_DP", "googleFrameGlowWidthDp"),
        ("GOOGLE_FRAME_GLOW_BLUR", "K_GOOGLE_FRAME_GLOW_BLUR_DP", "googleFrameGlowBlurDp"),
        ("GOOGLE_FRAME_GLOW_ALPHA", "K_GOOGLE_FRAME_GLOW_ALPHA", "googleFrameGlowAlpha"),
        ("GOOGLE_FRAME_SCRIM_ALPHA", "K_GOOGLE_FRAME_SCRIM_ALPHA", "googleFrameScrimAlpha"),
        ("GOOGLE_FRAME_TRAIL_WIDTH", "K_GOOGLE_FRAME_TRAIL_WIDTH_DP", "googleFrameTrailWidthDp"),
    )
    for key, legacy, getter in keys:
        if (f"YSettingKey.{key}" not in registry or
                f"YSettingKey.{key} ->" not in screen or
                f"value(YSettingKey.{key}, moduleId)" not in store or
                f"FloatSettings.{legacy} -> YSettingKey.{key}" not in bridge or
                f"public int {getter}() {{ return YFloatVisualSettings.readInt(" not in floats):
            raise AssertionError(f"YFloat Google visual not sourced from YUI: {key}")
        if f"p.getInt({legacy}" in floats:
            raise AssertionError(f"YFloat duplicate SharedPreferences visual: {legacy}")
    require(store_path, 'GOOGLE_FRAME_YUI_COLOR("google_frame_use_yui_color", "true")')
    require(registry_path, "YSettingKey.GOOGLE_FRAME_YUI_COLOR")
    require(screen_path, "YSettingKey.GOOGLE_FRAME_YUI_COLOR ->")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/GoogleOverlayAppearance.java",
            "K_GOOGLE_FRAME_USE_YUI_COLOR, true")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/LsposedRuntimeStore.java",
            "YOverlayTokens.accent(", "settings.googleFrameRadiusDp()")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/LsposedStatusManager.java",
            'key.startsWith("global.")', 'key.startsWith("module.yfloat.")')
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatVisualSettings.java",
            "migrateGoogleFrame(", "GOOGLE_MIGRATED")

    downloader = source("next/feature/ydownload/impl/src/main/java/com/yagay/ysuite/feature/ydownload/YDownloadFeatureScreen.kt")
    for forbidden in ("import android.app.DatePickerDialog", "import android.app.TimePickerDialog", "DatePickerDialog(", "TimePickerDialog("):
        if forbidden in downloader:
            raise AssertionError(f"YDownload owns an independent native picker: {forbidden}")
    if "YViewTransientControls.datePicker(" not in downloader or "YViewTransientControls.timePicker(" not in downloader:
        raise AssertionError("YDownload must use the common YUI picker adapter")
    menu = source("apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java")
    if "new PopupMenu(" in menu:
        raise AssertionError("YFloat must not instantiate its own native popup")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java",
            "YViewTransientControls.popupMenu(")
    require("libs/yui/src/main/java/com/yagay/yui/YViewTransientControls.kt",
            "fun datePicker(", "fun timePicker(", "fun popupMenu(")

    for page in ("suite/YSuite/src/main/java/com/yagay/YSuite/CompactSuiteHome.kt",
                 "next/core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteCompactDashboard.kt"):
        require(page, "YHomeModuleRow(", "YHomePresentation.readPinned(",
                "YHomePresentation.savePinned(", "YHomePresentation.orderedIds(")
        if "detectHorizontalDragGestures(" in source(page):
            raise AssertionError(f"{page} introduced a second swipe row renderer")
    require("libs/yui/src/main/java/com/yagay/yui/YHomePresentation.kt",
            'const val PREFERENCES = "ysuite_compact_home"', "fun YHomeModuleRow(",
            "fun readPinned(", "fun savePinned(")
    # The integrated host must never present competing controls for the
    # same YUI-owned YFloat border values.
    circle = require(
        "apps/YFloat/feature/src/main/java/com/yagay/YFloat/CircleBorderSettingsUi.java",
        'if ("com.yagay.YSuite".equals(activity.getPackageName())) return;',
        "YViewLayout.switchRow(", "YViewLayout.spinnerSetting(",
        "YViewLayout.sliderSetting(",
    )
    if circle.index('if ("com.yagay.YSuite".equals(activity.getPackageName())) return;') > circle.index("YViewLayout.switchRow("):
        raise AssertionError("Integrated YFloat still displays duplicate circle appearance controls")
    notify = source("apps/YNotify/feature/src/main/res/layout/item_event.xml")
    if 'app:strokeWidth="1dp"' in notify:
        raise AssertionError("YNotify XML hardcodes list-card outline; use the shared YUI renderer")
    print("yui-independent-surfaces: PASS Google Hook snapshot, picker/menu, home row/pins, notify XML")


if __name__ == "__main__":
    try:
        check()
    except (OSError, AssertionError) as error:
        raise SystemExit(f"yui-independent-surfaces: FAILED: {error}")
