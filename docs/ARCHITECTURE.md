# YSuite Unified Architecture

## Goal

YSuite is one application platform, not a collection of applications merged after the fact.

The production rule is:

> **One YSuite host + many pure Features + one generic standalone composer.**

Every Feature keeps one source implementation. The same Feature can be:

- embedded into the complete `YSuite.apk`;
- packaged alone as `YFiles-debug.apk`, `YDownload.apk`, etc. through the generic standalone host.

Independent APK packaging is a build composition choice, not a second implementation.

## Layers

### 1. `libs/yapi` — stable Feature/Host contract

Feature code depends on narrow host contracts instead of concrete Application classes.

Shared contracts cover host identity, declared capabilities, Root execution, process reload, framework state and host logging. New cross-feature interaction should be expressed through a shared capability contract rather than direct Feature-to-Feature Gradle dependencies.

A Feature must not assume the host package is `com.yagay.YSuite`; the same source must work when packaged with its standalone application id.

### 2. `libs/ycore` — one runtime implementation

`ycore` is the shared runtime used by both the complete YSuite host and generic standalone composition. It owns shared infrastructure such as:

- Feature registry/state/lifecycle;
- Root gateway/process ownership;
- logging and diagnostics;
- crash attribution;
- LSPosed host coordination used by the combined app;
- shared host identity/ID rules;
- other process-global infrastructure that must not be reimplemented by each Feature.

A standalone package should reuse this runtime instead of creating a `YFilesRootManager`, `YDownloadLogger`, etc.

### 3. `libs/yui` — one UI framework

YUI owns the shared theme, ordinary Activity/window shell, edge-to-edge handling, standard Compose primitives and reusable UI components.

Features own business-specific screens and interactions, but must not fork a second general-purpose design system.

### 4. `apps/*/feature` — pure reusable Feature implementation

The long-term source of each product area is its Android library Feature module.

Feature code may contain business UI, ViewModels, repositories, Hook handlers, task handlers, databases and Android components that genuinely belong to that capability. It must not become a second application host.

Feature modules must therefore avoid owning:

- an `Application` implementation through their reusable manifest;
- host-level LSPosed module metadata;
- a separate global Root/logging/crash framework;
- direct dependencies on another Feature module;
- hard-coded `com.yagay.YSuite` package assumptions;
- generic global component IDs that collide when multiple Features share one APK.

Reusable Android resources should use module-specific names such as `yfiles_*`, `ydownload_*`, `ydiag_*` wherever practical.

### 5. `suite/YSuite` — the only full production app host

The full YSuite APK owns the application boundary and combined system integration:

- launcher/home UI;
- `Application`;
- single combined LSPosed entry and Hook registry;
- shared Root/runtime ownership;
- shared Accessibility/Notification/Boot/IPC routers where reuse is valid;
- feature enable/disable state and lifecycle;
- combined diagnostics and crash attribution.

The host consumes every configured Feature module directly from `config/features.toml` generated metadata.

### 6. `standalone/host` — one generic standalone composer

There is one standalone application module for all Features.

A build selects one Feature:

```bash
gradle buildFeatureDebug -PySuiteStandaloneFeature=yfiles
```

The build composer derives from `config/features.toml`:

- Feature module;
- application id;
- app name;
- entry Activity;
- standalone LSPosed entry metadata;
- output APK name.

The resulting APK contains shared Host Runtime + YUI + exactly the selected Feature. The generic host contains no Feature business logic.

Legacy `apps/*/app` shells may remain temporarily as migration/reference copies, but they are no longer the preferred CI/build path and should not receive new business infrastructure.

## Android component ownership

"Unified" does not mean forcing every Android component into one physical class. Components fall into three ownership classes.

### Global singletons

Use one host implementation where process/application semantics require one owner, for example:

- Application;
- Root session/manager;
- crash handler;
- global logger;
- full-app LSPosed entry;
- shared configuration/runtime registries.

### Shared routers/brokers

Use one host router when several Features can safely share the platform component, for example:

- boot/package event routing;
- notification management;
- foreground task brokerage;
- shared IPC entry points;
- some FileProvider/Storage bridges.

### Host-managed independent slots

Some Android APIs legitimately require separate declarations or metadata. Examples can include:

- TileService;
- AppWidgetProvider;
- DocumentsProvider;
- special AccessibilityService variants;
- VPNService;
- feature-specific providers/services with distinct contracts.

These are allowed, but they remain host-governed and must use collision-safe class names, authorities, actions and resources.

## Feature dependency rule

A Feature must not use:

```kotlin
implementation(project(":another-feature"))
```

for normal cross-feature behavior.

Instead expose a narrow capability through the shared Host API. Optional capability consumers must provide a fallback when the provider Feature is absent in a standalone build.

This keeps every Feature independently composable.

## Identity and global ID rule

Any identifier that becomes global inside one APK must be namespaced or allocated by shared infrastructure, including:

- provider authorities;
- broadcast actions;
- notification channels/IDs;
- WorkManager unique names;
- preference keys/files;
- database names;
- file/cache directories;
- deep links;
- request IDs and other host-wide registries.

Feature code must derive real package identity from the host/context rather than hard-coding the YSuite application id.

## Root / Hook strategy

- Full YSuite: one Root runtime and one LSPosed module entry with Feature Hook plugins routed through host ownership.
- Standalone composition: the generic shell packages only the selected Feature Hook entries while reusing the same Feature Hook implementation.
- Hook business code remains one source file regardless of package mode.
- Duplicate physical hooks in the combined host are coordinated by the host Hook registry.

## Logging and diagnostics

Combined YSuite logs use the shared host writer with Feature IDs from the Feature registry.

The same Feature should call host contracts rather than inventing a second logger for standalone mode. This keeps standalone testing representative of behavior after the Feature is placed back into YSuite.

## Build and CI contract

Validation is layered:

1. Feature catalog generation/check;
2. Feature/Host boundary check;
3. affected standalone Feature composition;
4. integration/resource/manifest hazard scan;
5. full YSuite build.

`tools/verify_feature_boundaries.py` rejects direct Feature-to-Feature dependencies, duplicate Android component ownership, duplicate authorities, Feature-owned Application declarations and Feature-packaged LSPosed host metadata.

`tools/scan_feature_integration.py` remains responsible for broader integration/resource hazards.

## Adding a Feature

A normal new Feature should require:

1. create one reusable Android library Feature;
2. register it in `config/features.toml`;
3. regenerate/check generated catalogs;
4. use YAPI/YCore/YUI capabilities instead of creating another app stack;
5. build it alone through `:standalone`;
6. build it as part of YSuite.

Adding a Feature should not require creating another full Application module.

## Migration rule

Existing Features are migrated incrementally:

1. keep all business/UI/runtime logic in `feature`;
2. move shared Root/logging/runtime plumbing behind Host API/Core;
3. remove standalone-only Application assumptions from reusable source;
4. remove direct Feature-to-Feature dependencies;
5. namespace reusable resources and host-global IDs;
6. verify generic standalone build;
7. verify full YSuite composition;
8. only then retire the corresponding legacy `apps/*/app` shell.

Compilation alone is not acceptance; behavior in `PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md` remains the acceptance contract.
