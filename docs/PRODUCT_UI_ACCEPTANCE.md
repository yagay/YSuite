# Product UI acceptance rules

The design system and the product surface solve different problems. A feature is accepted only when its visible structure matches its declared product type.

## FileManager

Must provide a location/path model, file/folder content area and selection actions. Expanded layouts should support source and/or detail panes. File browsing must not be implemented as a generic settings/card feed.

## Browser

Must own an address/search surface and browser content. Tabs, toolbar and wide tab navigation belong to the browser workspace, not the host shell.

## Settings

Must use preference-oriented grouping and readable content width. It must not look like a file/task manager.

## Dashboard

Must prioritize overview/status/entry information and adaptive dashboard composition.

## LogViewer

Must provide log filters/search plus a log stream; expanded layouts may show event details.

## DownloadManager

Must prioritize active/history queues, progress, filtering and queue/selection actions.

## TaskManager

Must prioritize task state, filtering/grouping and task inspection/actions.

## AutomationStudio

Must support the automation-authoring workflow. Expanded layouts reserve distinct library/editor/inspector regions.

## EntityManager

For apps, components, notifications, rules and similar entities. Expanded layouts support collection navigation + entity list + inspector. Compact layouts keep the entity collection primary.

## Tool

For focused utilities/forms/parameter tools. Use readable-width work content rather than manager geometry.

## Detail

For one focused entity/result/editor. Use readable width and feature-owned actions.

## Fullscreen

For media, preview, terminal, remote-desktop or other intentionally chrome-free content. Host navigation must not force a generic app bar over the surface.

## CI contract

Every feature registration explicitly declares a ProductSurfaceKind. The matching product-surface symbol must exist in that feature's implementation. Generic legacy page wrappers are forbidden inside feature implementations.
