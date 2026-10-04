# Feature migration: YFiles

## Scope

Clean-room functionality currently implemented:
- primary storage entry and directory navigation,
- parent navigation,
- local and recursive search with a result limit,
- hidden-file filtering,
- name / modified / size / type sorting,
- ascending / descending order,
- multi-selection,
- file/folder properties,
- create file and folder,
- rename,
- batch copy and move with a destination clipboard,
- move to managed recycle bin,
- restore and empty recycle bin,
- persistent favorite folders,
- persistent recent locations,
- shared logging,
- integrated and standalone builds.

Later slices add archive/hash/duplicate/maintenance tools, then Root and DocumentsUI/SAF enhancement
through shared platform contracts.

## Clean-room implementation

The old YFiles implementation is used only to inventory observable behaviour and user-facing
requirements. The new implementation defines new API models, repository contracts, operation state,
places persistence and UI structure under YSuite Next.

No old activity, repository, page layout, hook implementation or utility source is transplanted.

## UI

The feature uses shared YSuite page, dialog and design-system components only. Create/rename input is
provided by the shared `YSuiteTextInputDialog`; YFiles does not own Material3 controls, a theme,
spacing system, colors or shapes.

## Platform capabilities

The current slices use normal file-system access available to the host and the shared logging API.
Favorites and recent locations are feature-specific persistence behind `YFilesPlacesRepository`.

Root, LSPosed, special all-files access and SAF/DocumentsUI integration remain deferred and will be
added only through shared platform/permission contracts.

## Localization

All user-facing text is defined in English and Simplified Chinese resources with matching keys.

## Tests

Repository tests cover:
- directory-first sorting,
- recursive search,
- create / rename / copy / trash / restore,
- batch move.

The full CI also runs architecture, foundation-freeze, migration, localization, tooling and standalone
checks.

## Standalone

YFiles builds through the generic host with `standaloneFeature=yfiles` and uses the same repository,
ViewModel and screen implementation as the integrated app.
