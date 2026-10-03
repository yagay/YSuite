# YUI design system

YUI is the single UI and layout layer for YSuite and every standalone feature build.

## Architecture

Normal feature screens keep business content in `apps/*/feature`, while shared presentation belongs here:

- `YPageScaffold` and role-specific scaffolds own page chrome and content width.
- `YNavigationSuite` owns top-level navigation: bottom navigation on compact windows and a rail on wider windows.
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
- Android **Adaptive Apps samples**: compact/medium/expanded layout behavior.
- **Mihon**: shared utility-app scaffolds and dense settings/list presentation.

YSuite keeps its own palette, semantics and component vocabulary. Dynamic color is used when available; the shared YUI palette is the fallback.

## Maintenance rule

If a visual or layout behavior is reusable by more than one feature, change YUI instead of adding another feature-local implementation. CI verifies this boundary.

## CI contract

A change under `libs/yui/` is treated as a shared dependency change, so standalone CI rebuilds every enabled feature. Architecture checks also reject feature-owned normal-screen Material navigation and feature-local UI systems.
