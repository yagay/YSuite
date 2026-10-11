#!/usr/bin/env python3
"""Fail CI if YUI cards/rows or the Debug-only branch build contract diverge."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
issues = []

def src(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        issues.append(f"Missing {path}")
        return ""
    return p.read_text(encoding="utf-8")

view = src("libs/yui/src/main/java/com/yagay/yui/YViewFramework.kt")
nav = view.split("fun navigationRow(", 1)[-1].split("fun navigationRow(", 1)[0]
if not re.search(r"val row = baseRow\(context\)", nav):
    issues.append("YFloat navigationRow must reuse the permission-row baseRow inset")
if not re.search(r"val horizontal = YView\.cardPadding\(context\)", view.split("fun baseRow(context: Context):", 1)[-1]):
    issues.append("Shared baseRow must be padded by central YUI cardPadding")
title = view.split("fun listTitle(context: Context)", 1)[-1].split("fun rowTitle(", 1)[0]
if "maxLines = 1" in title or "isSingleLine = false" not in title:
    issues.append("Native listTitle must allow multi-line accessibility text")

menu = src("apps/YFloat/feature/src/main/java/com/yagay/YFloat/MenuPickerActivity.java")
sortable = menu.split("private View sortableRow(", 1)[-1].split("private View actionRow(", 1)[0]
if re.search(r"\brow\.setPadding\(", sortable):
    issues.append("YFloat sortableRow overrides shared baseRow padding")
for file in ("MenuPickerActivity.java", "MenuLabelEditorActivity.java"):
    text = src("apps/YFloat/feature/src/main/java/com/yagay/YFloat/" + file)
    if re.search(r"\b(?:titleView|sub|name|a|b)\.setSingleLine\(true\)", text):
        issues.append(file + " forces setting-row labels to one line")

settings = src("next/core/productui/src/main/java/com/yagay/ysuite/productui/settings/ComposeSettingsSurface.kt")
group = settings.split("fun ComposeSettingsGroup(", 1)[-1].split("fun ComposeSettingsSwitch(", 1)[0]
if "LocalYAppearance.current.rowHorizontalPaddingDp.dp" not in group:
    issues.append("ComposeSettingsGroup title must use the same configurable YUI inset as YListItem")
if "horizontal = YSuiteSpacing.Medium" in group:
    issues.append("ComposeSettingsGroup hardcodes title inset")

# Keep old production package and the one-arm64 ABI policy intact.
app = src("suite/YSuite/build.gradle.kts")
if 'applicationId = "com.yagay.YSuite"' not in app:
    issues.append("YSuite package identity changed")
if 'listOf("arm64-v8a")' not in app:
    issues.append("YSuite default ABI is no longer ARM64")

workflow = src(".github/workflows/build-debug.yml")
if workflow.count(":suite:assembleDebug") != 1:
    issues.append("Expected one YSuite Debug APK assembly in the active workflow")
if "YSuite-debug-arm64-v8a.apk" not in workflow:
    issues.append("ARM64 Debug artifact is not uniquely named")
for extra in ("build.yml", "standalone.yml", "yui-screenshot.yml"):
    text = src(".github/workflows/" + extra)
    if "branches: [main, rebuild/material3-full-ui]" in text:
        issues.append(extra + " still auto-runs non-Debug compilation on the UI branch")
    if "github.ref != 'refs/heads/rebuild/material3-full-ui'" not in text:
        issues.append(extra + " not guarded against manual UI-branch builds")

if issues:
    print("YUI ROW / ARM64 BUILD POLICY FAIL:")
    for issue in issues:
        print(" - " + issue)
    sys.exit(1)
print("YUI ROW / ARM64 BUILD POLICY PASS: YFloat and Compose row alignment; one arm64 Debug artifact")
