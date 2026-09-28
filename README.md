# YSuite

YSuite is the unified host for my Android/LSPosed projects.

The repository keeps the suite host intentionally small. Feature implementations remain in their own repositories and are included here as Git submodules, so the same source can be built both as a standalone APK and as part of YSuite.

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

- `suite/` — unified APK host (`com.yagay.YSuite`)
- `core/` — shared feature switches, Root access and unified logging
- `features/` — feature repositories as Git submodules

The unified app centralizes common host behavior while each standalone project keeps its own package, release process and APK shell.
