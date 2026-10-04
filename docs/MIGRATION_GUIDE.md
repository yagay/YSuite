# Clean-room feature migration

Old YSuite implementations are references for **behaviour and requirements only**. Their source,
page structure and local infrastructure are not copied into YSuite Next.

## Migration sequence

1. Inventory the old feature's observable behaviour.
2. Define a new pure-Kotlin `feature/<name>/api` contract.
3. Generate a clean `api/impl` skeleton with `tools/new_feature.py`.
4. Reimplement business behaviour against the new contracts.
5. Build screens exclusively from `core:ui` and `core:designsystem`.
6. Use platform/logging/permission APIs; Android adapters remain in the composition root.
7. Add English and Simplified Chinese resources together.
8. Add unit tests for business logic and state transitions.
9. Verify both integrated and standalone builds.
10. Only then register the feature in the main app.

## Prohibited during migration

Do not bring back:

- old YSuite source directories or package names,
- Android View/widget UI,
- feature-owned themes, colors, dp spacing or shapes,
- direct Material3 controls inside features,
- direct `su` / process execution,
- direct LSPosed/Xposed APIs,
- direct Android Log usage,
- feature-owned permission request flows,
- dependencies on another feature implementation.

## Required migration document

Every non-framework feature must have:

`docs/migrations/<feature>.md`

with these sections:

- Scope
- Clean-room implementation
- UI
- Platform capabilities
- Localization
- Tests
- Standalone

CI enforces this with `tools/verify_feature_migration.py`.
