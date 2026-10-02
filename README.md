# YSuite

YSuite is the monorepo for the Android/LSPosed projects that share one host runtime, one UI system and one set of platform-facing infrastructure.

## Architecture in one sentence

**YSuite is the only full production app; every product area is a reusable Feature, and any Feature can be packaged into a standalone APK on demand by the generic standalone host.**

Feature business code is never copied between the combined app and standalone builds.

## Repository layout

```text
YSuite/
├── apps/                     # Feature source folders (legacy app shells remain only as migration copies)
│   ├── YDiag/feature/
│   ├── YDownload/feature/
│   ├── YFiles/feature/
│   └── ...
├── libs/
│   ├── yapi/                 # narrow Feature/Host contracts
│   ├── ycore/                # shared host runtime, Root, logging, lifecycle, diagnostics
│   └── yui/                  # shared UI framework
├── suite/
│   └── YSuite/               # the one full production application host
├── standalone/
│   └── host/                 # one generic APK shell for every Feature
├── config/
│   ├── features.toml         # single source of truth
│   └── generated/
├── tools/
└── .github/workflows/
```

## Host ownership

When Features are combined into YSuite, application-level capabilities have one owner. Shared Root, LSPosed entry/routing, logging, crash handling, settings infrastructure, diagnostics and other global services live in the host/core layer. Feature modules consume these capabilities through shared contracts instead of creating another app infrastructure stack.

Android components that genuinely need separate declarations (for example some TileService, AccessibilityService, DocumentsProvider, AppWidgetProvider or VPNService cases) are treated as host-managed slots rather than as independent app infrastructure.

`tools/verify_feature_boundaries.py`, host ownership checks and the integration scanner protect these boundaries in CI.

## Build the full YSuite APK

```bash
gradle :suite:assembleCompact
```

The resulting APK is under `suite/YSuite/build/outputs/apk/compact/`.

## Build one Feature as an APK

The generic standalone host reads the same `config/features.toml` metadata used by YSuite.

```bash
gradle buildFeatureDebug -PySuiteStandaloneFeature=yfiles
```

Output:

```text
build/standalone/YFiles-debug.apk
```

Release example:

```bash
gradle buildFeatureRelease -PySuiteStandaloneFeature=ydownload
```

Output:

```text
build/standalone/YDownload.apk
```

The standalone APK and YSuite both compile the same Feature module. There is no second copy of its business logic, UI or Hook implementation.

## Adding a Feature

The long-term path is:

1. create one Android library Feature;
2. register it once in `config/features.toml`;
3. regenerate/check catalogs;
4. use shared Host API/Core/YUI instead of creating app-level infrastructure;
5. verify both standalone composition and full YSuite composition.

The build system and CI derive the rest from the Feature catalog.

## Project rules

Before changing shared or integrated behavior, check:

- `docs/PRODUCT_REQUIREMENTS.md`
- `docs/MODULE_REQUIREMENTS.md`
- `docs/ARCHITECTURE.md`
- `docs/MONOREPO_MIGRATION.md`
