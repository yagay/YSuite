#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/ui"


def read(name: str) -> str:
    path = BASE / name
    if not path.is_file():
        raise SystemExit(f"yentry-ui: missing {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(source: str, marker: str, label: str) -> None:
    if marker not in source:
        print(f"yentry-ui: ERROR: {label} must contain {marker!r}", file=sys.stderr)
        raise SystemExit(1)


def reject(source: str, marker: str, label: str) -> None:
    if marker in source:
        print(f"yentry-ui: ERROR: {label} must not contain {marker!r}", file=sys.stderr)
        raise SystemExit(1)


def main() -> None:
    activity = read("MainActivity.kt")
    dashboard = read("DashboardSurface.kt")
    runtime = read("RuntimePanel.kt")
    browser = read("BrowserHostFilterRow.kt")
    custom_open = read("CustomOpenTypeDialog.kt")
    scope_picker = read("AppScopePicker.kt")
    controls = read("MainControls.kt")
    tabs = read("MainTabs.kt")

    require(activity, "UnifiedDashboardTabContent(", "dashboard route")
    for marker in ("YFeatureCard(", "YStatusRow(", "YSettingSwitch(", "YActionRow"):
        require(dashboard, marker, "unified dashboard")
    require(dashboard, "DashboardModuleStatusCard", "dashboard module status")

    for marker in ("YFeatureCard(", "YStatusRow(", "YStatusTone"):
        require(runtime, marker, "runtime panel")

    for marker in ("YSearchField(", "YFeatureEmpty(", "YSettingRow("):
        require(browser, marker, "browser host UI")
    require(browser, "normalizeBrowserHost", "browser host validation")
    require(browser, "BrowserLinkConfig.MAX_HOSTS", "browser host limit")

    for marker in ("YFeatureCard(", "YStatusRow(", "CustomOpenDefinition(", "validated()"):
        require(custom_open, marker, "custom OPEN editor")

    for marker in ("YFeatureCard(", "YSearchField(", "YSettingSwitch(", "YStatusRow(", "YFeatureEmpty("):
        require(scope_picker, marker, "app scope picker")
    require(scope_picker, "VisibilityScope.entries", "app scope category controls")
    require(scope_picker, "onSelectedChange", "app scope package selection")

    # MainActivity has routed to DashboardSurface since the unified dashboard landed. Keeping the
    # previous dashboard implementation in MainTabs would leave two visual contracts to maintain.
    reject(tabs, "fun DashboardTabContent(", "legacy dashboard implementation")

    # Rules/Priority intentionally keep the compact module indicator because it sits above dense,
    # scrollable lists. The expanded dashboard owns the full card treatment.
    require(controls, "internal fun ModuleStatusRow", "compact list module indicator")
    require(controls, "compact: Boolean", "compact list module indicator")

    print(
        "yentry-ui: OK dashboard/runtime/scope/filter surfaces use unified YUI; "
        "legacy dashboard removed and compact list interactions preserved"
    )


if __name__ == "__main__":
    main()
