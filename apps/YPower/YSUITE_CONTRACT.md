# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Preserve project-specific services, receivers and Hook behavior explicitly.

## YPower requirements

- Keep system-enhancement capabilities outside `/system`.
- Preserve the diagnostic center with real runtime state, concise/detailed views and actionable attribution suggestions.
- Keep recommended Hook targets visible and aligned with actual runtime requirements.
- Prefer shared YSuite Root/LSPosed host infrastructure when combined.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
