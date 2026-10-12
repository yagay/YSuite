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

    # MiniGuard heartbeat must survive early system context lookups and report
    # actual persistence failures; its status UI must update while visible.
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/EngineBridge.java",
            "Context availableContext() { return context; }")
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardModule.java",
            "current.availableContext()", 'acknowledgement.getBoolean("ok", false)')
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/EngineStatusProvider.java",
            'result.putBoolean("ok", saved);')
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java",
            "engineStatus.postDelayed(statusTicker, 2_000L)", "engineStatus.removeCallbacks(statusTicker)",
            "ymg_engine_load_failed", "ymg_engine_stale_heartbeat")

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
    # All YFloat appearance controls must remain discoverable inside the
    # module as well as in the YSuite appearance catalog. Both editors write
    # the same YAppearanceStore module keys, NOT two preference databases.
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsIconPage.java",
            "ui.styleSpinner(appearance.body)", "FloatSettings.K_ALPHA",
            "FloatSettings.K_SIZE", "FloatSettings.K_SHOW_PERCENT")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsGesturePage.java",
            "FloatSettings.K_LINE_ALPHA", "FloatSettings.K_LINE_WIDTH",
            "FloatSettings.K_LINE_GRADIENT", "ui.lineStyleSpinner(")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsCapturePage.java",
            "CircleBorderSettingsUi.add(activity, fs, circleBorder.body)")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/CircleBorderSettingsUi.java",
            "FloatSettings.K_CIRCLE_BORDER_COLOR", "FloatSettings.K_CIRCLE_BORDER_WIDTH_DP",
            "YViewLayout.switchRow(", "YViewLayout.spinnerSetting(", "YViewLayout.sliderSetting(")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsAdvancedSelectionPage.java",
            "FloatSettings.K_GOOGLE_FRAME_RADIUS_DP",
            "FloatSettings.K_GOOGLE_FRAME_GLOW_WIDTH_DP",
            "settings.resetAdvancedSelectionSettings(true)")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/AppearanceSettingsActivity.java",
            "addThemeSpinner(theme.body)", "addMainItemCountSlider(textMenu.body)")
    if 'if (!embeddedInSuite)' in source("apps/YFloat/feature/src/main/java/com/yagay/YFloat/SettingsIconPage.java"):
        raise AssertionError("YSuite installation must show YFloat icon sliders")
    if 'if ("com.yagay.YSuite".equals(activity.getPackageName())) return;' in source(
        "apps/YFloat/feature/src/main/java/com/yagay/YFloat/CircleBorderSettingsUi.java"
    ):
        raise AssertionError("YSuite installation must show YFloat border controls")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatVisualSettings.java",
            "new YAppearanceStore(context).set(key, raw, MODULE)",
            "new YAppearanceStore(context).set(key, Boolean.toString(value), MODULE)")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/FloatSettings.java",
            "resetAdvancedSelectionSettings(boolean resetVisuals)", "if (resetVisuals) {")
    chart = require(
        "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt",
        "val guideWidth = LocalYAppearance.current.cardOutlineWidthDp.dp",
        "Stroke(width = guideWidth.toPx())",
    )
    if "Stroke(width = 1f)" in chart:
        raise AssertionError("YTaskManager chart outline bypasses YUI appearance")
    geometry = require("libs/yui/src/main/java/com/yagay/yui/YUiGeometry.kt",
        "val minimumMainPane:", "val minimumLeadingPane:", "val minimumTrailingPane:")
    adaptive = require("next/core/productui/src/main/java/com/yagay/ysuite/productui/ProductAdaptive.kt",
        "YUiGeometry.minimumMainPane", "YUiGeometry.minimumLeadingPane",
        "YUiGeometry.minimumTrailingPane")
    if any(value in adaptive for value in ("320.dp", "160.dp", "200.dp")):
        raise AssertionError("Product pane thresholds must be owned by YUI")
    # Accessibility must capture only positively identified transient UI.
    # NotificationListenerService owns notifications; SystemUI clock and
    # other status text must not be persisted as generic events.
    a11y = source("apps/YNotify/feature/src/main/java/com/yagay/YNotify/collector/UiAccessibilityBridge.java")
    if 'return EventTypes.SYSTEM_UI;' in a11y or 'return EventTypes.OTHER_UI;' in a11y:
        raise AssertionError("YNotify still records generic status/announcement changes")
    for marker in ("return EventTypes.TOAST;", "return EventTypes.SNACKBAR;",
                   "return EventTypes.POPUP;", "return EventTypes.DIALOG;"):
        if marker not in a11y:
            raise AssertionError("YNotify lost explicit UI capture: " + marker)
    hook_receiver = source("apps/YNotify/feature/src/main/java/com/yagay/YNotify/collector/XposedEventReceiver.java")
    permitted = hook_receiver.split("private static boolean allowedType(String type)", 1)[1].split("private static boolean validPackage", 1)[0]
    if "EventTypes.SYSTEM_UI.equals(type)" in permitted or "EventTypes.OTHER_UI.equals(type)" in permitted:
        raise AssertionError("YNotify Hook allows unclassified UI events")
    # Card bodies must have the same configurable inset as card headings.
    # YNotify settings add status/caption views directly to section.body.
    framework = source("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt")
    section_body = framework.split("val body = LinearLayout(context).apply {", 1)[1].split("holder.addView(body", 1)[0]
    if "setPadding(horizontal, 0, horizontal," not in section_body:
        raise AssertionError("YUI section body text can touch the card border")
    # The legacy card facade must not double-pad the section body.
    card_facade = framework.split("fun card(parent: LinearLayout", 1)[1].split("return content", 1)[0]
    if "setPadding(horizontal, 0, horizontal, 0)" in card_facade:
        raise AssertionError("YUI card facade adds a duplicate horizontal inset")
    notify = source("apps/YNotify/feature/src/main/res/layout/item_event.xml")
    if 'android:maxLines="2"' in notify or 'android:ellipsize="end"' in notify:
        raise AssertionError("YNotify event title must wrap, not override YUI text sizing")
    require("apps/YNotify/feature/src/main/java/com/yagay/YNotify/ui/EventAdapter.java",
            "h.b.appName.setSingleLine(false)", "h.b.appName.setMaxLines(Integer.MAX_VALUE)")
    if 'app:strokeWidth="1dp"' in notify:
        raise AssertionError("YNotify XML hardcodes list-card outline; use the shared YUI renderer")
    print("yui-independent-surfaces: PASS Google Hook snapshot, picker/menu, home row/pins, notify XML")


if __name__ == "__main__":
    try:
        check()
    except (OSError, AssertionError) as error:
        raise SystemExit(f"yui-independent-surfaces: FAILED: {error}")
