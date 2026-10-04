# Design System

YSuite has one visual language but more than one page shape.

## Ownership

- `core:designsystem/theme`: color scheme, typography, shapes, spacing and layout tokens.
- `core:designsystem/component`: reusable visual controls.
- `core:ui`: adaptive window model, host navigation integration, page shells and page/state patterns.

Feature modules must not create their own Material theme, spacing scale, color palette, button family, dialog family or competing root shell system.

## Shared primitives

- `YSuiteTheme`
- `YSuiteSpacing`
- `YSuiteShapes`
- `YSuiteLayoutTokens`
- `YSuiteSection`
- `YSuiteListItem`
- `YSuiteSwitchItem`
- `YSuitePrimaryButton`
- `YSuiteSecondaryButton`
- `YSuiteDivider`
- `YSuiteStateHost`

## Shared application UI

- `YSuiteAdaptiveLayout` and `YSuiteAdaptiveInfo`
- `YSuiteAppShell`
- `YSuiteStandardTopBar`
- `YSuiteHostNavigationButton`
- Dashboard / Manager / Browser / Tool / Settings / Detail / Fullscreen shells

The goal is consistency of design language and behavior, not identical geometry. A browser should look like a browser, a file manager should look like a manager, and a settings page should remain readable, while all of them still use the same YSuite visual system.
