# Design System

YSuite has one visual language and several purpose-built page families.

## Ownership

- `core:designsystem/theme`: color scheme, typography, shapes, spacing and layout tokens.
- `core:designsystem/component`: reusable visual primitives.
- `core:ui`: adaptive window model, host navigation integration, shells and common screen layouts.

Feature modules must not create a private Material theme, spacing scale, color palette, dialog family or competing root scaffold.

## Current visual primitives

- `YSuiteTheme`
- `YSuiteSpacing`
- `YSuiteShapes`
- `YSuitePanel`
- `YSuiteSectionLabel`
- `YSuiteDataRow`
- `YSuiteToggleRow`
- `YSuiteModuleTile`
- `YSuiteMetricTile`
- `YSuiteSegmentedControl`
- `YSuiteSearchBar`
- `YSuitePrimaryAction`
- `YSuiteActionButton`
- `YSuiteStatusPill`
- shared dialogs and state surfaces

The visual model is intentionally different from the old card-heavy UI: sections no longer wrap everything in one Card, data rows are independent surfaces with optional semantic icons and selection state, module navigation uses tiles, and dashboard metrics have their own presentation.

## Shared application UI

- `YSuiteAdaptiveLayout` and `YSuiteAdaptiveInfo`
- `YSuiteAppShell`
- `YSuiteStandardTopBar`
- Dashboard / Manager / Browser / Tool / Settings / Detail / Fullscreen shells

Consistency means common tokens, controls and behavior—not identical geometry across unrelated pages.
