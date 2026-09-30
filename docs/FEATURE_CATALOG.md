# Feature Catalog

`config/features.toml` is the only human-edited inventory of YSuite features.

Do not manually register a feature in `FeatureRegistry`, `SuiteXposedModule`, root `settings.gradle.kts`, or `suite/YSuite/build.gradle.kts`.

## Adding a feature

1. Add one `[[feature]]` entry to `config/features.toml`.
2. Add the feature source module at the declared `project_dir`.
3. Run:

```bash
python3 tools/generate_feature_catalog.py
```

The generator updates:

- `libs/ycore/.../GeneratedFeatureCatalog.kt` for runtime/UI registration;
- `suite/YSuite/.../GeneratedXposedPlugins.java` for logical Xposed plugins;
- `config/generated/feature-modules.tsv` for Gradle module inclusion and YSuite dependencies.

CI runs `python3 tools/generate_feature_catalog.py --check` and rejects stale generated files.

## Catalog fields

Each feature declares its identity, description, Gradle module/path, entry Activity, optional runtime, shared capabilities, lifecycle mode, optional shared-service bridges, optional boot/IPC handlers, and logical Xposed hooks.

`requiresRoot` is derived from the `ROOT` capability. `requiresHook` is derived from whether the feature declares any hooks. New metadata should be added to the catalog and generator rather than creating another parallel registry.

## Removing a feature

Remove its `[[feature]]` block, regenerate, and then remove its source directory when no other code depends on it. The generated runtime registry, Xposed table, Gradle settings, and YSuite dependency set will all update together.
