# Settings

`core:settings` owns application-level preferences.

The first implementation uses AndroidX DataStore Preferences and exposes only the
`AppSettingsRepository` contract to consumers.

Stored foundation preferences:
- theme mode,
- application language tag.

Feature-specific settings should remain inside the feature unless two or more features genuinely
share the same preference.
