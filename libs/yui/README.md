# YUI design system

YUI is the single UI and layout layer for YSuite and every standalone feature build.

## Architecture

Normal feature screens keep business content in `apps/*/feature`, while shared presentation belongs here:

- `YPageTemplate` owns content width, row density, card emphasis and app-bar prominence for each page role.
- `YPageScaffold` and role-specific scaffolds render those templates.
- `YAppShell` uses the official Material 3 Adaptive Navigation Suite for top-level navigation.
- `YNavigationSuite` is only a library-level compatibility alias; feature modules are CI-enforced to use `YAppShell`.
- `YPageList`, list/detail and supporting-pane helpers own responsive layout.
- `YTheme`, semantic colors, shapes and generated tokens own Material 3 styling.
- `YViewLayout` and `Theme.YUI` are the compatibility layer for Java/View/XML features.
- `YForms`, `YInteractions` and shared status/action components own reusable controls.
- Feature-local themes, design tokens and normal-screen navigation shells are not allowed.

Window gutters are 16dp compact, 24dp medium and 32dp expanded. Android View/XML resources use the same breakpoints through resource qualifiers.

## Page roles

Use the narrowest role that describes the screen rather than the feature name:

- `SETTINGS` — preferences and configuration
- `LIST` / `TIMELINE` — ordinary or chronological collections
- `MANAGER` — dense controls and managed items
- `BROWSER` — file/content browsing
- `DASHBOARD` — overview and status surfaces
- `DETAIL` — one selected entity
- `LOG` — diagnostic/log output
- `EDITOR` — structured editing
- `WIZARD` — multi-step flows

Specialized interaction surfaces such as overlays, capture probes, drag/reorder surfaces and transparent helper activities may keep purpose-built geometry when that geometry is part of the feature itself.

## Design references

The implementation follows established open-source Android patterns rather than copying another app's visual identity:

- Android **Now in Android**: Material 3 design-system ownership and adaptive top-level navigation.
- Android **Material 3 Adaptive**: canonical adaptive navigation and pane behavior.
- **Seal**: settings hierarchy and task-first screen structure.
- **Droid-ify**: dense searchable/filterable app lists.
- **Material Files**: path-first file-browser information hierarchy.

YSuite keeps its own palette, semantics and component vocabulary. Dynamic color is used when available; the shared YUI palette is the fallback.

## Maintenance rule

If a visual or layout behavior is reusable by more than one feature, change YUI instead of adding another feature-local implementation. CI verifies this boundary.

## CI contract

A change under `libs/yui/` is treated as a shared dependency change, so standalone CI rebuilds every enabled feature. Architecture checks also reject feature-owned normal-screen Material navigation and feature-local UI systems.

## Runtime appearance preferences

YSuite includes an in-app appearance settings centre, backed by
`YAppearanceStore` in `libs/yui`. It exposes validated and versioned
parameters for theme, dynamic color, standard/compact/comfortable spacing,
button radius and padding, and text scaling. Default values preserve the prior
Material 3 standard layout; all touch targets remain at least 48dp.

The same store can resolve overrides for individual integrated modules using
`YAppearanceStore.MODULE_EXTRA` on their launch intents. Overrides fall
back to global values when not present. Shared Compose Activities automatically
subscribe to preference changes through `YTheme`. Feature business preferences
and Root/LSPosed configuration are not moved or cleared.

The JSON import/export operation only covers YUI appearance and home interactions;
the document is validated fully before replacement. Standalone APKs maintain
their own private preferences and are not silently synced across Android packages.
Legacy View/XML surfaces need an explicit migration to consume the new controls.
