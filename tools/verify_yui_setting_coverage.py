#!/usr/bin/env python3
"""Reject half-connected appearance options and visual overrides of YUI components.

These are source-contract checks; runtime screenshots and touch-size acceptance
still require actual Android device verification.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def source(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

def require(path: str, *tokens: str) -> str:
    body = source(path)
    for token in tokens:
        if token not in body:
            raise AssertionError(f"{path}: missing unified appearance contract: {token}")
    return body

def run() -> None:
    store = require("libs/yui/src/main/java/com/yagay/yui/YAppearanceStore.kt",
                    'INPUT_HEIGHT("input_height"', 'val inputHeightDp: Int = 56',
                    'inputHeightDp = value(YSettingKey.INPUT_HEIGHT, moduleId).toInt()')
    keys = set(re.findall(r'^\s+([A-Z][A-Z_]+)\("[a-z_]+"', store, flags=re.M))
    registry = source("libs/yui/src/main/java/com/yagay/yui/YUiControlRegistry.kt")
    settings = source("suite/YSuite/src/main/java/com/yagay/YSuite/SuiteSettingsScreen.kt")
    missing_catalog = sorted(k for k in keys - {"HOME_SWIPE_PIN", "HOME_STATUS"}
                             if f"YSettingKey.{k}" not in registry)
    missing_label = sorted(k for k in keys if f"YSettingKey.{k} ->" not in settings)
    missing_reader = sorted(k for k in keys if f"value(YSettingKey.{k}, moduleId)" not in store)
    if missing_catalog or missing_label or missing_reader:
        raise AssertionError(f"Appearance settings drift: catalog={missing_catalog}, "
                             f"title={missing_label}, reader={missing_reader}")
    for path in ("suite/YSuite/src/main/res/values/strings_appearance_catalog.xml",
                 "suite/YSuite/src/main/res/values-zh-rCN/strings_appearance_catalog.xml"):
        require(path, 'name="appearance_input_height"')
    require("libs/yui/src/main/java/com/yagay/yui/YView.kt",
            "styleLabel(button)", "inputHeight(context: Context)",
            "buttonHorizontalPadding(context)")
    yui = require("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt",
                  "fun listActionButton(context: Context, label: String): MaterialButton =",
                  "secondaryButton(context, label)", "YView.inputHeight(context)",
                  "background = rounded(context, if (positive)")
    if re.search(r'fun listActionButton\([^\n]+\)\s*:\s*MaterialButton\s*=\s*'
                 r'secondaryButton\([^\n]+\)\.apply', yui):
        raise AssertionError("List button reintroduced local appearance overrides")
    require("libs/yui/src/main/java/com/yagay/yui/YMaterialInputs.kt",
            "modifier.heightIn(min = LocalYAppearance.current.inputHeightDp.dp)")
    require("libs/yui/src/main/java/com/yagay/yui/YMaterialChips.kt",
            "modifier.heightIn(min = LocalYAppearance.current.buttonHeightDp.dp)")
    # A switch-width slot is not switch visual size. Check every setting through
    # persisted appearance, control catalog, both renderers and preview editor.
    switch_keys = (
        "SWITCH_SCALE_PERCENT", "SWITCH_TRACK_WIDTH", "SWITCH_TRACK_HEIGHT",
        "SWITCH_THUMB_SIZE", "SWITCH_LABEL_GAP",
    )
    for key in switch_keys:
        if f"YSettingKey.{key}" not in registry or f"YSettingKey.{key} ->" not in settings or (
                f"value(YSettingKey.{key}, moduleId)" not in store):
            raise AssertionError(f"Switch appearance is not end-to-end YUI owned: {key}")
    require("libs/yui/src/main/java/com/yagay/yui/YSwitchGeometry.kt",
            "fun YAppearance.switchGeometry()", "touchWidthDp = max(", "touchHeightDp = max(")
    require("libs/yui/src/main/java/com/yagay/yui/YCompactSwitch.kt",
            "appearance.switchGeometry()", "Modifier.scale(geometry.scale)",
            "role = Role.Switch", "geometry.trackWidthDp.dp", "geometry.thumbDiameterDp.dp")
    require("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt",
            "appearance.switchGeometry()", "toggle.scaleX = geometry.scale",
            "toggle.trackDrawable = GradientDrawable()", "toggle.thumbDrawable = GradientDrawable()",
            ".switchLabelGapDp")
    for path in ("suite/YSuite/src/main/res/values/strings_appearance_catalog.xml",
                 "suite/YSuite/src/main/res/values-zh-rCN/strings_appearance_catalog.xml"):
        for name in ("switch_scale_percent", "switch_track_width", "switch_track_height",
                     "switch_thumb_size", "switch_label_gap"):
            require(path, 'name="appearance_' + name + '"')
    require("libs/yui/src/main/java/com/yagay/yui/YMaterialSurfaces.kt",
            "border = border ?: LocalYAppearance.current.cardOutlineWidthDp")
    require("libs/yui/src/main/java/com/yagay/yui/YForms.kt",
            "YUiModalBottomSheet as ModalBottomSheet",
            "LocalYAppearance.current.fieldRadiusDp.dp")
    power = require("apps/YPower/feature/src/main/java/com/yagay/ypower/ui/MainActivity.java",
                    "YViewLayout.secondaryButton(MainActivity.this,",
                    "ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT")
    if "YViewLayout.listActionButton(MainActivity.this" in power:
        raise AssertionError("YPower bypasses common standard button appearance")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java",
            "YViewLayout.secondaryButton(this, sideText)")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/ResultUi.java",
            'YViewLayout.secondaryButton(YOverlayUi.scopedContext(c, "yfloat"), text)')
    print(f"YUI appearance coverage: OK settings={len(keys)}, all exposed style keys "
          "registered, View/Compose input, buttons, chips, cards, overlays linked")

if __name__ == "__main__":
    try:
        run()
    except (AssertionError, FileNotFoundError) as exc:
        print(f"YUI appearance coverage FAILED: {exc}", file=sys.stderr)
        raise SystemExit(1)
