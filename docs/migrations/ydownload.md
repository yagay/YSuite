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
global bandwidth limit, Wi-Fi-only mode, network-return auto-resume, download notifications and
default User-Agent. These values are wired into the engine/runtime rather than being presentation-only
settings. Interrupted transfers remain resumable; removing an unfinished task cleans up its partial
destination while clearing completed tasks keeps the finished files.

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

QDM's integrated browser and browser ad-blocking remain intentionally outside YDownload. The rest
of the download-manager feature set is implemented in YDownload, including persisted segmented
downloads, per-task connection count and speed limit, custom HTTP headers, scheduled downloads,
SAF destinations and share-to-download URL capture. QDM's current Copy/Move/Rename dialog is an
upstream placeholder rather than a working download feature, so it is not presented as supported.

## Platform capabilities

Downloads use OkHttp and HTTP Range requests. Files are created through MediaStore under
Download/YDownload by default, or through a persisted SAF tree selected in YDownload settings.

Active transfers run through a dataSync foreground service with a partial wake lock. Per-task
progress/result notifications can be disabled in settings while the Android-required foreground
service notification remains. Android 13+ notification permission is requested through the shared
YSuite permission requester.

The engine enforces maximum concurrent downloads, auto-pumps queued tasks when capacity opens,
supports both global and per-task bandwidth limits, and prevents Wi-Fi-only downloads from starting
on non-Wi-Fi networks. Multi-connection tasks persist every chunk range and completed byte count so
pause, network interruption and process recreation resume each chunk from its own saved offset.
Servers or destinations that cannot safely support segmented random-access transfers fall back to a
single connection rather than risking file corruption.

Scheduled tasks are persisted and dispatched with Android JobScheduler. Incoming HTTP/HTTPS links
and text/plain URL shares are accepted by a lightweight capture activity, persisted, and routed to
the YDownload add dialog in either the integrated YSuite host or the standalone YDownload build.
Download tasks are stored in a module-owned SQLite database so process recreation does not lose
state.

The implementation keeps QDM's Repository -> Engine -> Foreground Service -> ViewModel -> Product UI
separation but uses the YSuite composition root instead of Hilt.

QDM's thread-count feature is implemented with an additional safety layer: YDownload persists
per-chunk offsets instead of reconstructing progress from one aggregate byte counter. The default
connection count is configurable from 1 to 16 and each task can override it. The add-download
dialog also exposes QDM model capabilities that were not fully surfaced in the upstream UI:
per-task speed limit, custom headers and scheduled start time.

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
