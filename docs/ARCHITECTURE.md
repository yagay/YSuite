# YSuite Unified Architecture

## Goal

Keep the codebase easy to change while allowing every project to build both independently and inside one unified APK.

The default rule is: if behavior, UI infrastructure, dependency versions or host plumbing can be shared safely, keep one implementation and make both standalone apps and YSuite use it.

## Layers

### 1. `ui/` — one shared UI source

`YSuite/ui` is the single YUI implementation. It owns:

- Material theme, Day/Night behavior, typography, shapes and spacing tokens;
- normal Activity/window shell, edge-to-edge and system-bar handling;
- shared Compose Activity/scaffold/page components;
- common cards, settings rows, status/loading/error/empty states and dialog action bars;
- common AndroidX/Material/Compose UI dependency versions.

YSuite uses the local `:ui` project. Standalone projects resolve exactly the same source from `YSuite/main` with Gradle `sourceControl` and depend on `com.github.yagay.YSuite:ui` using the `main` branch.

There is no separate JitPack/AAR publication path. A Gradle source-dependency checkout does not initialize YSuite feature submodules, so `settings.gradle.kts` exposes only `:ui`. A normal recursive YSuite checkout exposes the complete host.

Feature code must not create a second general-purpose Theme/Insets/window framework. A genuinely special window, such as capture/transparent/overlay infrastructure, declares an explicit YUI opt-out rather than adding a class-name exception inside YUI.

### 2. `core/` — shared host contracts only

Keep this intentionally small. It owns:

- feature registry and feature enable state;
- shared Root host entry/status;
- shared YSuite logging/export;
- crash attribution and active-feature context;
- constants/contracts needed by both the host and reusable feature modules.

It should not absorb feature business logic or duplicate YUI behavior.

### 3. `suite/` — unified shell

The YSuite app owns:

- launcher/home UI built with YUI;
- shared Root/LSPosed/Hook host presentation;
- shared diagnostics and permission entry points;
- feature enable/disable controls;
- feature launch and crash attribution.

The shell should not copy feature screens or maintain a second window/theme coordinator.

### 4. `feature/` — reusable feature implementation

Each project keeps its actual feature UI/business/runtime here. Both the standalone app and YSuite depend on the same module.

Feature modules may contain Compose, AppCompat, classic Activity/View, services, providers, databases and Hook code, but host-dependent initialization must be explicit rather than relying on a standalone `Application` side effect.

Reusable feature resources are part of the same final Android resource table when YSuite is built, so feature-owned resource names must be module-specific wherever practical. Use names such as `yentrycleaner_*`, `ynotify_*`, `yfloat_*`, and module-specific compatibility theme aliases rather than generic names such as `AppTheme`, `accessibility_service_config`, or shared launcher names.

Application-level resources belong in the standalone `app/` shell when the reusable feature does not need them. This includes launcher artwork, standalone `app_name` / app description metadata, per-app locale configuration and similar APK identity resources.

YSuite CI runs `tools/scan_feature_integration.py --fail-on-high-risk`. New cross-feature resource collisions are build failures. UI bypasses such as a new local general-purpose `MaterialTheme`, edge-to-edge owner or system-bar padding path should be surfaced by the integration scan and either removed or documented as a real special case.

### 5. `app/` — standalone shell

Each standalone APK should be thin. It supplies launcher/application/package wiring and standalone implementations of host services that the feature needs. It must use the same YUI source rather than carrying a copied theme or UI dependency stack.

## UI strategy

Unify infrastructure aggressively, but do not force unrelated business screens into one layout.

YUI owns:

- theme and visual tokens;
- system bars, safe areas and ordinary Activity shell;
- common top-level page/scaffold patterns;
- common buttons/cards/status/settings/loading/error/empty components;
- shared UI dependency versions.

Features own:

- business state and actions;
- feature-specific lists, editors and visualizations;
- dimensions that have functional meaning rather than global design meaning.

This keeps global changes simple without flattening every screen into the same business layout.

## Root / Hook strategy

- Standalone APK: feature may use its standalone host implementation.
- YSuite: prefer one shared Root session/command layer and one shared LSPosed/Hook host layer.
- Feature code should depend on a narrow contract, not a concrete standalone Application class.
- Hook process entry points remain process-safe and explicit; do not move Android UI state into Hook processes.

## Logging and diagnostics strategy

All YSuite logs use one host writer and module IDs from `FeatureRegistry`.

Required events include host start/version, per-feature enabled state, runtime initialization, feature open failures, Root/Hook failures and uncaught crashes with active-feature attribution. Do not log high-frequency successful refresh loops.

Diagnostic export is built fresh at export time and combines shared evidence with module logs.

Exports:

- full: `Download/YSuite/YSuite-all-diagnostic-<timestamp>.zip`
- module: `Download/YSuite/YSuite-<module>-diagnostic-<timestamp>.zip`

## Module add/remove contract

Adding a feature should normally require:

1. add the Git submodule / feature project;
2. include its Gradle feature module;
3. add one `FeatureSpec`;
4. make the standalone project use the shared YUI source if it has ordinary UI;
5. add Hook metadata only if the feature actually has Hook entry points.

Removing a feature should be the reverse. No unrelated feature should need modification.

## Migration rule

For each module:

1. keep shared business/UI/runtime in `feature`;
2. remove standalone-only `Application` assumptions;
3. wire shared host services behind small contracts;
4. isolate reusable feature resources from standalone APK identity resources;
5. remove duplicate theme/window/Insets/common UI implementations in favor of YUI;
6. remove duplicate shared UI dependency versions;
7. verify standalone build through the YUI Git source dependency;
8. verify YSuite build and integration scan;
9. verify open/return/disable/diagnostic export;
10. only then remove old compatibility code.

Compilation alone is not acceptance; behavior in `PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md` remains the acceptance contract.
