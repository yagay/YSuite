# Feature migration: YFiles

## Scope

YFiles was restarted from a clean source baseline and rebuilt as a provider-based file manager.

The rebuilt implementation now includes:
- unified Local, Document/SAF, Root and ZIP Archive providers,
- a provider registry and central file engine,
- same-provider and cross-provider copy/move,
- streamed cross-provider file transfer with progress,
- create, rename, delete and batch operations,
- search, recursive search, hidden-file filtering and sorting,
- favorites and recent locations,
- managed recycle bin with restore/empty,
- ZIP browsing, creation and extraction,
- SHA-256, duplicate scan and directory analysis,
- UTF-8 text editing and HEX preview,
- bulk rename,
- split/join,
- file comparison,
- cleanup analysis,
- Linux chmod and symbolic-link support where the provider exposes those capabilities,
- all-files-access settings entry and persistent SAF tree grants,
- integrated and standalone builds.

Remote SMB/SFTP/WebDAV is intentionally not embedded in this clean-room slice because it requires
separate protocol dependencies and credential models. The engine already reserves the Remote
provider kind for a future backend without changing the YFiles UI.

## Clean-room implementation

The earlier YFiles implementation on this branch was deleted completely before this rewrite.

Material Files and Amaze File Manager were used only as architectural and behavioural references.
No GPL source code was copied into YSuite.

The YFiles engine, provider interfaces, Local/SAF/Root/Archive providers, transfer coordinator,
maintenance tools, state models and UI binding are independently written for YSuite.

## UI

YFiles does not own a separate UI framework.

Feature-owned code only composes YFiles business state. All visible controls, page structure,
dialogs, status badges, list rows, filters and search fields come from `core:ui` and
`core:designsystem`.

Local, SAF, Root and Archive use the same YFiles screen. CI rejects YFiles-owned themes, widget
libraries, layouts, colors, dimensions, shapes and styles.

## Platform capabilities

Local access uses the Android storage surface available to the host.

SAF uses persisted document-tree URI grants.

Root uses the shared `RootGateway`; YFiles never launches `su` directly. The Android host provides
the Root adapter in the composition root.

Archive is a read-only ZIP virtual provider. Archives from non-local providers are materialized into
the app cache through the engine and then mounted.

Cross-provider copy/move streams through provider read/write contracts.

## Localization

All user-facing YFiles text is provided in English and Simplified Chinese resources with matching
keys.

## Tests

The test suite covers:
- provider registration,
- Local provider listing, recursive search and root boundary,
- engine-routed create/rename/copy/move,
- cross-provider streaming using test providers,
- ZIP archive mount/read,
- checksum, text/HEX and directory-analysis utilities.

CI also runs architecture, foundation-freeze, migration, localization and standalone checks.

## Standalone

YFiles uses the generic standalone host with `standaloneFeature=yfiles`.

The generic standalone host does not inject the Android Root adapter, so Root reports unavailable in
that host. The integrated YSuite app injects the real shared Root adapter. All non-Root YFiles
features remain available in standalone.
