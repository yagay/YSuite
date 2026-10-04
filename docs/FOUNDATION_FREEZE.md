# Foundation freeze

The clean YSuite foundation is now architecture-frozen.

This does **not** mean core code can never be fixed. It means the module topology and dependency
direction are treated as a stable platform contract.

## Frozen topology

`config/architecture-contract.json` is the source of truth for:

- the exact `core:*` module set,
- the allowed internal `core:*` dependency graph,
- framework-owned features,
- required root directories,
- forbidden legacy roots.

CI runs `tools/verify_foundation_contract.py` on every change.

Adding/removing a core module or changing a core-to-core dependency is therefore an explicit
architecture change, not an accidental side effect of feature work.

## What feature migration may change

Feature migration may:

- add `feature/<name>/api`,
- add `feature/<name>/impl`,
- add its migration document,
- register the feature in the app composition root,
- register it for standalone builds,
- add feature-specific tests/resources.

Feature migration must not reshape the foundation to make an old implementation fit.

## When foundation changes are justified

Change the architecture contract only when the requirement is genuinely shared by multiple
features or is a platform concern. A single migrated feature is not sufficient justification for
creating a parallel UI, logging, permission, Root or LSPosed system.
