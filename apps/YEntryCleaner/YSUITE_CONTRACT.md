# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Ordinary UI/rule changes should apply without restart; only Hook/runtime changes should request restart.
- Preserve project-specific Application/Provider/Service/Hook behavior explicitly rather than relying on accidental standalone initialization.

## YEntryCleaner requirements

- YEntryCleaner is the YSuite replacement for ListCleaner; do not restore the old ListCleaner compatibility path.
- Keep Root + LSPosed architecture; do not move back to IFW.
- Preserve category management, share/open-with cleanup, component management, priority sorting, historical/restricted handling, QS Tiles, shortcuts and widgets.
- Lock state is independent from ordinary selection; bulk selection must not silently change locked items.
- Preserve locked filtering and system/user separation where applicable.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
