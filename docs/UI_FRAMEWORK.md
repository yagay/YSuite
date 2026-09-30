# YSuite UI Framework Contract

YSuite uses one shared UI design system across combined and standalone builds. Feature business logic may use Compose, classic Views, or XML, but normal app screens must delegate their page-level presentation to `libs/yui`.

## Shared ownership

### Compose
Use the `YFeature*` vocabulary:

- `YComposeActivity`
- `YFeatureScaffold` / `YFeatureCustomScaffold`
- `YFeatureCard` / `YFeatureList` / `YFeatureSectionHeader`
- `YStatusRow` / `YStatusPill` / `YFeatureStat`
- `YSearchField`
- `YSettingRow` / `YSettingSwitch`
- `YFeatureEmpty`
- `YActionRow`

A complex feature may keep a custom top bar, sticky filter, drag overlay, or row interaction while the normal screen/card/status/empty-state layers stay YUI-owned.

### Classic Views / Java
Use `YViewLayout` and `YView`:

- `install` for scrolling pages
- `installFixed` when a ListView/RecyclerView owns scrolling
- `card`, `sectionHeader`, `statusLine`, `setStatus`
- `searchField`, `switchRow`, `keyValueRow`, `emptyState`
- shared primary/secondary buttons and root/text styling

### XML / ViewBinding
Activity layouts consume shared `Widget.YUI.*`, `TextAppearance.YUI.*`, and `@dimen/yui_*` resources.

## Compatibility adapter

YFloat predates the shared View framework and has many focused Java pages built through `AppUi`. Rewriting those pages independently would create unnecessary behavior risk, so `AppUi` is treated as a compatibility adapter. Its normal screen palette is backed by YUI/Material tokens through `UiTokens -> YView`.

The adapter is accepted by CI only while that YUI backing remains present. Floating result/action-menu surfaces may retain dedicated high-contrast overlay tokens because they are not normal app pages.

## Specialized exceptions

An exception is allowed only when the window itself is part of the interaction rather than a normal page. Current examples are:

- YFloat `ResultActivity`: transparent result-dialog host
- YFloat `SecureCaptureProbeActivity`: controlled `FLAG_SECURE` marker-color probe
- YFloat `ShadeDismissActivity`: empty translucent short-lived compatibility Activity

Drag previews, horizontal lock gestures, floating OCR/result surfaces, log consoles, and focused form editors may keep specialized internal rendering. Their surrounding normal page shell still follows YUI when applicable.

Do not add an exception merely because migrating a normal screen is inconvenient.

## CI enforcement

`tools/verify_ui_screen_ownership.py` automatically discovers normal `Activity`, `*Screen` and `activity_*.xml` surfaces. A normal screen must be owned by:

1. shared Compose YUI,
2. `YViewLayout`,
3. shared YUI XML resources, or
4. the verified YFloat compatibility adapter.

Unknown new normal screens fail architecture CI. Specialized exceptions are explicit and stale exception paths also fail.

`tools/verify_ui_framework.py` verifies the key shared components and migrated high-value screens. `tools/verify_yentry_unified_ui.py` additionally protects YEntryCleaner's complex interactions while enforcing shared presentation ownership.

## New features

`tools/new_feature.py` already generates a YUI-based scaffold. New features should extend that scaffold rather than creating another feature-local design system.
