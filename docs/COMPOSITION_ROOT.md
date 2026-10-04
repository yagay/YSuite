# Composition root

The application module is the only place that constructs concrete shared services.

`YSuiteAppContainer` creates:
- DataStore application settings,
- Android logger,
- Android permission checker,
- platform Root/Hook service adapters,
- UI feature registrations.

Feature implementations receive only the interfaces they need.

No service locator or dependency injection framework is introduced at this stage. The dependency graph
is explicit, small and testable.
