# Upstream architecture policy

YSuite keeps one shared Android architecture and one Compose product UI layer. Mature open-source projects are used as upstream references for proven product structure and capability boundaries; feature implementations remain modular and independently buildable.

## YFiles

Primary upstream: `SysAdminDoc/FileExplorer` (MIT).

YSuite follows its provider/repository model: every storage backend is explicit, advertises supported operations, and is routed through one engine. The YFiles provider catalog currently distinguishes Local, SAF, Root, Shizuku, Archive, Network, Cloud, and smart Collection backends.

`dev2ex/twig` (GPL-3.0) was reviewed only as an architectural reference for a unified filesystem abstraction. No Twig source code is copied or linked into YSuite.

## Platform / System

`RikkaApps/Shizuku` (Apache-2.0) is used as the non-root privileged Android backend. Shizuku is owned by the shared platform layer rather than by any individual feature.

The common capability model is:

`Normal Android -> Shizuku -> Root -> LSPosed hooks`

Features can depend on the shared capability contracts and do not need to implement their own privilege detection.

`LibChecker/LibChecker` (Apache-2.0) remains the product reference for capability and application diagnostics. App Manager projects are feature research references only and are not copied into the codebase.

## Settings

Primary upstream: `alorma/Compose-Settings` (MIT).

All application and feature settings surfaces use the shared `core:productui/settings` components. Persistence remains feature-local unless a setting is genuinely shared by multiple features.

## Diagnostics and logs

The existing LogcatReader-inspired MIT UI remains the shared log workspace. YSuite now has a common log query/source model and a bounded Android Logcat collector.

`F0x1d/LogFox` (GPL-3.0) was reviewed only to identify mature diagnostic capabilities such as Logcat sources, filtering, Root/Shizuku access, crash and ANR workflows. No LogFox source code is copied or linked.

## Download manager

YDownload remains based on the QDM-Android product structure and YSuite's existing native segmented downloader. Gopeed and FluxDown are not part of the active branch.
