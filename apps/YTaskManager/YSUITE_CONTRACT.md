# YSuite Integration Contract

This project must remain independently buildable while exposing the same reusable `feature` source to YSuite.

Before refactoring, preserve these rules:

- Keep standalone `app` and reusable `feature` responsibilities separate.
- Do not copy feature source into YSuite or maintain a long-lived adapter mirror.
- In YSuite, reuse shared Root/LSPosed/log/diagnostics infrastructure wherever practical; standalone remains first-class.
- Keep architecture simple and make the module easy to add/remove.
- Do not flood unified logs with high-frequency successful refreshes.

## YTaskManager requirements

- Root is the primary privileged data/control layer; LSPosed API 102 is an enhancement layer.
- Do not introduce Shizuku or a native daemon as a required dependency.
- Preserve Processes / Resources / Network functionality, process details and controls, CPU/RAM/SWAP/GPU/system information, and app traffic views.
- Keep rooted netd/eBPF traffic accounting with qtaguid fallback where needed.
- Root failures, command timeouts and meaningful collection failures must be logged.

Canonical cross-project requirements live in `yagay/YSuite/docs/PRODUCT_REQUIREMENTS.md` and `MODULE_REQUIREMENTS.md`.
