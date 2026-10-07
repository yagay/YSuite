# Standalone builds

The standalone host is generic and contains no feature-specific screen source.

Supported features are declared in `config/standalone-features.properties`.

Each row contains:

`id | impl module | UI registration object | application id | runtime modules | Xposed init classes | default scopes`

List-valued fields use `;` as the separator. Non-hook features leave the last three fields empty.

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
2. adds any runtime modules required by that standalone feature,
3. assigns that feature's standalone application id,
4. writes the registration object's class name into BuildConfig,
5. generates API-102 Xposed metadata for hook-enabled standalone features.

At runtime the host loads that internal, repository-controlled Kotlin object and casts it to
`YSuiteFeatureUiRegistration`. No generated Kotlin source or feature-specific host source is needed.

For hook-enabled standalone features, CI must also validate that the produced APK really contains
`META-INF/xposed/java_init.list`, `module.prop`, and `scope.list`. A successful Gradle assemble by
itself is not considered proof that LSPosed functionality was packaged.
