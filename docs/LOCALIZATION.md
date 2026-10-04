# Localization

Localization is part of the architecture, not a later patch.

## Initial locales

- English: default `values/`
- Simplified Chinese: `values-zh-rCN/`

`core:resources` owns common strings and the centralized `LocaleController`.

## Rules

1. No user-visible hardcoded strings in Kotlin/Java.
2. Common actions use shared resources.
3. Feature-specific copy stays in the feature's resources.
4. All locale folders must contain the same required keys.
5. New languages require only resource additions and locale registration.
6. RTL support remains enabled at the application level.
