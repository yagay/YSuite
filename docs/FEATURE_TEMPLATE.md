# Feature template

Every production feature starts from:

```
feature/<name>/
  api/
  impl/
```

## api

Pure Kotlin only. It owns:
- feature id and route contract,
- public models needed by the composition root,
- no Android or Compose implementation.

## impl

Owns:
- ViewModel,
- feature-specific resources,
- repositories specific to that feature,
- screens built from `core:ui` page contracts and `core:designsystem`.

It must not:
- import Material3 directly,
- create another theme/page system,
- depend on another feature implementation,
- hardcode user-visible UI strings.

## Generator

Create a skeleton:

```
python tools/new_feature.py sample
```

Preview without writing:

```
python tools/new_feature.py sample --dry-run
```

Remove a generated feature:

```
python tools/remove_feature.py sample --yes
```

The framework-owned `template` and `settings` features cannot be removed by the removal tool.
