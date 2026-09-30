# YSuite

YSuite is the monorepo for the Android/LSPosed projects that share the YSuite host, UI and runtime infrastructure.

## Repository layout

```text
YSuite/
├── apps/
│   ├── YDiag/
│   ├── YNotify/
│   ├── YPower/
│   ├── YMiniGuard/
│   ├── YEntryCleaner/
│   ├── YNFC/
│   ├── YTaskManager/
│   ├── YParam/
│   └── YFloat/
├── libs/
│   ├── ycore/
│   └── yui/
├── suite/
│   └── YSuite/
├── docs/
├── tools/
└── .github/workflows/
```

## Architecture

Each project under `apps/` keeps its standalone `app/` shell and reusable `feature/` module. The unified YSuite APK consumes those same feature modules directly, so feature code is not copied into the host.

`libs/yui` is the shared UI implementation. `libs/ycore` owns host-level contracts and shared runtime infrastructure. Code moves into `libs/` only when multiple projects genuinely share it; app-specific behavior stays inside its app folder.

The unified APK host is `suite/YSuite` with application id `com.yagay.YSuite`. YSuite remains the physical owner for shared system-facing capabilities such as the combined Xposed entry and host-level services.

## Included projects

- YDiag
- YNotify
- YPower
- YMiniGuard
- YEntryCleaner
- YNFC
- YTaskManager
- YParam
- YFloat

## Build

Build the unified compact ARM64 APK from the repository root:

```bash
gradle :suite:assembleCompact
```

The resulting APK is under `suite/YSuite/build/outputs/apk/compact/`.

The original standalone repositories remain intact as migration safety copies. New shared development uses this monorepo as the source of truth.

## Project rules

Before changing shared or integrated behavior, check:

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/MODULE_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/MONOREPO_MIGRATION.md`
