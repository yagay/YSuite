# Feature migration: YNotify

Product surface: LogViewer
Upstream project: darshanparajuli/LogcatReader
Upstream license: MIT

## Scope

Reimplements persistent notification/UI history with real NotificationListenerService and
AccessibilityService capture. Notification updates are deduplicated by notification key, removal
time is preserved, UI events are classified as Toast/dialog/snackbar/popup/SystemUI/other, actual
presentation observations are correlated back to nearby notifications, and history can be
reclassified, searched, inspected and exported.

## Product UI

Uses `YNotifyWorkspace` for search, type filters, a history stream and a focused details pane.
The LogcatReader-style structure is layout-only; capture/classification remains YNotify-owned.

## Platform capabilities

Android listener/accessibility services and SQLite persistence live in the feature runtime module so
the Compose implementation stays free of View/service infrastructure. LSPosed enrichment can later
feed the same runtime store without changing the UI model.

## Localization

English and Simplified Chinese resources are maintained together in UI and runtime modules.

## Tests

Classification and persistence are isolated in runtime helpers. CI compiles the runtime transitively
through integrated and standalone hosts.

## Standalone

The same runtime service manifest is transitively included by the generic standalone host.
