# Feature migration: YDownload

Product surface: DownloadManager
Upstream project: PBhadoo/QDM-Android
Upstream license: Apache-2.0

## Scope

YDownload is the YSuite download manager. It implements persistent HTTP/HTTPS download tasks,
metadata probing, public Download/YDownload output, global and per-task SAF destination folders,
Range-based resume, foreground background execution, queueing, search, state tabs, pause/resume/cancel/retry,
completion opening, task removal, batch URL import and clipboard import.

The add-download dialog can override the save folder per task and persists that choice with the
task. The integrated settings page controls the default save folder, maximum concurrent downloads,
global bandwidth limit, Wi-Fi-only mode, download notifications and default User-Agent. These values
are wired into the engine/runtime rather than being presentation-only settings.

## Product UI

The product hierarchy is adapted from QDM-Android: top app bar with search and settings access,
scrollable download-state tabs, task rows with progress/speed/ETA, state-dependent overflow actions
and a multi-action add button for URL entry, clipboard paste and text-file import.

Task actions include pause, resume, cancel, retry, open, share, copy link, open save folder,
properties, redownload and remove-task. Sorting by added time/name and clearing completed tasks are
also implemented.

YDownload settings use YSuite's Compose-Settings product surface. YSuite supplies the shared Material
theme, typography, spacing, shapes, localization, permission requester and host navigation.
YDownload does not define a private Theme/Color/Type system.

QDM's browser remains a separate Browser product concern in YSuite. QDM's current
Copy/Move/Rename dialog is an upstream placeholder, so YDownload does not claim it as a supported
feature. Scheduled-download data/worker scaffolding is also not exposed in QDM's current Add
Download UI and is therefore not presented as a completed YDownload function.

## Platform capabilities

Downloads use OkHttp and HTTP Range requests. Files are created through MediaStore under
Download/YDownload by default, or through a persisted SAF tree selected in YDownload settings.

Active transfers run through a dataSync foreground service with a partial wake lock. Per-task
progress/result notifications can be disabled in settings while the Android-required foreground
service notification remains. Android 13+ notification permission is requested through the shared
YSuite permission requester.

The engine enforces maximum concurrent downloads, auto-pumps queued tasks when capacity opens,
supports a shared global bandwidth limiter, and prevents Wi-Fi-only downloads from starting on
non-Wi-Fi networks. Download tasks are persisted in a module-owned SQLite store so process
recreation does not lose state.

The implementation keeps QDM's Repository -> Engine -> Foreground Service -> ViewModel -> Product UI
separation but uses the YSuite composition root instead of Hilt.

QDM exposes a thread-count preference for segmented downloads. YDownload deliberately does not
expose a non-functional thread-count control: safe multi-connection resume requires persistent
per-chunk offsets, which the current YDownload engine does not yet implement.

## Localization

All user-facing YDownload strings are available in English and Simplified Chinese with matching
keys. Settings, task actions, notification channel names and properties use localized resources.

## Tests

Pure model filtering and filename sanitization are covered by JVM tests. Architecture checks enforce
that feature code does not import Material3, define raw product styling or bypass the shared UI
layers. Final CI runs localization parity, migration rules, unit tests, Android lint, YFiles
standalone smoke, YDownload standalone smoke and the integrated YSuite Debug build.

## Standalone

YDownload is registered in the generic standalone host as `standaloneFeature=ydownload`.
Standalone uses the same product UI, settings repository, persistent task repository, download
engine, foreground service and resources as the integrated YSuite build.
