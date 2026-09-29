# YUI shared UI architecture

`YSuite/ui` is the single source of truth for UI that can be shared across YSuite and the standalone feature APKs.

## Ownership

YUI owns:

- Material theme, dynamic light/dark colour and typography.
- Shared shape and spacing tokens.
- Status/navigation bar appearance and normal Activity edge-to-edge policy.
- System-bar/cutout insets for traditional View Activities.
- Standard Compose Activity shell (`YComposeActivity`).
- Standard Compose page/components (`YScaffold`, `YScreen`, `YCard`, `YSettingRow`, status/loading/error states, buttons).
- Standard XML/View theme (`Theme.YUI`), button/card defaults and dimensions.
- Versions of common Android UI dependencies that all features need.

Feature modules own:

- Business state and feature-specific screens/content.
- Feature-specific controls whose behaviour or geometry is genuinely unique.
- Special window semantics only when required by the feature.

## Standalone and YSuite builds

Standalone feature builds depend on:

```kotlin
implementation("com.github.yagay.YSuite:ui:main-SNAPSHOT")
```

Every standalone repository includes JitPack and disables changing-module caching so the next build can consume a newly updated YUI snapshot.

When those same feature modules are embedded in YSuite, root dependency substitution replaces the remote artifact with local `project(":ui")`. Therefore:

- standalone APK and YSuite use the same YUI API/source;
- YSuite never packages a second remote copy of YUI;
- UI fixes can be implemented once;
- feature source remains independently buildable.

JitPack builds only `:ui`. `settings.gradle.kts` intentionally skips the host and feature git submodules when `JITPACK=true`.

## Compose screens

Prefer `YComposeActivity` for ordinary Compose Activities:

```kotlin
class MainActivity : YComposeActivity() {
    @Composable
    override fun YContent() {
        FeatureScreen()
    }
}
```

Use `onBeforeYContent()` only for initialization that must happen before composition.

Do not create another app-level `MaterialTheme`, dynamic colour scheme, or Activity edge-to-edge implementation inside a normal feature screen.

Use `YScaffold` when the page needs the standard YUI top bar. A feature may still use Material components inside the content when they are part of the feature UI.

## Traditional View screens

Normal View Activities are handled automatically by `YUiInitializer` through AndroidX Startup. YUI applies the shared system-bar policy and one system-bar/cutout padding layer.

Use shared XML resources where practical:

- `@style/Theme.YUI`
- `@dimen/yui_screen_horizontal`
- `@dimen/yui_screen_vertical`
- `@dimen/yui_section_gap`
- `@dimen/yui_card_padding`
- `@dimen/yui_control_gap`
- `@dimen/yui_button_height`
- `@dimen/yui_card_radius`

Feature theme names may remain as compatibility aliases for existing manifests, but normal aliases must inherit `Theme.YUI`.

## Special windows

Screenshot, transparent, probe, overlay or other Activities that require their own window semantics implement:

```kotlin
YUiWindowOptOut
```

YUI must not contain feature package/class-name checks. The feature declares the exception itself.

## Adding a new feature

A new feature should normally need only:

1. the shared YUI dependency;
2. a theme alias inheriting `Theme.YUI` if its manifest already references a feature-specific name;
3. `YComposeActivity` for a Compose entry point, or the automatic View shell for a View entry point;
4. business UI/content only.

Do not copy YUI source into the feature.

## CI guardrails

`tools/scan_feature_integration.py` reports common UI-ownership regressions such as local edge-to-edge/insets handling, local dynamic colour roots, and non-YUI theme parents. These are warnings because intentional special windows exist; special windows should use `YUiWindowOptOut`.
