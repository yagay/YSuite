# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Host theme and Activity wiring must work in both standalone and YSuite builds.

## YNotify requirements

- Preserve notification, Toast and detectable UI-event history such as heads-up/banner, bubble, full-screen/popup/dialog/snackbar.
- Preserve per-app views and full notification content rather than only truncated status-bar text.
- Historical reclassification must remain possible as classifiers improve.
- Avoid splitting one logical ongoing notification into meaningless duplicate rows where stable identity is available.
- Diagnostics must attribute notification-listener, accessibility, Hook and capture failures.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
