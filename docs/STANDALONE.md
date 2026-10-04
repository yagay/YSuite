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

The host generates a tiny source bridge for the selected UI registration and depends only on that
selected feature implementation plus shared foundation modules.
