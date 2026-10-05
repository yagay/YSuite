# YSuite Product UI System

YSuite standardizes visual language, not product-page geometry.

## Layer 1 — unified YSuite design system

`core:designsystem` owns only:
- Material theme and semantic colors
- typography
- spacing
- shapes
- icons
- dialogs / sheets / snackbars
- motion
- light/dark behavior
- localization conventions

## Layer 2 — upstream product layouts

`core:productui` adapts mature open-source products while preserving their information architecture:

- `NiaDashboardSurface` — Android Now in Android
- `FileExplorerWorkspace` — SysAdminDoc/FileExplorer
- `YueBrowserWorkspace` — Yue-Browser
- `ComposeSettingsSurface` — Compose-Settings
- `LogcatReaderWorkspace` — LogcatReader
- `QdmDownloadWorkspace` — QDM-Android
- `ComposeTodoTaskWorkspace` — Compose-ToDo
- `OpenTaskerWorkspace` — OpenTasker
- `LibCheckerWorkspace` — LibChecker
- `NiaToolSurface` / `NiaDetailSurface` — Now in Android bounded-content conventions
- `FileExplorerPreviewSurface` — FileExplorer preview/fullscreen convention

The upstream page workflow remains recognizable after applying YSuite theme tokens.

## Shared Compose page host

All normal product workspaces run inside one YSuite-owned page lifecycle:

- `YSuiteProductPage` is the canonical normal-screen host.
- `YSuiteProductScaffold` and `YSuiteProductTopBar` are implementation primitives owned by
  `ProductChrome.kt`; product workspaces must not call them directly.
- The host owns app-bar treatment, page background, edge-to-edge system-bar insets, bottom bars and
  floating actions.
- `ProductAdaptiveRoot` classifies the window once at the YSuite host. Nested workspaces reuse the
  same `ProductAdaptiveInfo` instead of changing layout class after a navigation pane consumes width.
- Product workspaces keep their mature internal information architecture: file panes, download tabs,
  settings groups, log filters, entity inspectors and other domain-specific content.
- YFiles selection mode replaces only the shared top-bar slot; it does not create another Scaffold.
- Browser and fullscreen product types may intentionally omit normal page chrome when the upstream
  product model owns the complete screen.

This makes YSuite one Compose application with multiple product structures, rather than several
independent Compose applications embedded beside each other.

## Hard rule

Feature modules may not use the previous YSuite-authored generic product surfaces. CI rejects them.

When a feature is created, `--product` selects the mature upstream page family. A feature may add domain-specific behavior inside that page family, but may not replace the page with a generic card/list composition.

See `UPSTREAM_PRODUCT_BASES.md` and `THIRD_PARTY_NOTICES.md`.
