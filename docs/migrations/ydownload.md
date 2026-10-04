# Feature migration: YDownload

Product surface: DownloadManager
Upstream project: PBhadoo/QDM-Android
Upstream license: Apache-2.0

## Scope

YDownload is the YSuite download manager. It supports persistent HTTP/HTTPS download tasks,
metadata probing, public Downloads/YDownload output, Range-based resume, foreground background
execution, queueing, search, state tabs, pause/resume/cancel/retry, completion opening and task
removal.

## Product UI

The page hierarchy is adapted from QDM-Android: top app bar with search, scrollable download-state
tabs, task rows with progress/speed/ETA, state-dependent overflow actions and an add-download action.
YSuite supplies the shared Material theme, typography, spacing, shapes, localization and host
navigation. YDownload does not define a private Theme/Color/Type system.

## Platform capabilities

Downloads use OkHttp and HTTP Range requests. Files are created through MediaStore under
Download/YDownload. Active transfers run through a dataSync foreground service with a partial
wake lock and notification controls. Download tasks are persisted in a module-owned SQLite store so
process recreation does not lose state.

The implementation keeps QDM's Repository -> Engine -> Foreground Service -> ViewModel -> Product UI
separation but uses the YSuite composition root instead of Hilt.

## Localization

All user-facing YDownload strings are available in English and Simplified Chinese with matching
keys. Notification channel names and actions use the same resources.

## Tests

Pure model filtering and filename sanitization are covered by JVM tests. The final YSuite CI also
runs Android lint, the integrated app Debug build and a YDownload standalone smoke build.

## Standalone

YDownload is registered in the generic standalone host as `standaloneFeature=ydownload`. Standalone
uses the same product UI, persistent repository, download engine, foreground service and resources
as the integrated YSuite build.
