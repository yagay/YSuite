# YSuite unified Material 3 UI

Upstream renderer: [AndroidX Compose Material 3](https://github.com/androidx/androidx/tree/androidx-main/compose/material3)

## Architecture

**AndroidX Material 3 → libs/yui → ordinary feature screens.**
The upstream Material 3 library supplies accessibility, animations, semantics, typography and widgets.
YUI owns suite-wide sizing, color mapping, default shapes, compact button insets,
touch targets, standard dialogs and compatibility with Java/View modules.

- `YTheme` / `YSuiteTheme`: one color, typography and shape source. Dynamic color is
  opt-in, so legacy XML/View and Compose pages do not randomly use different palettes.
- `YMaterialControls.kt`: upstream Material 3 button, text button, outline button,
  icon button and checkbox adapters; all default geometry comes from `YDimens`.
- `YMaterialInputs.kt`: standard outlined text fields with the same shape on every screen.
- `YUiScaffold` and `YCustomTopBar`: shared page background, edge-to-edge insets,
  title-bar colors and navigation/action slot behavior for YUI and ProductUI.
- `YDialogs.kt`, `YViewDialogs.kt`: shared Compose and View dialog entry points.
- `YViewLayout`: legacy Java/View layouts, buttons, forms and switches use YUI XML tokens.
- `next/core/designsystem`: backwards-compatible aliases and adapters, **not** a parallel
  theme or collection of feature-specific default dimensions.
- `next/core/productui`: file manager, downloader, task manager and browser keep their own
  *page structure* (panes, command bars, drawers, tabs), but common visual controls should
  come from YUI and the active `MaterialTheme`.

Do not copy Material 3 source files into features or replace feature business logic.
Import `com.yagay.yui.YUiIconButton as IconButton` (or the matching adapter)
when a product-specific composable expects the upstream slot API.

## What has changed on this branch

- Moved theme defaults and button/switch sizing to common Material 3 + YUI sources.
- Aligned old View corner radii with Material 3: 8dp for buttons, 12dp for cards and outlined fields; YUI tokens remain generated from one JSON file.
- Consolidated two YSection renderers into the role-aware component; old setting rows now
  delegate to YListItem/YSwitchItem instead of drawing another row implementation.
- Consolidated regular and full-screen Scaffold/TopBar rendering and all normal Compose
  dialog entry points into YUI, including the shared product settings adapter.
- Centralized radio buttons, sliders, integer slider rows, tab strips and suite drawer
  item presentation alongside existing button and text-field facades.
- Generated ListIconSize, IconVisualSize and IconSmallSize so XML/View and Compose rows
  share the same icon geometry; normal YFloat picker rows reuse common touch heights.
- Generated success/warning/info semantic colors from the same JSON source as
  Material3 light/dark palettes and bound YView to those XML resources.
- Expanded the YUI preview catalog to cover radio, checkbox, slider and tabs.
- Removed recursive runtime restyling of View trees.
- Centralized button, icon button, checkbox and outlined text-field default geometry
  in the suite host, YEntryCleaner and the rebuilt YFiles/YDownload product workspaces.
- Routed standard dialogs from YNotify, YFloat and YParam through the Material 3
  View dialog builder while retaining existing dialog content and actions.
- Added `tools/verify_yui_controls.py` to architecture CI so feature UI cannot
  reintroduce independent Material 3 button/input imports. The check also rejects
  default Material 3 large text fields/list rows within ProductUI and requires
  product page chrome to delegate to the common YUI renderer.
- File-explorer search and detail rows, downloader tabs, and ordinary product
  Scaffold/TopAppBar now use shared YUI controls without rewriting their data logic.

## Known boundaries and validation

This is a **shared-control migration, not an assertion that every page has been
visually verified on a device**. Java/View screens retain their implementation
until converted; specialized overlays keep feature-owned geometry. Material 3
navigation, cards, menus and product-specific page composition are allowed
provided they use the shared theme. The static import check does not prove every
hard-coded size or layout is gone.

Before merging, verify architecture checks, Gradle debug build, light/dark mode,
dialog focus, accessibility touch targets, back navigation and the working
file/download UI on an actual device. GitHub Actions did not yet report a run
for the migration branch after its initial successful commit; the original
success runs cannot establish build success for this version.

## Standard UI density (October 2026)

Standard Material 3 replaces compact YUI sizing: 48dp visible buttons, minimum
56dp setting rows, 16dp page and card padding, 24dp horizontal button padding,
24dp normal icons, and unscaled Material3 switches. Compose and Java/View/XML
share `libs/yui/yui_tokens.json` generated dimensions. `YCompactSwitch` and
`compactButton` remain backwards-compatible aliases but no longer render compactly.
Compact/medium/expanded *viewport width classes* remain for responsive layouts.
