# Feature migration: YParam

Product surface: EntityManager
Upstream project: LibChecker/LibChecker
Upstream license: Apache-2.0

## Scope

Reimplements per-app parameter overrides for display, locale/time zone, window behaviour, WebView
identity and location simulation. The full existing override model is retained, with default values
shown next to overrides and restore-default actions for every parameter.

## Product UI

Uses `YParamWorkspace` for an app/entity collection plus focused parameter detail. Known domains
are offered as presets while custom values remain possible.

## Platform capabilities

Persistence is feature-local. Hook application is routed only through the shared `HookGateway`;
the feature contains no direct LSPosed/Xposed API calls.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

Validation and serialization are isolated in the feature implementation. CI compiles both integrated
and standalone hosts; model tests will grow with the Hook adapter migration.

## Standalone

The generic standalone host injects the same shared Hook gateway as the integrated application.
