# Feature migration: YTaskManager

Product surface: TaskManager
Upstream project: RohitKushvaha01/TaskManager
Upstream license: Apache-2.0

## Scope

Reimplements the existing YSuite task manager behaviour: Root-backed process inspection and
controls, CPU/RAM/SWAP/GPU resources, process search/filter/sort, and per-UID traffic visibility.

## Product UI

Uses `YTaskManagerWorkspace`, whose Resources/Processes hierarchy follows the mature Android
TaskManager product layout. YSuite data collection and controls remain independent.

## Platform capabilities

Uses shared `RootGateway` for privileged reads and controls and `HookGateway` only for capability
status. No direct `su`, daemon, Xposed or Shizuku dependency exists in the feature.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

Business parsing remains isolated in the repository. CI compiles the feature through integrated and
standalone hosts; parser fixture tests will be expanded as additional traffic backends are migrated.

## Standalone

The generic standalone host injects the same shared Root/Hook gateways used by the integrated app.
