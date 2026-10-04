# Page contracts

Future features do not own page structure. They select a page contract and provide content/state.

## Standard page roles

- `YSuiteDashboardPage`: overview, metrics and entry points.
- `YSuiteListPage`: searchable/filterable collections and managers.
- `YSuiteDetailPage`: one entity, diagnostic result or configuration detail.
- `YSuiteSettingsPage`: grouped preferences and toggles.

All roles are built on `YSuitePage` and therefore share:

- one title/subtitle hierarchy,
- one spacing system,
- one adaptive content width,
- one loading/empty/error/permission state host,
- one theme and shape system.

## State contract

Use `YSuitePageState`:

- Content
- Loading
- Empty
- Error
- PermissionRequired

A feature supplies data and callbacks only. It must not invent a second loading/error/page framework.
