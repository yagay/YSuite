# Feature migration: YMiniGuard

Product surface: EntityManager
Upstream project: LibChecker/LibChecker
Research reference: LuckyTool
Upstream license: Apache-2.0 for the primary layout reference

## Scope

Preserves the system_server API-102 guard engine, OPlus/ColorOS FlexibleWindow observation,
per-app always-foreground protection, background-playback protection, force-support policy,
lock-screen keepalive, edge-handle state, task-removal/kill guards, diagnostics and engine reload.

## Product UI

Uses YMiniGuardWorkspace as an app/entity manager. Each app owns its keepalive modes while global
system-server policy and runtime status remain visible separately. No fake floating-window UI is
introduced.

## Platform capabilities

Configuration and LSPosed scope are routed through HookGateway. Diagnostics use RootGateway. The
system_server Hook lives only in feature/yminiguard/runtime.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

The integrated and standalone builds compile the same runtime. Engine heartbeat includes hook count,
PID, generation and active-session count so stale engines are distinguishable from UI writes.

## Standalone

The generic standalone host injects Root/Hook services; the same provider and Xposed runtime are
merged into the standalone APK.
