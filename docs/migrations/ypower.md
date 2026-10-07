# Feature migration: YPower

Product surface: EntityManager
Upstream project: LibChecker/LibChecker
Upstream license: Apache-2.0

## Scope

Reimplements YPower's per-app enhancement profile, Root-side Doze/AppOps/standby/network policy
changes, optional dangerous-permission grants, Hook compatibility/trace switches, recommended app
presets and runtime diagnostics.

## Product UI

Uses `YPowerWorkspace`: app/entity collection, filters, runtime summary and focused per-app detail.
The LibChecker-style hierarchy is layout-only; YPower behaviour remains YSuite-owned.

## Platform capabilities

Root operations use only `RootGateway`. Hook scope/reload and runtime status use only
`HookGateway`. The feature implementation contains no direct `su`, Xposed or LSPosed API. `feature/ypower/runtime` restores API-102 target-process simulation and runtime tracing behind the shared HookGateway configuration.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

Profile persistence and privileged execution are isolated behind the repository. CI compiles the
integrated and standalone host. Additional correlation fixtures can be added when the shared Hook
runtime exposes trace streams.

## Standalone

The generic standalone host injects the same Root/Hook gateways used by the integrated application.
