# Module product layouts

YSuite modules keep their own business behaviour. Open-source projects are used only for product
page structure: information hierarchy, navigation, list/detail organization, filters, status
placement and responsive pane behaviour.

No module may replace YSuite Root, LSPosed/Hook, diagnostics, persistence or domain rules with an
upstream implementation merely to obtain its UI.

| Feature | Surface | Workspace | Primary layout reference | YSuite behaviour retained |
| --- | --- | --- | --- | --- |
| YEntryCleaner | EntityManager | `YEntryCleanerWorkspace` | LibChecker | share/open-with cleanup, components, priority, locks and system/user separation |
| YDiag | LogViewer | `YDiagWorkspace` | LogcatReader | selected-app diagnostics, Root/Hook evidence and structured export |
| YNotify | LogViewer | `YNotifyWorkspace` | LogcatReader | notification/Toast/heads-up/bubble/popup history and reclassification |
| YPower | EntityManager | `YPowerWorkspace` | LibChecker | Root enhancements, Hook simulation, runtime detection and recommendations |
| YMiniGuard | EntityManager | `YMiniGuardWorkspace` | LibChecker | protected apps, OPlus mini-window keepalive and media controls |
| YNFC | Tool | `YNfcWorkspace` | NFCGate | access-card profiles, one-tap switching, payment-safe HCE routing and recovery |
| YTaskManager | TaskManager | `YTaskManagerWorkspace` | RohitKushvaha01/TaskManager | processes/resources/network, Root controls and per-app traffic |
| YParam | EntityManager | `YParamWorkspace` | LibChecker | per-app DPI/display/language/location/window/Web overrides and restore-default |
| YFloat | Settings | `YFloatWorkspace` | Compose-Settings | floating actions, OCR/selection, gestures, appearance and Hook-assisted capture |

GPL-family references are research-only: App Manager, LogFox, LuckyTool, DPIS and
XposedAppSettings. Their source is not copied into this branch. TextSnip is MIT and is a secondary
layout reference for YFloat's floating OCR/selection settings.

A migrated feature listed in `config/feature-product-layouts.json` must declare the configured
`ProductSurfaceKind` and call the configured feature-specific workspace. CI rejects a migrated
feature that falls back to a different generic page family.
