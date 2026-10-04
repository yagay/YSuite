# Architecture

YSuite Next is a clean-room multi-module rebuild inspired by mature Android projects such as Now in Android and Element X Android.

## Dependency direction

```
app
 ├─ core:ui
 └─ core:navigation

host:standalone
 ├─ feature:<name>:impl
 └─ core:ui

feature:<name>:impl
 ├─ feature:<name>:api
 ├─ core:ui
 ├─ core:designsystem
 └─ core:presentation

feature:<name>:api
 ├─ core:model
 └─ core:navigation

core:ui
 ├─ core:designsystem
 ├─ core:navigation
 ├─ core:resources
 ├─ core:runtime
 └─ core:settings

core:settings         -> DataStore-backed application settings
core:logging          -> centralized structured logging contract
core:permissions      -> permission status/checking boundary
core:diagnostics      -> framework-neutral diagnostic runner
core:platform:api     -> Root / Hook contracts only
core:platform:android -> replaceable Android adapters
```

## UI architecture

The host owns module navigation and lifecycle, not feature page geometry.

Every feature declares a `YSuitePageKind` and composes its screen through the matching shared shell. The supported kinds are Dashboard, Manager, Browser, Tool, Settings, Detail and Fullscreen.

This means YFiles can use a dense manager layout, a browser can own its address/tab controls, and diagnostics/settings can keep readable document widths without creating separate design systems.

## Rules

1. Feature implementations never depend on another feature implementation.
2. Feature API modules are framework-neutral Kotlin modules.
3. Shared UI primitives live only in `core:designsystem`.
4. Shared adaptive shells and page patterns live only in `core:ui`.
5. Feature modules choose a shared shell; they do not invent a second app shell or theme.
6. Shared strings belong in `core:resources`; feature-specific strings stay inside that feature.
7. Root and LSPosed are accessed only through `core:platform:api`.
8. The app module is the composition root; the host owns feature navigation only.
9. Standalone APKs reuse the same feature implementation and shared UI.
10. No legacy YSuite source is used by this branch.
11. Manual composition is preferred until dependency injection is actually needed.
