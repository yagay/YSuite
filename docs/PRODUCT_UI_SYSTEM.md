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

## Shared outer chrome

All normal product workspaces share the same YSuite-owned outer chrome:

- `YSuiteProductTopBar` owns app-bar color, title/navigation treatment and the separator.
- `YSuiteProductScaffold` owns the page background and edge-to-edge system-bar insets.
- Mature upstream layouts still own their internal information architecture, panes, tabs, filters,
  queues, browser controls and content density.
- Browser and fullscreen product types may intentionally omit the standard app bar when their
  upstream product model owns the complete screen.

This keeps YFiles, YDownload, Settings, System and the home dashboard visually part of one app
without flattening them into one generic page template.

## Hard rule

Feature modules may not use the previous YSuite-authored generic product surfaces. CI rejects them.

When a feature is created, `--product` selects the mature upstream page family. A feature may add domain-specific behavior inside that page family, but may not replace the page with a generic card/list composition.

See `UPSTREAM_PRODUCT_BASES.md` and `THIRD_PARTY_NOTICES.md`.
