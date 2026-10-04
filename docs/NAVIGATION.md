# Navigation

Navigation is owned by the application composition root.

Features expose only:

- `FeatureRegistration`
- a `startRoute`
- the set of routes they own

Features never navigate directly into another feature implementation.

`BackStackNavigator` is the framework-neutral navigation state contract. A UI/navigation adapter can
be replaced later without changing feature APIs.
