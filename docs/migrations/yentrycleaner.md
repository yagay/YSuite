# Feature migration: YEntryCleaner

Product surface: EntityManager
Upstream project: LibChecker/LibChecker
Research reference: MuntashirAkon/AppManager
Upstream license: Apache-2.0 for the primary layout reference

## Scope

Reimplements List Cleaner as YEntryCleaner with Root + LSPosed and no IFW: share/open-with cleanup,
browser-host rules, priority ordering, historical/restricted candidates, independent locks,
system/user filtering, QS Tiles, shortcuts and widgets, persistent disabled-component protection and
Root state verification.

## Product UI

Uses YEntryCleanerWorkspace with a mature app/entity collection and detail pane. Surface/category
selection, search, filters, locked state and component controls remain YEntryCleaner-specific.

## Platform capabilities

Root component changes use RootGateway and are verified against PackageManager state. Resolver and
component-guard runtime configuration uses HookGateway. API-102 resolver/component hooks live only
in feature/yentrycleaner/runtime.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

Integrated and standalone builds compile the same runtime. Root mutations require command success
plus observed component state; locked items are skipped by bulk operations.

## Standalone

The generic standalone host injects the same Root/Hook gateways and merges the same API-102 runtime.
