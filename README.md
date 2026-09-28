# YSuite

YSuite is the unified host for my Android/LSPosed projects.

Each feature remains in its own repository and exposes a reusable `feature` module. The same business source is therefore built both by the standalone APK and by YSuite; YSuite no longer copies app source trees or maintains adapter mirrors.

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

- `suite/` — unified APK host (`com.yagay.YSuite`) and its own `YSuiteApp`
- `core/` — small shared host layer for feature registry, host switches, Root status and host logging
- `features/` — feature repositories as Git submodules

Integrated projects use the same pattern wherever practical:

```text
project/
├── app/      standalone APK shell
└── feature/  reusable business/UI/runtime module
```

YFloat additionally keeps `ppocr-sdk/` as its own reusable module.

YSuite host switches control whether a feature entry/runtime is active inside YSuite. LSPosed Hook activation and target scope remain managed by LSPosed so the host does not introduce a second cross-process hook-control protocol.
