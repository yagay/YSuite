# Page contracts

YSuite uses one design system with multiple first-class adaptive page shapes. A feature selects the page family that fits its job; there is no compatibility layer that forces every screen through one legacy scaffold.

## First-class page kinds

- `Dashboard`: overview, metrics and feature entry points.
- `Manager`: files, downloads, tasks, notifications and other dense collections.
- `Browser`: full-bleed browser/web content with feature-owned toolbar, tabs and bottom controls.
- `Tool`: diagnostics, parameter editors, forms and focused utilities.
- `Settings`: preference-oriented screens with readable line length.
- `Detail`: one entity, result or editor.
- `Fullscreen`: preview, media, terminal or other intentionally chrome-free content.

## Shared shells

- `YSuiteDashboardShell`
- `YSuiteManagerShell`
- `YSuiteBrowserShell`
- `YSuiteToolShell`
- `YSuiteSettingsShell`
- `YSuiteDetailShell`
- `YSuiteFullscreenShell`

Convenience screen layouts are new-only: `YSuiteDashboardScreen`, `YSuiteSettingsScreen`, `YSuiteDetailScreen` and `YSuiteManagerListScreen`.

## Ownership

The host owns module navigation and lifecycle only. A feature owns composition inside the appropriate shared shell.

Browser and fullscreen surfaces are never constrained to document width. Manager surfaces can use wider space and supporting panes. Tool, Settings and Detail surfaces keep readable widths.

## Adaptive contract

`YSuiteAdaptiveInfo` exposes width and height classes. Compact and medium host windows use drawer navigation; expanded windows use a permanent navigation pane.

## No legacy UI

The pre-rebuild `YSuiteSection`, `YSuiteListItem`, `YSuiteFilterBar`, `YSuiteDashboardPage`, `YSuiteLazyListPage` and related APIs are removed. New code must use the new visual primitives and shells directly.
