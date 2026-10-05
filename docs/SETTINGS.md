# Settings

`core:settings` owns application-wide preferences and `core:productui/settings` owns the single settings presentation language used across YSuite.

The persistence rule stays intentionally small:

- application-wide preferences live in `core:settings`;
- feature-specific preferences remain inside the feature unless two or more features genuinely share the same value;
- every settings page uses the shared Compose-Settings-derived components from `core:productui/settings` instead of creating feature-local switches, choice rows, sliders, or link rows.

Current application-wide preferences are:

- theme mode;
- dynamic Material color;
- application language tag.

Current feature settings following the same UI contract include YFiles and YDownload. This keeps feature persistence independent while making navigation, grouping, switches, choices and sliders visually and behaviorally consistent.
