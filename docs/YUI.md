# YUI design-system contract

YUI is the single normal-screen UI layer for YSuite and every standalone feature build. Feature modules own business state and business actions; `libs/yui` owns normal-screen visual language, geometry, navigation patterns, feedback and reusable interaction behavior.

## Core rules

- New features must not create feature-local `AppUi`, `UiTokens`, `DesignTokens`, `AppTheme` or `FeatureTheme` systems.
- Compose features start with `YPageScaffold` or a role wrapper: `YSettingsScaffold`, `YListScaffold`, `YManagerScaffold`, `YBrowserScaffold`, `YDashboardScaffold`, `YDetailScaffold`, `YTimelineScaffold`, `YLogScaffold`, `YEditorScaffold`, or `YWizardScaffold`.
- Ordinary content uses shared primitives such as `YPageList`, `YSectionHeader`, `YListItem`, `YSwitchItem`, `YCheckboxItem`, `YStatusItem`, `YProgressItem`, `YActionGroup`, `YFilterBar`, `YTabBar`, `YSelectionBar`, `YNotice` and `YLogPanel` before creating custom normal-screen UI.
- Forms use `YTextField`, `YNumberField`, `YPasswordField`, `YRadioGroup`, `YSliderField`, `YSegmentedControl`, `YDropdownField` and `YPickerItem`.
- Feedback and transient UI use `YConfirmDialog`, `YBottomSheet`, `YOverflowMenu` and `YMessageHost`.
- Adaptive screens use `YAdaptiveBox`, `YNavigationSuite`, `YListDetailScaffold`, `YSupportingPaneScaffold`, `YResponsiveGrid` and `YBreadcrumbBar` rather than feature-local window-width logic.
- Java/View and XML modules consume `YView`, `YViewLayout`, `Widget.YUI.*`, `TextAppearance.YUI.*` and `@dimen/yui_*`.
- Existing `YFeature*` APIs are compatibility wrappers. They remain supported while old screens migrate, but new modules are generated with current YUI APIs only.
- Specialized overlay windows, capture surfaces, OCR selection layers and high-contrast diagnostic consoles may use task-specific rendering. Their settings/control pages still use YUI.

## One token source

Geometry is defined only in `libs/yui/yui_tokens.json`.

`tools/generate_yui_tokens.py` generates:

- `YGeneratedTokens.kt` for Compose.
- `values/yui_tokens.xml` for View/XML.

Run `python3 tools/generate_yui_tokens.py` after changing token values. Architecture CI executes the generator in `--check` mode, so Compose/View/XML geometry cannot silently drift apart.

The minimum normal-screen touch target is 48dp. Breakpoints are shared tokens rather than copied constants.

## Page roles

Roles now affect shared layout behavior, including content width and list density.

- **SETTINGS** — preferences, capability toggles, provider/Hook options; constrained form width.
- **LIST** — ordinary searchable/filterable collections.
- **MANAGER** — long-running jobs such as downloads, tasks, notifications and processes; compact task density.
- **BROWSER** — hierarchical content such as files or component trees; compact rows and breadcrumb support.
- **DASHBOARD** — live status and diagnostics summaries; responsive grid/supporting pane patterns.
- **DETAIL** — one entity with properties and actions; constrained readable width or list/detail pane.
- **TIMELINE** — event/history streams.
- **LOG** — diagnostics and selectable log streams.
- **EDITOR** — rule/configuration editing; constrained form width and bottom actions.
- **WIZARD** — ordered setup/creation flows.

Uniformity means the same visual language and interaction grammar, not forcing unrelated features into one screen shape.

## Adaptive behavior

YUI exposes compact, medium and expanded width classes. Modules should not invent their own phone/tablet breakpoints.

- Compact: single-pane content and bottom navigation.
- Medium: wider single-pane layouts and navigation rail where appropriate.
- Expanded: list/detail or supporting-pane layouts and responsive grids.

This covers phones, landscape, tablets, foldables and resizable desktop-style windows without adding per-feature adaptive code.

## Semantic color and icons

Success, warning and information colors are semantic tokens and are not aliases for the product accent color. Shared normal-screen icons come from `YIcons` so direction-aware icons, sizing and future replacements stay consistent.

## Accessibility

- Interactive controls preserve a minimum 48dp target.
- Components must survive large font scaling; fixed-height buttons are forbidden.
- Directional icons use mirrored variants where applicable.
- Icon-only actions require content descriptions.
- Layouts must remain usable in dark mode and with high text scale.
- Keyboard/focus behavior should use platform/Material defaults rather than feature-local gesture-only controls.

## Living catalog

`YComponentCatalogScreen` is the shared component catalog. Its previews cover compact light mode, expanded dark mode and 2x font scale. New reusable components should be represented in the catalog before feature modules depend on them.

## New features

`tools/new_feature.py` creates a feature already wired to shared YUI and shared strings. CI runs `verify_yui_single_source.py` to enforce:

- generated token freshness,
- active page-role behavior,
- adaptive/form/catalog APIs,
- shared YUI usage by all current modules,
- no new private UI systems,
- YFloat remaining a thin compatibility adapter.

## Cross-module row alignment and ARM64 Debug policy

On `rebuild/material3-full-ui`, YFloat's Android View settings are live and
use the shared `libs/yui/YViewFramework.kt` renderer. Navigation rows,
permission rows and ordinary settings rows use the **same YUI card inset**
from `YView.cardPadding`; menu reordering rows do not override it. Native
row labels wrap instead of enforcing one line.

The rebuilt Compose settings-group header uses
`LocalYAppearance.current.rowHorizontalPaddingDp`, the same appearance
parameter as `YListItem`. Existing YUI controls and dynamic color owners
remain untouched, rather than importing a second design system.

`tools/verify_yui_row_alignment.py` asserts these contracts and checks CI
workflow ownership. For this branch, only `:suite:assembleDebug` executes as
an APK build; its artifact is `YSuite-debug-arm64-v8a`. Other workflows may
still run source-only architecture checks. GitHub Action success is not a
substitute for large-font, narrow-screen and dark-theme device validation.

## Shared installed-app inventory and other cross-feature data sources

Android's installed-app inventory and label lookup must use
`com.yagay.suite.api.InstalledAppCatalog` rather than duplicate
`PackageManager.getInstalledApplications` calls in feature UI/repositories.
It caches flag-specific immutable snapshots by host package and locale,
invalidates on package-change broadcasts, and shares bounded icon bitmaps.
Callers perform their own UI/background dispatching. The source is located
in `libs/yapi` so both standalone features and the integrated suite use it.
YEntryCleaner still classifies updated system apps with its own policy;
Root-only component discovery and security-sensitive package queries
remain feature-owned and are **not** substituted with this ordinary inventory.

`tools/verify_shared_app_catalog.py` enforces source ownership and
checks consumers. Root, LSPosed, accessibility permission, and runtime state
use their existing host facade when appropriate, but heterogeneous
privileged operations are not forced into a single unsafe loader.

The same installed-app data source is also used by MiniGuard's launchable
target picker, YTaskManager's UID/process mapping, YFiles' installed APK
view (which retains its additional package/version lookup flags) and YNotify's
notification-history label/icon helper. Their feature-specific filtering,
UID joining, historical-uninstalled fallback, and Root/component enumeration
remain separate. Standard application labels and 64px icon bitmaps are cached
centrally rather than within each feature.
