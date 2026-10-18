#!/usr/bin/env python3
"""Guard against standalone APK paths silently leaking into YSuite runtime IPC.

This is a source/manifest build contract, not an Android device test. It intentionally fails
when a feature regresses to remote-preference-based host selection or lacks retry/freshness.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    source = ROOT / relative
    if not source.is_file():
        raise AssertionError(f"missing runtime contract source: {relative}")
    return source.read_text(encoding="utf-8")


def require(relative: str, *fragments: str) -> str:
    content = read(relative)
    for fragment in fragments:
        if fragment not in content:
            raise AssertionError(f"{relative}: missing contract {fragment!r}")
    return content


def check_host_identity() -> None:
    require("libs/yapi/src/main/java/com/yagay/suite/api/HostIdentity.java",
            "compiledPackage(", "validPackage(")
    checks = (
        ("apps/YMiniGuard/feature/build.gradle.kts",
         "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardConfig.java"),
        ("apps/YNotify/feature/build.gradle.kts",
         "apps/YNotify/feature/src/main/java/com/yagay/YNotify/xposed/YNotifyModule.java"),
    )
    for build, implementation in checks:
        require(build, "embeddedInSuite", "HOST_PACKAGE", "com.yagay.YSuite")
        source = require(implementation, "HostIdentity.compiledPackage(BuildConfig.HOST_PACKAGE")
        if re.search(r"return\s+STANDALONE_PACKAGE\s*;", source):
            raise AssertionError(f"{implementation}: silent standalone IPC fallback")

    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/EngineBridge.java",
            "GuardConfig.hostPackage()", "updateContext(", "installed APK path")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatHostIdentity.java",
            "XposedHostBridge.attachListener(", "LsposedRuntimeConfig.K_HOST_PACKAGE")
    require("apps/YFloat/feature/build.gradle", "embeddedInSuite", "HOST_PACKAGE")
    require("apps/YNotify/feature/src/main/java/com/yagay/YNotify/xposed/YNotifyModule.java",
            "YSUITE_BRIDGE_RECEIVER", "hostPackage()")

    manifest = require("suite/YSuite/src/main/AndroidManifest.xml",
                       "SuiteMiniGuardStatusProvider",
                       "SuiteNfcConfigProvider", "SuiteBridgeReceiver",
                       "${applicationId}.engine_status",
                       "${applicationId}.ynfc.config")
    if "android:exported=\"true\"" not in manifest:
        raise AssertionError("YSuite IPC providers must remain registered")


def check_lifecycle_and_health() -> None:
    require("libs/yapi/src/main/java/com/yagay/suite/api/RuntimeHealth.java",
            "isRecentWallClock(", "isRecentFromCurrentBoot(", "SystemClock.elapsedRealtime()")
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/EngineStatusProvider.java",
            "RuntimeHealth.isRecentFromCurrentBoot(startedAt, 15_000L)")
    require("apps/YNotify/feature/src/main/java/com/yagay/YNotify/YNotifyRuntime.java",
            "RuntimeHealth.isRecentWallClock(", "ynotify_hook_no_recent_events")
    require("apps/YNotify/feature/src/main/res/values/strings.xml",
            'name="ynotify_hook_no_recent_events"')
    require("apps/YNotify/feature/src/main/res/values-zh-rCN/strings.xml",
            'name="ynotify_hook_no_recent_events"')

    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteXposedServiceBroker.kt",
            "private var registered = false", "if (registered) return",
            "XposedServiceHelper.registerListener(this)", "registered = true")
    require("suite/YSuite/src/main/java/com/yagay/YSuite/YSuiteApp.kt",
            "ensureXposedBrokerRegistered()", "scheduleHostStartupRetry()",
            "MAX_RECOVERY_ATTEMPTS", "brokerRegistered = true",
            "retryHostRegistrationOnForeground()")
    require("suite/YSuite/src/main/java/com/yagay/YSuite/MainActivity.kt",
            "retryHostRegistrationOnForeground()")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/LsposedStatusManager.java",
            "LISTENER_ATTACHED", "YSuite broker attach failed; retry on enable")
    require("apps/YFloat/feature/src/main/java/com/yagay/YFloat/YFloatSuiteRuntime.java",
            "LsposedStatusManager.initialize(app);")


def check_root_recovery() -> None:
    require("libs/ycore/src/main/java/com/yagay/suite/core/RootManager.kt",
            "for (attempt in 0 until 2)", "shell.close()", "shell.isRoot",
            "ROOT_VERIFIED_TTL_MS", "ROOT_RETRY_TTL_MS", "lastCheckedAtMs", "openIsolatedShell")
    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteRootGateway.kt",
            "if (!RootManager.isAvailable(app))", "Root shell could not open as uid 0", "scanSlots.tryAcquire()", "RootManager.openIsolatedShell(app)",
            ".to(isolatedOut, isolatedErr)", "if (isolated) isolatedOut else shellResult.out")
    # All ordinary and binary requests must go through the same hosted libsu
    # shell. No second su process is allowed in the binary execution path.
    gateway = require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteRootGateway.kt",
                      "private fun executeBinary(", "Shell.cmd(capture).enqueue()",
                      "readAtMost(stdout, stdoutLimit)")
    binary_body = gateway.split("private fun executeBinary(", 1)[1].split(
        "private fun quotePath(", 1)[0]
    if 'ProcessBuilder("su"' in binary_body.splitlines()[1:]:
        raise AssertionError("binary Root execution must not spawn a second su process")

    require("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/root/RootShell.kt",
            "services.hostOrNull()", "fun probeRoot(): RootProbe",
            "HostRootProbe.probe(services, 8L)", "fun probeRoot(): RootProbe",
            "Managed Root execution failed:")
    require("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/MainViewModel.kt",
            "processShell.probeRoot()", "message = probe.detail")
    require("apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/ui/TaskManagerScreen.kt",
            "text = state.root.message")
    require("suite/YSuite/src/main/java/com/yagay/YSuite/YSuiteApp.kt",
            "!FeatureRuntimeManager.isRuntimeEnabled(feature.id)",
            "managed feature runtime recovered on foreground")


def check_live_handoff() -> None:
    require("apps/YNFC/feature/build.gradle.kts",
            "CONFIG_AUTHORITY", "com.yagay.YSuite.ynfc.config")
    require("apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/HookConfigStore.java",
            "readValues(context, uri)", "readValues(context, fallback)",
            "RuntimeHandoffGate.isSuiteActiveHere",
            "public static Uri activeUri()", "public static Uri standaloneUri()",
            "public static Uri suiteUri()", "selectedUri = uri",
            "return values.isEmpty() ? null : values",
            "static boolean isPassive()", "return SimConfig.uninitialized()")
    # One command provider, one status provider, one cached-profile provider, and two observed
    # authorities during ownership handoff. Do not allow split-brain or synthetic generation-0 STOP.
    state_writer = require(
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/HookStateWriter.java",
        "HookConfigStore.activeUri()", "final Uri destination",
        "HookConfigStore.isPassive()", "!destination.equals(HookConfigStore.activeUri())")
    profile_store = require(
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/profile/HookProfileStore.java",
        "HookConfigStore.activeUri()")
    injection = require(
        "apps/YNFC/feature/src/main/java/com/yagay/YNFC/xposed/NfcInjectionModule.java",
        "HookConfigStore.activeUri()", "HookConfigStore.standaloneUri()",
        "HookConfigStore.suiteUri()", "if (HookConfigStore.isPassive()) return chain.proceed();",
        "registerContentObserver(")
    for name, body in (("writer", state_writer), ("profile", profile_store), ("module", injection)):
        if 'BuildConfig.CONFIG_AUTHORITY + "/settings"' in body:
            raise AssertionError(f"NFC Hook {name} reintroduced a fixed provider authority")
    require("suite/YSuite/src/main/java/com/yagay/YSuite/ipc/SuiteRuntimeHandoffProvider.kt",
            "processStartToken", "Binder.getCallingPid()", "RuntimeHandoffGate.RESULT_ACTIVE")
    require("libs/yapi/src/main/java/com/yagay/suite/api/RuntimeHandoffGate.java",
            "isSuiteActiveHere", "shouldStandaloneFallback")


def check_shared_capability_evidence() -> None:
    require("libs/yapi/src/main/java/com/yagay/suite/api/HostRootProbe.kt",
            "enum class Status", "UNPRIVILEGED", "HOST_MISSING", "TRANSIENT_FAILURE",
            "Root verified (uid=0)", "READY_TTL_MS", "RETRY_TTL_MS",
            "HostCapabilityState.GRANTED", "HostCapabilityState.UNKNOWN")
    for module, filename in (
        ("TaskManager", "apps/YTaskManager/feature/src/main/java/com/yagay/YTaskManager/root/RootShell.kt"),
        ("YDiag", "apps/YDiag/feature/src/main/java/com/yagay/ydiag/root/RootShell.kt"),
        ("YPower", "apps/YPower/feature/src/main/java/com/yagay/ypower/root/RootShell.java"),
        ("YMiniGuard", "apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/RootManager.java"),
        ("YEntryCleaner", "apps/YEntryCleaner/feature/src/main/java/com/yagay/YEntryCleaner/data/ComponentRootCommand.java"),
        ("YNFC", "apps/YNFC/feature/src/main/java/com/yagay/YNFC/system/RootShell.kt"),
    ):
        require(filename, "HostRootProbe.probe(")
    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteCapabilityMonitor.kt",
            "enum class State", "fun target(", "fun root(", "fun framework(",
            "TRANSIENT_GRACE_MS")
    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteRootGateway.kt",
            'requireRootUid(command)', "RootManager.isAvailable(app)")
    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteXposedServiceBroker.kt",
            "targetEvidence(", "State.TARGET_LOADED", "State.MISSING_SCOPE")
    require("libs/yapi/src/main/java/com/yagay/suite/api/FeatureHookEvidence.kt",
            "State.STALE", "fun publish(", "fun invalidate(")
    require("libs/ycore/src/main/java/com/yagay/suite/core/FeatureManager.kt",
            "FeatureHookEvidence.invalidate(feature.id)", "HostRootProbe.invalidate(feature.id)")
    require("apps/YNFC/feature/src/main/java/com/yagay/YNFC/RuntimeStatusViewModel.kt",
            'featureId = "ynfc"', 'status.hookPid == status.currentPid')
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardRuntime.java",
            '"yminiguard", "system"', "status.isFromCurrentBoot()",
            "getSystemTargetLoadedVersionCode()", "getRunningTargets()",
            "if (!isHotReloadAvailable()) return false;")
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/MainActivity.java",
            "ymg_restart_old_bootstrap", "ymg_restart_no_heartbeat",
            "ymg_reboot_title")
    require("apps/YMiniGuard/feature/src/main/java/com/yagay/YMiniGuard/GuardModule.java",
            "scheduleEngineHeartbeat(handler);", "heartbeat deferred: system context unavailable")
    require("libs/ycore/src/main/java/com/yagay/suite/core/SuiteDiagnostics.kt",
            "capability-evidence.txt", "FeatureHookEvidence.diagnosticSnapshot()")


def main() -> None:
    check_host_identity()
    check_shared_capability_evidence()
    check_lifecycle_and_health()
    check_root_recovery()
    check_live_handoff()
    print("runtime IPC contract: OK host identity, broker recovery, health, Root recovery, NFC handoff")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as error:
        print(f"runtime IPC contract: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
