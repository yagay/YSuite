# YSuite Product UI System

YSuite separates visual consistency from product structure.

## Layer 1: design system

`core:designsystem` owns theme, typography, colors, spacing, shapes, icons, buttons, dialogs and small reusable controls.

It does **not** decide how a file manager, browser, settings screen or automation editor is structured.

## Layer 2: product UI

`core:productui` owns product-grade interaction structures:

- `YFileManagerScaffold` — sources, breadcrumb, command bar, file pane, selection bar, optional detail pane.
- `YBrowserWorkspace` — address bar, tabs, browser toolbar, optional wide tab sidebar, content.
- `YSettingsSurface` — category navigation and readable preference content.
- `YDashboardSurface` — dashboard/overview content at adaptive widths.
- `YLogViewerSurface` — filters + log stream + optional details pane.
- `YDownloadManagerSurface` — queues/history/filtering/selection actions.
- `YTaskManagerSurface` — filters + task collection + optional details pane.
- `YAutomationStudioSurface` — library + editor + inspector on expanded screens.
- `YToolSurface` — focused parameter/form/utility workflows.

A feature chooses one product surface. It must not fall back to a generic page because a generic page is easier to wire.

## Open-source references

Architecture and design-system separation:
- Android Now in Android (Apache-2.0)
- Android Adaptive Apps Samples (Apache-2.0)

File-manager interaction references:
- XFiles (GPL-3.0-only): dual-pane/tree workflow, breadcrumb, selection toolbar, root/archive product behavior. Reference only; do not copy GPL source into YSuite unless YSuite adopts a compatible license.
- Material Files (GPL-3.0): breadcrumb, storage/root/archive/NAS UX. Reference only.
- MTExplorer (MIT): dual-pane context actions and manager/editor workflow.

Settings:
- Compose-Settings (MIT): dedicated settings groups, switches, radios, sliders and segmented preference patterns.

Browser:
- Solara (MPL-2.0): browser-owned tabs/address workspace and responsive navigation concepts. Reference architecture/interactions unless license obligations are intentionally accepted.

## Non-negotiable rule

Shared design does not imply shared page geometry.

A file manager must look and behave like a file manager.
A browser must look and behave like a browser.
A settings page must look and behave like settings.
An automation editor must look and behave like an automation studio.

The product surface is part of the feature contract and is validated by CI.
