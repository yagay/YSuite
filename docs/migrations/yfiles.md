# Feature migration: YFiles

## Scope

YFiles has been restarted from a clean source baseline.

The first rebuilt slice contains:
- a provider-neutral file reference and node model,
- a provider capability model,
- a provider registry,
- a central YFiles engine,
- a local-storage provider,
- directory browsing,
- local and recursive search,
- bounded provider-root navigation,
- basic provider-routed create / rename / delete / copy / move primitives,
- integrated and standalone builds.

Document/SAF, Root, Archive and Remote providers are intentionally separate future backends.

## Clean-room implementation

The previous YFiles implementation on this branch was removed completely before this rewrite.

Material Files and Amaze File Manager are used only as architectural and behavioural references.
No source code from those GPL projects is copied into YSuite.

The new YFiles contracts, engine, registry, local provider and UI were written for the YSuite
architecture.

## UI

The UI only consumes `YFilesEngine`. It does not know whether a node comes from a local,
Document/SAF, Root, Archive or Remote provider.

Screens use shared YSuite pages and design-system components. The feature owns no Material3 widgets,
theme, spacing, colors or shapes.

## Platform capabilities

The initial provider is Local storage.

The engine model reserves distinct provider kinds for:
- Document / SAF,
- Root,
- Archive,
- Remote.

Root and Android-specific privileged capability plumbing will be injected through shared YSuite
platform contracts rather than embedded into the UI.

## Localization

English and Simplified Chinese resources are maintained together with matching keys.

## Tests

Unit tests cover:
- duplicate provider registration,
- provider descriptor exposure,
- directory-first local listing,
- recursive search,
- provider-root navigation boundary,
- engine-routed file operations.

## Standalone

YFiles uses the generic standalone host with `standaloneFeature=yfiles`.
