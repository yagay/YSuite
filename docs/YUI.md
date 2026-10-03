# YUI design-system contract

YUI is the single normal-screen UI layer for YSuite and standalone feature builds.

## Rules

- Feature modules own business state and business actions. `libs/yui` owns normal-screen theme, spacing, shapes, typography, system bars, page chrome and reusable interaction patterns.
- New features must not create feature-local `AppUi`, `UiTokens`, `DesignTokens`, `AppTheme` or `FeatureTheme` systems.
- Compose features start with `YPageScaffold` or one of its role wrappers: `YSettingsScaffold`, `YListScaffold`, `YManagerScaffold`, `YBrowserScaffold`, `YDashboardScaffold`, `YDetailScaffold`, `YTimelineScaffold`, `YLogScaffold`, `YEditorScaffold`, or `YWizardScaffold`.
- Ordinary content uses `YPageList`, `YSectionHeader`, `YListItem`, `YSwitchItem`, `YCheckboxItem`, `YStatusItem`, `YProgressItem`, `YActionGroup`, `YFilterBar`/`YTabBar`, `YSelectionBar`, `YNotice`, and `YLogPanel` before creating custom normal-screen components.
- Java/View and XML modules consume `YView`, `YViewLayout`, `Widget.YUI.*`, `TextAppearance.YUI.*`, and `@dimen/yui_*`. Their normal-screen geometry must follow the same YUI tokens as Compose.
- Existing `YFeature*` APIs are compatibility wrappers. They remain supported while older screens migrate, but new modules are generated with YUI 2.0 APIs only.
- Specialized overlay windows, capture surfaces, OCR selection layers, and high-contrast diagnostic consoles may use task-specific rendering when normal Material surfaces would be inappropriate. Their settings/control pages still use YUI.

## Page-role guidance

- **SETTINGS** — preferences, capability toggles, provider/Hook options.
- **LIST** — ordinary searchable/filterable collections.
- **MANAGER** — long-running jobs such as downloads, tasks, notifications, processes.
- **BROWSER** — hierarchical content such as files or component trees.
- **DASHBOARD** — live status, health and diagnostics summaries.
- **DETAIL** — one entity with properties and actions.
- **TIMELINE** — event/history streams.
- **LOG** — diagnostics and log streams.
- **EDITOR** — rule/configuration editing.
- **WIZARD** — ordered setup/creation flows.

A module may combine roles through tabs or navigation. Uniformity means the same visual language and interaction grammar, not forcing unrelated features into the same screen layout.

## New features

`tools/new_feature.py` creates a feature already wired to shared YUI and shared string resources. CI runs `verify_yui_single_source.py` to prevent new private normal-screen UI systems from entering the monorepo.
