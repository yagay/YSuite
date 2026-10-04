# Design System

The entire application has one visual language.

## Ownership

- `core:designsystem/theme`: color scheme, typography, shapes, spacing.
- `core:designsystem/component`: primitive controls.
- `core:ui`: complete page shells and reusable page patterns.

Feature modules must not create their own themes, spacing systems, color palettes, button styles,
cards, page scaffolds or dialog families.

## Initial primitives

- YSuiteTheme
- YSuiteSpacing
- YSuiteShapes
- YSuiteSection
- YSuiteListItem
- YSuiteSwitchItem
- YSuitePrimaryButton
- YSuiteSecondaryButton
- YSuiteDivider
- YSuiteAppShell
