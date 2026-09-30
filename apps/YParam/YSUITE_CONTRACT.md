# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Prefer reusable parameter descriptors/components over one-off screens.

## YParam requirements

- Preserve per-app overrides such as DPI/resolution/language/location and future parameter simulation.
- Read and show the app/system default value so default vs override is always clear.
- Every override needs a restore-default path.
- Prefer selectors, presets and constrained controls over raw text entry when values have a known domain.
- New parameter types should plug into the same common UI/data model where practical.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
