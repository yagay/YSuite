# Architecture

YSuite Next is a clean-room multi-module rebuild inspired by mature Android projects such as
Now in Android and Element X Android.

## Dependency direction

```
app
 ├─ core:ui
 ├─ core:navigation
 └─ core:resources

core:ui
 ├─ core:designsystem
 └─ core:resources

core:designsystem   -> no feature dependencies
core:resources      -> no feature dependencies
core:navigation     -> core:model
core:platform       -> core:common
core:model          -> standalone
core:common         -> standalone
```

## Future feature shape

```
feature/<name>/api
feature/<name>/impl
```

Rules:

1. Feature implementations never depend on another feature implementation.
2. Shared UI primitives live only in `core:designsystem`.
3. Shared page patterns live only in `core:ui`.
4. Shared strings belong in `core:resources`; feature-specific strings stay inside that feature.
5. Root, LSPosed and logging are accessed through `core:platform` contracts.
6. The app module is the composition root and navigation owner.
7. No legacy YSuite source is used by this branch.
