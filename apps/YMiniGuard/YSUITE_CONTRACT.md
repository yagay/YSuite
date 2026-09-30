# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Preserve project-specific OPlus/system integration explicitly.

## YMiniGuard requirements

- Goal remains generic background playback / foreground survival, not a single-app Hook.
- Preserve protected-app selection and background playback, including lock-screen behavior where technically supported.
- Prefer the real OxygenOS/OPlus system mini-window path rather than a visually similar fake window.
- Preserve notification playback controls: play/pause and previous/next when supported by the target media session.
- Avoid UI changes that regress background playback or return-to-desktop behavior.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
