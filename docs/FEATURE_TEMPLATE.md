# Feature template

Every production feature starts from the same shape:

```
feature/<name>/
  api/
  impl/
```

## api

Pure Kotlin only.

Owns:
- feature id and route contract,
- public models needed by the app composition root,
- no Android, Compose, Root or LSPosed implementation.

## impl

Owns:
- ViewModel,
- feature-specific strings,
- repository implementations specific to the feature,
- screens built only from `core:ui` page contracts and `core:designsystem` components.

It must not:
- import Material3 directly,
- create a second theme/page system,
- depend on another feature implementation,
- hardcode user-visible UI strings.

`feature:template:api` and `feature:template:impl` are compile-checked reference modules and are not
connected to the main YSuite app.
