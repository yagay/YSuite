# YSuite monorepo migration

This branch converts YSuite from Git submodules to a real monorepo while preserving each Android project as an independently buildable project directory.

## Target layout

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

## Rules

1. `YSuite` is the source of truth after migration; feature code is not duplicated inside the host.
2. Every project under `apps/` keeps its `app/` and `feature/` split so its standalone APK can still be built independently.
3. Shared host/runtime code belongs under `libs/`; reusable code should move there only when at least two projects actually need it.
4. The YSuite host consumes the same `feature/` modules used by standalone apps.
5. Root GitHub Actions own monorepo CI. Nested project workflows are not authoritative after migration.
6. Changes under one app should build only that app plus affected shared/suite targets; shared library changes may fan out to dependent apps.
7. Debug/compact CI remains ARM64-only where the project supports native ABI filtering, with stable artifact names.
8. Old standalone repositories remain untouched during migration until the monorepo branch builds successfully.

## Migration safety

The migration is performed on `monorepo-migration`. `main` remains on the current submodule architecture until the imported source tree and YSuite host build successfully. The old repositories are not deleted or archived by the migration workflow.
