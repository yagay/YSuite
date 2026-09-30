#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    print(f"feature-state-gating: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(path: str, *needles: str) -> str:
    file = ROOT / path
    if not file.is_file():
        fail(f"missing file: {path}")
    text = file.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            fail(f"{path} missing required gate: {needle}")
    return text


def require_order(text: str, path: str, before: str, after: str) -> None:
    left = text.find(before)
    right = text.find(after)
    if left < 0 or right < 0 or left >= right:
        fail(f"{path} must order {before!r} before {after!r}")


def main() -> None:
    contract = require(
        "libs/ycore/src/main/java/com/yagay/suite/core/SuiteContract.kt",
        'FEATURE_STATE_REMOTE_GROUP = "ysuite_feature_state"',
        'FEATURE_STATE_KEY_PREFIX = "enabled."',
    )
    revision = re.search(r"const val REVISION\s*=\s*(\d+)", contract)
    if not revision or int(revision.group(1)) < 2:
        fail("SuiteContract revision must be >= 2 for remote feature-state gating")

    manager_path = "libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt"
    manager = require(
        manager_path,
        "SuiteXposedServiceBroker.setPluginEnabled(feature.id, true)",
        "SuiteXposedServiceBroker.setPluginEnabled(feature.id, false)",
        "SuiteRootGateway.stopPluginProcesses(feature.id)",
        "FeatureRuntimeManager.enable(appContext, feature)",
        "FeatureRuntimeManager.disable(appContext, feature)",
    )
    # Enable: runtime must be ready before the broker replays an existing LSPosed connection.
    require_order(
        manager,
        manager_path,
        "FeatureRuntimeManager.enable(appContext, feature)",
        "SuiteXposedServiceBroker.setPluginEnabled(feature.id, true)",
    )
    # Disable: cut off framework callbacks/root work before feature-specific cleanup runs.
    require_order(
        manager,
        manager_path,
        "SuiteXposedServiceBroker.setPluginEnabled(feature.id, false)",
        "FeatureRuntimeManager.disable(appContext, feature)",
    )
    require_order(
        manager,
        manager_path,
        "SuiteRootGateway.stopPluginProcesses(feature.id)",
        "FeatureRuntimeManager.disable(appContext, feature)",
    )

    require(
        "libs/ycore/src/main/java/com/yagay/suite/core/SuiteXposedServiceBroker.kt",
        "fun setPluginEnabled(pluginId: String, enabled: Boolean)",
        "syncAllFeatureStates(service)",
        "FEATURE_STATE_REMOTE_GROUP",
        "disabledPlugins",
    )

    require(
        "suite/YSuite/src/main/java/com/yagay/YSuite/xposed/SuitePluginXposedInterface.java",
        "SuiteContract.FEATURE_STATE_REMOTE_GROUP",
        "FeatureGateHookBuilder",
        "if (!isFeatureEnabled()) return chain.proceed();",
    )

    require(
        "libs/ycore/src/main/java/com/yagay/suite/core/SuiteRootGateway.kt",
        "disabledFeatureError",
        "FeatureStateStore(context).isEnabled(feature)",
        "fun stopPluginProcesses(pluginId: String)",
        "streamingProcesses",
    )

    print("feature-state-gating: OK ordered runtime + LSPosed + Hook + Root gates share one feature switch")


if __name__ == "__main__":
    main()
