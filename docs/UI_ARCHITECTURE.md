# YSuite UI Architecture

YSuite uses one visual/layout contract even though feature code currently contains Compose, programmatic Java/Kotlin Views, and XML/ViewBinding screens.

## Ownership rule

Feature modules own business state, navigation targets, validation and actions. `libs/yui` owns normal-screen window policy, system insets, theme, typography, spacing, cards, buttons, setting rows, status presentation and standard empty/loading/error states.

Normal feature screens should not introduce their own screen padding, card radius, status palette or button height when YUI already provides the same concept.

Overlay, transparent and probe Activities may keep custom window behavior and can use `YUiWindowOptOut` where appropriate.

## Compose screens

Normal Compose Activities use `YComposeActivity` and the `YFeature*` surface:

- `YFeatureScaffold`
- `YFeatureList`
- `YFeatureCard`
- `YStatusRow` / `YStatusPill`
- `YSettingSwitch`
- `YSearchField`
- `YNavigationRow`
- `YPrimaryButton` / `YSecondaryButton`
- `YPageState` for Ready / Loading / Empty / Error

The older `YPlugin*` API remains source-compatible while existing screens are migrated. New normal screens should prefer `YFeature*`.

## Programmatic View screens

Java/Kotlin View Activities use `YViewLayout`:

- `install()` / `screen()`
- `card()`
- `statusLine()` / `setStatus()`
- `switchRow()`
- `searchField()`
- `primaryButton()` / `secondaryButton()`
- `actionRow()` / `addAction()`
- `detailBlock()`

This keeps legacy View screens maintainable without forcing a risky Compose rewrite.

## XML / ViewBinding screens

XML layouts consume the same YUI resource contract:

- dimensions: `@dimen/yui_*`
- buttons: `Widget.YUI.Button`, `.Tonal`, `.Outlined`
- input: `Widget.YUI.TextInput`
- text: `TextAppearance.YUI.SectionTitle`, `.Body`, `.Caption`

Do not copy those numeric dimensions or parent Material styles into feature-local XML unless the screen intentionally needs a special visual treatment.

## Shared layout hierarchy

Use the same conceptual hierarchy across all UI technologies:

```text
Screen
  Section / Card
    Row
      Status / Setting / Navigation / Action
```

This keeps spacing and information hierarchy predictable while allowing each feature to keep the UI technology that is safest for its existing code.

## Migration policy

Migrate incrementally. A UI refactor should preserve business behavior and only replace layout/style ownership first. Convert a legacy View/XML screen to Compose only when there is a separate product or maintenance reason to do so.

`tools/verify_ui_framework.py` protects migrated entry screens from reintroducing local palettes and verifies that Compose, View and XML YUI surfaces remain available.
