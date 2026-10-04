# Feature registry

A feature has two registrations:

1. `FeatureRegistration` in the pure Kotlin API module.
2. `YSuiteFeatureUiRegistration` in the Android implementation module.

The application composition root owns the list of UI registrations.

The shared feature host provides:
- a registry-backed home surface,
- compact/medium modal navigation,
- expanded permanent navigation,
- one back behavior,
- one destination renderer.

Adding a feature does not require changing the navigation framework.
