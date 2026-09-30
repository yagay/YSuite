# Feature Catalog

`config/features.toml` is the only human-edited inventory of YSuite features.

Do not manually register a feature in `FeatureRegistry`, `SuiteXposedModule`, root `settings.gradle.kts`, or `suite/YSuite/build.gradle.kts`.

## What the catalog drives

Running:

```bash
python3 tools/generate_feature_catalog.py
```

generates and keeps these consumers synchronized:

- `libs/ycore/.../GeneratedFeatureCatalog.kt`: runtime feature metadata and exact IPC action ownership;
- `suite/YSuite/.../GeneratedXposedPlugins.java`: logical LSPosed plugin entries behind the single YSuite Xposed host;
- `config/generated/feature-modules.tsv`: Gradle feature includes/dependencies;
- `config/generated/host-replaced-components.tsv`: standalone Android components replaced by host-owned YSuite components.

`tools/plan_standalone_ci.py` and `tools/sync_version_catalog.py` also read the catalog directly. Adding or removing a feature therefore updates standalone CI selection and version-catalog mirroring without another app list.

## Add a normal feature

For a new feature that does not have Hook code yet, use the scaffold helper:

```bash
python3 tools/new_feature.py \
  --id ysample \
  --name YSample \
  --package com.yagay.ysample \
  --description "示例功能" \
  --capabilities ROOT
```

The helper creates the standard independently buildable `app + feature` structure, a `ManagedFeatureRuntime`, a minimal YUI screen, a mirrored version catalog, and the catalog entry. It then regenerates catalog outputs.

Use `--dry-run` to preview paths and the TOML entry without changing files.

## Add Hook support later

Do not create an empty Hook only to satisfy registration. Implement the real Hook class first, then add its namespaced entry to the feature:

```toml
hooks = [
  { id = "ysample/main", class = "com.yagay.ysample.hook.YSampleModule" },
]
```

`requiresHook` is derived automatically from whether `hooks` is non-empty.

## Host-replaced Android components

Standalone APKs may own Android special components that must not remain independently registered when embedded in YSuite. Declare each such component in the feature entry:

```toml
replaced_components = [
  { type = "receiver", class = "com.yagay.ysample.SampleReceiver" },
  { type = "provider", class = "com.yagay.ysample.SampleProvider" },
]
```

Allowed types are `activity`, `service`, `receiver`, and `provider`.

CI verifies all three sides agree:

1. the component exists in the standalone feature manifest;
2. it is declared in `config/features.toml`;
3. the YSuite manifest contains the matching `tools:node="remove"` entry.

A catalog `boot_receiver` and every `ipc_routes[].receiver` must also appear in `replaced_components`; CI rejects omissions.

## IPC routing

Each exported logical route is declared once:

```toml
ipc_routes = [
  { action = "com.yagay.ysample.EVENT", receiver = "com.yagay.ysample.SampleReceiver" },
]
```

The generator creates a constant-time action index. `SuiteBridgeReceiver` resolves an action through that generated index and dispatches only to the owning enabled feature.

## Required checks

Before merging feature-registration changes, run:

```bash
python3 tools/generate_feature_catalog.py --check
python3 tools/sync_version_catalog.py --check
python3 tools/verify_feature_catalog.py
python3 tools/verify_feature_state_gating.py
```

GitHub Actions runs these checks again, smoke-tests the feature scaffold, and builds affected standalone apps plus YSuite.

## Remove a feature

Remove its `[[feature]]` block and source directory, regenerate the catalog, then remove only host wrappers that are genuinely no longer referenced. Do not manually edit generated Kotlin, Java, or TSV files.
