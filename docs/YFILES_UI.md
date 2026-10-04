# YFiles UI contract

YFiles does not own a UI framework.

The only feature-owned UI source is page composition and feature state binding. Visual primitives
must come from the shared YSuite UI stack:

```
feature:yfiles
  -> core:ui
  -> core:designsystem
  -> core:resources
```

YFiles may define:
- `YFilesFeatureScreen`,
- ViewModel/state,
- feature-specific labels in string resources,
- binding between file-engine state and shared YSuite components.

YFiles must not define:
- a YFiles theme,
- a YFiles component/widget library,
- feature-local colors, dimensions, shapes or styles,
- layout XML,
- feature-owned Material3 widgets,
- separate UI implementations for Local / SAF / Root / Archive / Remote providers.

All providers are rendered through the same YFiles screen using shared YSuite components.

CI enforces this rule in `tools/verify_feature_migration.py`.
