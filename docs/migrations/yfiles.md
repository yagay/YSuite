# Feature migration: YFiles

## Scope

First clean-room slice:
- primary storage entry,
- directory navigation,
- parent navigation,
- local search,
- recursive search with a result limit,
- hidden-file filtering,
- name / modified / size / type sorting,
- ascending / descending order,
- multi-selection state,
- file/folder properties,
- shared logging,
- integrated and standalone builds.

Later slices add file mutations, recycle bin/favorites/duplicates/maintenance tools, then Root and
DocumentsUI/SAF enhancement through shared platform contracts.

## Clean-room implementation

The old YFiles implementation was inspected only to inventory observable behaviour and user-facing
requirements. The new implementation defines new API models, repository contracts, ViewModel state
and UI structure under the YSuite Next architecture.

No old activity, repository, page layout, hook implementation or utility source is transplanted.

## UI

The feature uses shared YSuite page contracts and design-system components only. It does not own a
theme, spacing system or Material3 controls.

## Platform capabilities

This first slice uses only normal file-system access available to the host and the shared logging API.

Root, LSPosed, special all-files access and SAF/DocumentsUI integration are intentionally deferred.

## Localization

All user-facing text is defined in English and Simplified Chinese resources with matching keys.

## Tests

The repository has unit coverage for directory-first sorting and recursive search.

## Standalone

YFiles is registered with the generic standalone host as standaloneFeature=yfiles.
