# Standalone builds

The standalone host is generic and contains no feature-specific screen source.

Supported features are declared in `config/standalone-features.properties`.

Each row contains:

`id | impl module | UI registration object | application id`

Build the default template:

```
gradle :host:standalone:assembleDebug
```

Build another registered feature:

```
gradle :host:standalone:assembleDebug -PstandaloneFeature=settings
```

At build configuration time the host:
1. selects exactly one feature implementation dependency,
2. assigns that feature's standalone application id,
3. writes the registration object's class name into BuildConfig.

At runtime the host loads that internal, repository-controlled Kotlin object and casts it to
`YSuiteFeatureUiRegistration`. No generated Kotlin source or feature-specific host source is needed.
