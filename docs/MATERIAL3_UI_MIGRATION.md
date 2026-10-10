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
- Removed recursive runtime restyling of View trees.
- Centralized button, icon button, checkbox and outlined text-field default geometry
  in the suite host, YEntryCleaner and the rebuilt YFiles/YDownload product workspaces.
- Routed standard dialogs from YNotify, YFloat and YParam through the Material 3
  View dialog builder while retaining existing dialog content and actions.
- Added `tools/verify_yui_controls.py` to architecture CI so feature UI cannot
  reintroduce independent Material 3 button/input imports.

## Known boundaries and validation

This is a **shared-control migration, not an assertion that every page has been
visually verified on a device**. Java/View screens retain their implementation
until converted; specialized overlays keep feature-owned geometry. Material 3
navigation, cards, menus and product-specific page composition are allowed
provided they use the shared theme. The static import check does not prove every
hard-coded size or layout is gone.

Before merging, verify architecture checks, Gradle debug build, light/dark mode,
dialog focus, accessibility touch targets, back navigation and the working
file/download UI on an actual device.
