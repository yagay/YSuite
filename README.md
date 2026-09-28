# YSuite

YSuite is the unified host for my Android/LSPosed projects.

Each feature remains in its own repository and exposes a reusable `feature` module. The same business source is therefore built both by the standalone APK and by YSuite; YSuite does not copy app source trees or maintain long-lived adapter mirrors.

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

- `suite/` — unified APK host (`com.yagay.YSuite`), launcher UI, compatible host theme and unified diagnostics entry
- `core/` — small shared host layer for feature registry, host switches, Root status, host logging, crash attribution and stable contracts
- `features/` — feature repositories as Git submodules

Integrated projects use the same pattern wherever practical:

```text
project/
├── app/      standalone APK shell
└── feature/  reusable business/UI/runtime module
```

YFloat additionally keeps `ppocr-sdk/` as its own reusable module.

YSuite host switches control whether a feature entry/runtime is active inside YSuite. LSPosed Hook activation and target scope remain explicit so the host does not introduce hidden cross-process behavior.

Unified diagnostic exports are written to `Download/YSuite/`, with full-suite and per-module ZIP export.
