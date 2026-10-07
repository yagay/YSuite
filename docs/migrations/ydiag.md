# Feature migration: YDiag

Product surface: LogViewer
Upstream project: darshanparajuli/LogcatReader
Upstream license: MIT

## Scope

Reimplements selected-app diagnostics with option presets, Root/Hook capability awareness,
human/AI-readable evidence, immediate collection without reboot, and structured per-module export.

## Product UI

Uses `YDiagWorkspace`: search/filter controls, diagnostic stream and a focused evidence pane follow
the LogcatReader product hierarchy while YSuite owns all diagnostic options and collection logic.

## Platform capabilities

Root collectors go exclusively through `RootGateway`; Hook-aware options use `HookGateway`; `feature/ydiag/runtime` provides the API-102 target-process lifecycle, Intent, WebView, network, file, method and stack trace hooks.
No direct process execution or Xposed API is present in the feature.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

Collectors are option-isolated and bounded. CI compiles integrated and standalone hosts; command
fixture/parser tests will expand as the shared Hook adapter and Perfetto backend are migrated.

## Standalone

The generic standalone host injects the same Root/Hook gateways used by the integrated application.
