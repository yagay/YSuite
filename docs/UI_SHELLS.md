# YSuite adaptive UI shell system

YSuite uses one design system and multiple first-class page shapes. A feature must not create a second theme or a private application scaffold.

## Page kinds

- Dashboard: overview and entry pages.
- Manager: file, download, task, notification, and other dense management surfaces.
- Browser: full-bleed browser/web content with feature-owned toolbar, tab strip, and bottom controls.
- Tool: forms, diagnostics, parameter editors, and focused utilities.
- Settings: preference-oriented pages with readable line length.
- Detail: focused detail/edit pages.
- Fullscreen: media, preview, terminal, or other intentionally chrome-free content.

## Ownership

The host owns only feature navigation and feature lifecycle.

Each feature owns its page chrome by selecting a shell from core:ui. This prevents a browser, manager, and settings page from being forced through the same Scaffold.

core:designsystem owns visual tokens and reusable components.
core:ui owns adaptive window classification, shells, navigation chrome integration, and common page/state hosts.
feature modules own business UI composition only.

## Adaptive behavior

Compact and medium windows use the host drawer. Expanded windows use a permanent navigation pane.

Document-like surfaces are width constrained for readability. Manager surfaces can grow much wider. Browser and fullscreen surfaces are never constrained to document width.

Manager and tool shells may expose a supporting pane on expanded widths without changing feature business logic.

## Rules

1. Do not add a feature-local MaterialTheme.
2. Do not add a feature-local root Scaffold when a YSuite shell fits the page.
3. Do not place WebView/browser content inside document-width containers.
4. Do not force file/task/download managers into card-only layouts.
5. Standalone builds use the same feature UI and the same shell APIs as the integrated YSuite host.
