# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Preserve project-specific initialization explicitly.

## YDiag requirements

- Selected-app diagnostics should start without reboot.
- Keep Root/LSPosed-aware collection, but expose selectable options so unnecessary logs are not always exported.
- Mark recommended diagnostic options.
- Keep logs structured and readable by both humans and AI.
- In YSuite, participate in full-suite and per-module export under `Download/YSuite/`.
- Capture meaningful failures without flooding logs with high-frequency success events.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
