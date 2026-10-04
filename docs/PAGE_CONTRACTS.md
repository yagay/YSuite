# Page contracts

YSuite uses one design system and multiple first-class adaptive page shapes. Features select the shell that matches their interaction model instead of forcing every screen through one scaffold.

## First-class page kinds

- `Dashboard`: overview, metrics, feature entry points.
- `Manager`: files, downloads, tasks, notifications and other dense collections.
- `Browser`: full-bleed browser/web content with feature-owned toolbar, tabs and bottom controls.
- `Tool`: diagnostics, parameter editors, forms and focused utilities.
- `Settings`: grouped preferences and toggles with readable line length.
- `Detail`: one entity, diagnostic result or focused editor.
- `Fullscreen`: previews, media, terminal or other intentionally chrome-free content.

## Shells

- `YSuiteDashboardShell`
- `YSuiteManagerShell`
- `YSuiteBrowserShell`
- `YSuiteToolShell`
- `YSuiteSettingsShell`
- `YSuiteDetailShell`
- `YSuiteFullscreenShell`

Legacy-friendly wrappers such as `YSuiteDashboardPage`, `YSuiteListPage`, `YSuiteDetailPage`, `YSuiteSettingsPage` and `YSuiteLazyListPage` are implemented on top of these shells.

## Ownership

The host owns feature navigation and feature lifecycle only.

A feature owns the composition of its own page by selecting a shared shell. It must not create a private theme or duplicate shell framework.

Browser and fullscreen surfaces are never forced into a document-width container. Manager surfaces may expand across large windows and may expose a supporting pane. Tool, Settings and Detail surfaces use readable width constraints.

## Adaptive contract

`YSuiteAdaptiveInfo` exposes both width and height classes.

Compact and medium host windows use drawer navigation. Expanded host windows use a permanent navigation pane.

A shell may change its layout at expanded width without changing feature business logic.

## State contract

Use `YSuitePageState` for shared loading, empty, error and permission states when appropriate. Browser/fullscreen content may use its own content-specific transient states while still using the shared design system.
