# Feature migration: YFiles

Product surface: FileManager
Upstream project: SysAdminDoc/FileExplorer
Upstream license: MIT

## Scope

YFiles is a provider-based file manager with unified Local, Document/SAF, Root and ZIP Archive access.

The current implementation includes provider-routed browsing and search, create/rename/delete,
same-provider and streamed cross-provider copy/move, conflict handling, favorites/recent locations,
managed recycle bin, ZIP browse/create/extract, checksums, duplicate and directory analysis, text
and HEX tools, bulk rename, split/join, compare, cleanup analysis, chmod and symbolic-link support.

Remote SMB/SFTP/WebDAV remains a separate backend concern. The engine keeps a Remote provider kind
so a mature protocol implementation can be added without changing the file-manager product UI.

## Product UI

The product hierarchy is adapted from the MIT-licensed `SysAdminDoc/FileExplorer` project rather
than being invented as a YSuite-specific generic page. YFiles preserves the file-manager model:
locations drawer/pane, breadcrumb navigation, search, sort direction, selection-mode top bar,
long-press multi-select, file rows, inspector/details, compact bottom-sheet details, tools as
secondary file-manager pages and a new-folder primary action.

YSuite owns only the shared visual/integration layer: Material theme, typography, spacing, localized
strings, dialogs, Root/Hook/platform adapters and diagnostics. Feature code does not define its own
theme, layout XML, drawable UI kit or private design system.

Material Files and Amaze File Manager may still be used as behavioral references, but GPL source is
not copied into YSuite.

## Platform capabilities

Local access uses Android external storage through the Local provider.

SAF stores persistable document-tree grants transactionally. If local persistence fails after a
grant is taken, the grant is released again.

Root uses the shared `RootGateway`; YFiles never launches `su` directly. Both integrated YSuite and
the generic standalone host inject the Android Root adapter through the composition root.

Archive is a read-only ZIP virtual provider. Cross-provider transfer streams through provider
read/write contracts. Replace operations stage the new item and preserve the old destination until
promotion succeeds.

The recycle bin persists metadata synchronously, rolls back a move when metadata persistence fails,
prunes stale records and restores using the original file name with conflict-safe rename behavior.

## Localization

All user-facing YFiles text is maintained in English and Simplified Chinese resources with matching
keys. Product actions added by this migration, including sort direction and select-all, are localized
in both locales.

## Tests

The test suite covers provider registration, Local listing/search/root boundaries, engine-routed
create/rename/copy/move, cross-provider streaming, explicit target names, safe replace staging, ZIP
and file-tool behavior.

CI additionally runs architecture boundaries, localization parity, frozen foundation checks,
migration policy checks, Android lint, YFiles standalone smoke assembly and the integrated YSuite
Debug build.

## Standalone

YFiles uses the generic standalone host with `standaloneFeature=yfiles`.

Standalone enters the feature directly instead of showing the suite dashboard and receives the real
shared Android Root adapter through `YSuiteStandaloneDependencies`. The same feature UI and provider
engine are used in integrated and standalone builds.
