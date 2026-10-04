# Architecture

YSuite Next is a clean-room multi-module rebuild inspired by mature Android projects such as
Now in Android and Element X Android.

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
 └─ core:resources

core:settings        -> DataStore-backed application settings
core:logging         -> centralized structured logging contract
core:permissions     -> permission status/checking boundary
core:diagnostics     -> framework-neutral diagnostic runner
core:platform:api    -> Root / Hook contracts only
core:platform:android-> replaceable Android adapters
```

## Rules

1. Feature implementations never depend on another feature implementation.
2. Feature API modules are framework-neutral Kotlin modules.
3. Shared UI primitives live only in `core:designsystem`.
4. Shared page patterns live only in `core:ui`.
5. Shared strings belong in `core:resources`; feature-specific strings stay inside that feature.
6. Root and LSPosed are accessed only through `core:platform:api`.
7. The app module is the composition root and navigation owner.
8. Standalone APKs reuse the same feature implementation and shared UI.
9. No legacy YSuite source is used by this branch.
10. Manual composition is preferred until dependency injection is actually needed.
