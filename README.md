# YSuite

YSuite is the unified host for my Android/LSPosed projects.

Each feature remains in its own repository and exposes a reusable `feature` module. The same business source is therefore built both by the standalone APK and by YSuite; YSuite does not copy app source trees or maintain long-lived adapter mirrors.

Shared UI is also single-source. `YSuite/ui` is the only YUI implementation. YSuite uses it as the local `:ui` project, while standalone projects resolve the same module directly from `YSuite/main` with Gradle `sourceControl`. There is no separate JitPack/AAR release path to keep in sync.

## Source-of-truth requirements

Future refactors must check these files before changing shared or feature behavior:

- `docs/PRODUCT_REQUIREMENTS.md` — non-negotiable YSuite product/maintenance rules
- `docs/MODULE_REQUIREMENTS.md` — preserved behavior for each included project
- `docs/ARCHITECTURE.md` — simple unified architecture and migration rules

Compiling successfully is not enough if a change violates those requirements.

## Included features

- YEntryCleaner
- YNotify
- YDiag
- YPower
- YMiniGuard
- YNFC
- YTaskManager
- YParam
- YFloat

## Structure

- `ui/` — shared YUI design system, Activity/window shell, Compose theme/components, common UI dependency versions
- `suite/` — unified APK host (`com.yagay.YSuite`), launcher UI and unified diagnostics/permission entry
- `core/` — small shared host layer for feature registry, host switches, Root status, host logging, crash attribution and stable contracts
- `features/` — feature repositories as Git submodules

Integrated projects use the same pattern wherever practical:

```text
project/
├── app/      standalone APK shell
└── feature/  reusable business/UI/runtime module
```

YFloat additionally keeps `ppocr-sdk/` as its own reusable module.

Standalone projects reference YUI with Gradle source dependency:

```kotlin
sourceControl {
    gitRepository(uri("https://github.com/yagay/YSuite.git")) {
        producesModule("com.github.yagay.YSuite:ui")
    }
}
```

and depend on `com.github.yagay.YSuite:ui` using the `main` branch. A source-dependency checkout has no recursive feature submodules, so YSuite automatically configures only `:ui`; a normal YSuite checkout with all submodules configures the complete host.

YSuite host switches control whether a feature entry/runtime is active inside YSuite. LSPosed Hook activation and target scope remain explicit so the host does not introduce hidden cross-process behavior.

Unified diagnostic exports are written to `Download/YSuite/`, with full-suite and per-module diagnostic ZIP export.
