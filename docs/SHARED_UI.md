# YUI shared UI architecture

`YSuite/ui` is the single source of truth for UI shared by YSuite and the standalone feature APKs.

## Ownership

YUI owns:

- Material theme, dynamic light/dark colour and typography.
- Shared shape and spacing tokens.
- Status/navigation bar appearance and normal Activity edge-to-edge policy.
- System-bar/cutout insets for traditional View Activities.
- Standard Compose Activity shell (`YComposeActivity`).
- Standard Compose page/components (`YScaffold`, `YScreen`, `YCard`, `YSettingRow`, status/loading/error states, buttons and bottom action bars).
- Standard XML/View theme (`Theme.YUI`), button/card defaults and dimensions.
- Versions of common Android UI dependencies that all features need.

Feature modules own:

- Business state and feature-specific screens/content.
- Feature-specific controls whose behaviour or geometry is genuinely unique.
- Special window semantics only when required by the feature.

## Standalone and YSuite builds

Standalone repositories map the YUI module directly to the YSuite Git repository:

```kotlin
sourceControl {
    gitRepository(uri("https://github.com/yagay/YSuite.git")) {
        producesModule("com.github.yagay.YSuite:ui")
    }
}
```

Feature dependencies follow the YSuite `main` branch:

```kotlin
implementation("com.github.yagay.YSuite:ui") {
    version { branch = "main" }
}
```

A Git source-dependency checkout does not contain recursively initialized feature submodules. `YSuite/settings.gradle.kts` detects that state and configures only `:ui`, so a standalone app does not configure or build the YSuite host or the other feature projects.

When those same feature modules are embedded in a normal YSuite checkout, root dependency substitution replaces `com.github.yagay.YSuite:ui` with local `project(":ui")`. Therefore:

- standalone APK and YSuite compile the same YUI source;
- there is no copied UI source and no published AAR to keep in sync;
- UI fixes are implemented once in `YSuite/ui`;
- each feature repository remains independently buildable;
- YSuite never fetches its own UI over the network.

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

Use `YScaffold` for the normal page shell. It owns the top status-bar inset plus horizontal cutout/bottom safe-drawing insets, so feature screens should not add `safeDrawing`, `statusBarsPadding` or `navigationBarsPadding` again unless the surface is a deliberate special case.

Full-screen dialogs and similar edge-to-edge surfaces should use shared YUI components such as `YBottomActionBar` rather than calculating navigation-bar padding in the feature.

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

1. the source-control mapping for `com.github.yagay.YSuite:ui` in the standalone repository;
2. the shared YUI dependency following `main`;
3. a theme alias inheriting `Theme.YUI` if its manifest already references a feature-specific name;
4. `YComposeActivity` for a Compose entry point, or the automatic View shell for a View entry point;
5. business UI/content only.

Do not copy YUI source into the feature and do not publish a second UI artifact.

## CI guardrails

`tools/scan_feature_integration.py` reports common UI-ownership regressions such as local edge-to-edge/insets handling, local dynamic colour roots, and non-YUI theme parents. These are warnings because intentional special windows exist; special windows should use `YUiWindowOptOut`.

YSuite CI additionally clones the repository without recursive feature submodules and builds only `:ui`. This verifies that the exact lightweight source-dependency mode used by standalone apps remains buildable.
