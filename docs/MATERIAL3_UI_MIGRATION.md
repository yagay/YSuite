# Material 3 UI migration

This branch replaces YSuite's bespoke visual component system with the upstream open-source AndroidX Compose Material 3 component implementation.

Upstream: https://github.com/androidx/androidx/tree/androidx-main/compose/material3

## Source of truth

- Use `androidx.compose.material3` for themes, buttons, dialogs, inputs, lists, navigation, and overlays.
- Keep `YTheme` as a source-compatible adapter for current feature modules, not as a second implementation. Its default color palette is shared with legacy XML themes. Dynamic colors require explicit opt-in.
- Keep the product-specific layouts (file manager, download manager, log viewer, NFC, etc.) while replacing their common controls with upstream primitives.
- Use the official Material 3 Typography and Shapes rather than inventing per-feature font sizes and shapes.
- Legacy View screens use Material 3 XML themes during migration; they must ultimately become Compose screens. Do not recursively repaint/re-size View hierarchies at runtime.
- Reuse `YDialogConfirmButton` and `YDialogDismissButton` for dialog actions; do not introduce per-feature modal button implementations.

## Migration status

Foundation migration in progress. This first commit unifies the Compose theme defaults, removes the global View tree restyler and dynamic-color mismatch, and consolidates shared dialog actions and setting rows. Other legacy feature-specific screens still require direct conversion; **this is not yet a full application-wide UI replacement**.

## Validation targets

- Compare colors, typography, dialogs, input fields, switches, buttons, lists, and settings side-by-side in light and dark modes.
- Audit legacy View and feature-local Compose implementations for unmanaged hard-coded colors, independent styles and mismatched input controls.
- Build `:suite:assembleDebug` and exercise the existing UI catalog before merging to `main`.
