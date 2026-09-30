# YSuite UI Architecture

YSuite uses one visual/layout contract even though feature code currently contains Compose, programmatic Java/Kotlin Views, and XML/ViewBinding screens.

## Ownership rule

Feature modules own business state, navigation targets, validation and actions. `libs/yui` owns normal-screen window policy, system insets, theme, typography, spacing, cards, buttons, setting rows, status presentation and standard empty/loading/error states.

Normal feature screens should not introduce their own screen padding, card radius, status palette or button height when YUI already provides the same concept.

Overlay, transparent and probe Activities may keep custom window behavior and can use `YUiWindowOptOut` where appropriate.

## Compose screens

Normal Compose Activities use `YComposeActivity` and the `YFeature*` surface:

- `YFeatureScaffold` for the normal shared top bar;
- `YFeatureCustomScaffold` when the top bar carries real interaction state such as an expanded search field;
- `YFeatureList`;
- `YFeatureSectionHeader` for consistent section hierarchy;
- `YFeatureCard` for normal information/settings groups;
- `YFeatureEmpty` for inline empty results;
- `YFeatureStat` for compact counters/summary metrics;
- `YStatusRow` / `YStatusPill` for status and metric presentation;
- `YSettingSwitch`;
- `YSearchField`, including optional leading/trailing actions;
- `YNavigationRow`;
- `YPrimaryButton` / `YSecondaryButton`;
- `YPageState` for Ready / Loading / Empty / Error.

`YFeatureCustomScaffold` is an exception surface, not an escape from YUI. YUI still owns safe-drawing insets, page-state rendering, bottom navigation, snackbars and FAB placement; the feature only supplies the stateful top-bar content.

The older `YPlugin*` API remains source-compatible while existing screens are migrated. New normal screens should prefer `YFeature*`.

## Programmatic View screens

Java/Kotlin View Activities use `YViewLayout`:

- `install()` / `screen()` for vertically scrolling screens;
- `installFixed()` / `fixedScreen()` when a RecyclerView or another child owns scrolling;
- `card()`;
- `statusLine()` / `setStatus()`;
- `switchRow()`;
- `searchField()`;
- `primaryButton()` / `secondaryButton()`;
- `actionRow()` / `addAction()`;
- `detailBlock()`.

This keeps legacy View screens maintainable without forcing a risky Compose rewrite.

## XML / ViewBinding screens

XML layouts consume the same YUI resource contract:

- dimensions: `@dimen/yui_*`;
- buttons: `Widget.YUI.Button`, `.Tonal`, `.Outlined`;
- input: `Widget.YUI.TextInput`;
- text: `TextAppearance.YUI.SectionTitle`, `.Body`, `.Caption`.

Do not copy those numeric dimensions or parent Material styles into feature-local XML unless the screen intentionally needs a special visual treatment.

## Shared layout hierarchy

Use the same conceptual hierarchy across all UI technologies:

```text
Screen
  Section / Card
    Row
      Status / Setting / Navigation / Action
```

The hierarchy now applies to both entry shells and high-traffic content areas. YNFC status/card/log sections, YTaskManager process/resource/network sections, and YDiag monitor/history/configuration sections use the same YUI vocabulary while retaining their feature-specific behavior.

## Specialized surfaces

A feature may keep purpose-built rendering where a generic card would reduce usability. Examples include diagnostic log consoles, charts, overlays and drawing/selection surfaces. Their surrounding page structure, spacing, actions and normal status presentation should still use YUI.

YNFC intentionally keeps its log console high-contrast palette; normal NFC cards and status colors are YUI-owned.

## Migration policy

Migrate incrementally. A UI refactor should preserve business behavior and only replace layout/style ownership first. Convert a legacy View/XML screen to Compose only when there is a separate product or maintenance reason to do so.

A screen may keep a specialized console, overlay, drawing surface, stateful search toolbar or other product-specific interaction. The surrounding normal page shell should still use YUI where possible.

`tools/verify_ui_framework.py` protects migrated entry/content screens from reintroducing local palettes and verifies that Compose, View and XML YUI surfaces remain available.
