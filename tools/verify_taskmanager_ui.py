#!/usr/bin/env python3
"""Protect the common YUI TaskManager layout and every existing control callback.

A source contract, not a device screenshot test.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
screen = ROOT / "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt"
en = ROOT / "apps/YTaskManager/feature/src/main/res/values/strings.xml"
zh = ROOT / "apps/YTaskManager/feature/src/main/res/values-zh-rCN/strings.xml"

def run() -> None:
    content = screen.read_text(encoding="utf-8")
    required = (
        "YManagerScaffold(", "YAppShell(", "YSearchField(",
        "ProcessControls(", "ProcessFilterSheet(", "SettingsDialog(",
        "YBottomSheet(", "YRadioGroup(", "YChoiceSpec(", "YSwitchItem(",
        "YSectionHeader(", "YStatusStrip(", "YPageList(", "YListItem(",
        "YMessageHost(", "YConfirmDialog(", "YFormDialog(",
        "onSort = viewModel::setSort", "onUser = viewModel::setShowUserApps",
        "onSystem = viewModel::setShowSystemApps", "onLinux = viewModel::setShowLinuxProcesses",
        "onQuery = viewModel::setQuery", "onAutoRefresh = viewModel::setAutoRefresh",
        "onRefreshInterval = viewModel::setRefreshInterval",
        "onConfirmKill = viewModel::setConfirmKill",
        "viewModel.killProcess(process)", "viewModel.forceStop(process)",
        "viewModel.loadProcessDetails(", "viewModel.togglePin(process)",
        "ProcessDialog(", "ResourcePage(state)", "NetworkPage(state)",
        "LaunchedEffect(drawable, packageName, px)", "formatSpeed(",
    )
    for fragment in required:
        if fragment not in content:
            raise AssertionError(f"TaskManager UI missing {fragment}")
    if "YToggleFilterBar(" in content or "YFilterBar(" in content:
        raise AssertionError("TaskManager UI regressed to cramped horizontal setting chips")
    for file in (en, zh):
        xml = file.read_text(encoding="utf-8")
        for name in ("ytm_filter_and_sort", "ytm_sort_by", "ytm_sort_current",
                     "ytm_auto_refresh_desc", "ytm_confirm_kill_desc",
                     "ytm_process_identity", "ytm_process_performance",
                     "ytm_process_advanced", "ytm_long_press_copy"):
            if f'name="{name}"' not in xml:
                raise AssertionError(f"{file}: missing translation {name}")
    print("TaskManager unified YUI layout and callbacks: PASS")

if __name__ == "__main__":
    try:
        run()
    except (AssertionError, FileNotFoundError) as error:
        print("TaskManager unified UI FAILED:", error, file=sys.stderr)
        raise SystemExit(1)
