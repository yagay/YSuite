# YSuite Product Requirements

This file is a source-of-truth contract for future YSuite changes. Refactors are allowed and encouraged, but changes must preserve these requirements unless the owner explicitly changes them.

## Non-negotiable architecture rules

1. **One business source, two build modes.** Every project must remain independently buildable as its own APK, and the same feature/business source must also build inside YSuite. Do not maintain copied source trees or long-lived adapter mirrors.
2. **Unify aggressively, but keep real business differences.** Shared UI shell, theme, navigation patterns, Root access, LSPosed/Hook infrastructure, logging, diagnostics, status presentation, permissions helpers and common utilities should be reused wherever practical. Project-specific behavior stays inside the feature module.
3. **Standalone remains first-class.** A YSuite refactor must not make the standalone APK a second-class wrapper or require YSuite to work.
4. **YSuite uses one shared host runtime.** When combined, modules should reuse YSuite's shared Root/Hook/log/diagnostics infrastructure instead of starting parallel host frameworks. Standalone APKs may provide their own host implementation of the same contract.
5. **Simple over clever.** Prefer small interfaces, direct dependencies and obvious data flow. Avoid excessive abstraction, generated plumbing, duplicated bridges, complicated automation or dead compatibility layers.
6. **Easy module lifecycle.** Adding/removing a module should require a small, predictable set of changes: feature dependency, registry entry and Hook metadata when applicable. Removing a module must not require edits throughout unrelated modules.
7. **Feature switches are real switches.** Disabling a module must disable its YSuite UI/runtime work. Hook scope remains explicit and must not silently continue work that the host says is disabled.
8. **Restart only when technically required.** Ordinary UI/config/data changes should apply immediately. Request process/system restart only for changes that actually require Hook/runtime reloading.
9. **Unified diagnostics are mandatory.** Every included module must always have a YSuite log identity. Runtime start/failure, Root failures, Hook failures and uncaught crashes must be attributable to the module.
10. **Export location is fixed.** YSuite diagnostic ZIPs are exported under `Download/YSuite/`. Full-suite and per-module export are both supported.
11. **Crash diagnosis before guessing.** Module launch, active module and uncaught exception context must be logged so a click-to-crash can be diagnosed from the next exported ZIP.
12. **UI compatibility is shared infrastructure.** YSuite must provide a host theme compatible with Compose, AppCompat and Material-based feature Activities. A module must not crash simply because it is hosted by YSuite.
13. **Preserve project identity.** Package names, LSPosed module identity, scope metadata, providers/services and project-specific initialization must remain correct in both standalone and YSuite builds.
14. **YEntryCleaner replaces ListCleaner in YSuite.** Do not reintroduce the old ListCleaner path or compatibility alias in the unified host.
15. **Requirements-first maintenance.** Before changing shared source or a feature implementation, check this file and `MODULE_REQUIREMENTS.md`. A refactor is not complete if it compiles but violates an established requirement.

## Preferred project shape

```text
project/
├── app/       # standalone APK shell only
└── feature/   # reusable UI + business + runtime implementation
```

The `app` shell should contain only standalone-specific wiring: application ID, launcher manifest, standalone host services/providers as needed, theme entry, and dependency on `feature`.

YSuite should depend directly on `feature` modules and supply shared host services through stable contracts.

## Change checklist

Before merging a structural change:

- Does the standalone APK still build?
- Does YSuite still build with the same feature source?
- Did we accidentally duplicate Root, Hook, logging, navigation or diagnostics code?
- Can the module still be removed cleanly?
- Does disabling the feature stop its host runtime work?
- Does the change preserve the module requirements?
- Does a normal setting change avoid unnecessary restart?
- Can a crash/failure be identified from `Download/YSuite/` logs?
