# YSuite Unified Architecture

## Goal

Keep the codebase easy to change while allowing every project to build both independently and inside one unified APK.

## Layers

### 1. `core/` — shared host contracts only

Keep this intentionally small. It owns:

- feature registry and feature enable state;
- shared Root host entry/status;
- shared YSuite logging/export;
- crash attribution and active-feature context;
- constants/contracts needed by both the host and reusable feature modules.

It should not absorb feature business logic.

### 2. `suite/` — unified shell

The YSuite app owns:

- launcher/home UI;
- unified theme and shared navigation patterns;
- shared Root/LSPosed/Hook host presentation;
- shared diagnostics entry points;
- feature enable/disable controls;
- feature launch and crash attribution.

The shell should not copy feature screens.

### 3. `feature/` — reusable feature implementation

Each project keeps its actual feature UI/business/runtime here. Both the standalone app and YSuite depend on the same module.

Feature modules may contain Compose, AppCompat, classic Activity/View, services, providers, databases and Hook code, but host-dependent initialization must be explicit rather than relying on a standalone `Application` side effect.

### 4. `app/` — standalone shell

Each standalone APK should be thin. It supplies launcher/application/package wiring and the standalone implementation of host services that the feature needs.

## UI strategy

Unify the shell first, not every screen by force.

Shared rules:

- Material-compatible DayNight host theme;
- common top-level spacing, typography and status presentation;
- common Root/Hook status chips/rows;
- common list/search/filter patterns where they fit;
- common diagnostics/export controls;
- common confirmation and restart messaging.

Feature-specific complex screens may keep their own layout until conversion is useful. A UI rewrite must not become a prerequisite for preserving functionality.

## Root / Hook strategy

- Standalone APK: feature may use its standalone host implementation.
- YSuite: prefer one shared Root session/command layer and one shared LSPosed/Hook host layer.
- Feature code should depend on a narrow contract, not a concrete standalone Application class.
- Hook process entry points remain process-safe and explicit; do not move Android UI state into Hook processes.

## Logging strategy

All YSuite logs use one host writer and module IDs from `FeatureRegistry`.

Required events:

- host start and version/contract revision;
- per-feature enabled/disabled state;
- runtime initialization requested/succeeded/failed;
- feature open requested/failed;
- Root/Hook failures and meaningful runtime failures;
- uncaught crash with active feature attribution.

Do not log high-frequency successful refresh loops.

Exports:

- full: `Download/YSuite/YSuite-all-logs-<timestamp>.zip`
- module: `Download/YSuite/YSuite-<module>-logs-<timestamp>.zip`

## Module add/remove contract

Adding a feature should normally require:

1. add the Git submodule / feature project;
2. include its Gradle feature module;
3. add one `FeatureSpec`;
4. add Hook metadata only if the feature actually has Hook entry points.

Removing a feature should be the reverse. No unrelated module should need modification.

## Migration rule

Refactor incrementally. For each module:

1. move shared business/UI/runtime into `feature` if not already there;
2. remove dependence on standalone-only `Application` assumptions;
3. wire shared host services behind small contracts;
4. adopt shared theme/status/diagnostic components where useful;
5. verify standalone build;
6. verify YSuite build;
7. verify open/return/disable/log export;
8. only then remove old duplicate/compatibility code.

Compilation alone is not acceptance; behavior in `PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md` is the acceptance contract.
