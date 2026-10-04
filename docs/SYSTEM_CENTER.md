# System center

The framework-owned System feature is the first real consumer of the clean YSuite foundation.

It displays live information from injected APIs:

- Root capability status,
- LSPosed hook capability status,
- permissions declared by the current host,
- registered diagnostics,
- bounded in-memory shared logs.

The feature depends only on pure/shared API modules. Android implementations are constructed by the
application composition root and injected into the feature.

The page also validates:
- unified page components,
- status badges,
- permission request bridge,
- feature lifecycle events,
- logging,
- diagnostic refresh,
- capability probing.

No legacy YSuite feature source is used.
