# YSuite Next

Clean-room rebuild of YSuite on a standard multi-module Android architecture.

This branch intentionally does **not** reuse the legacy YSuite feature/UI source tree.

## Foundation included

- One design system and one page shell
- Dashboard / List / Detail / Settings page contracts
- Loading / Empty / Error / Permission page states
- Search / Filter / Dialog primitives
- Compact / Medium / Expanded responsive layout
- Framework-neutral navigation contracts
- Standard ViewModel state/effect base
- DataStore-backed application settings
- Central logging contract
- Permission checking boundary
- Framework-neutral diagnostics runner
- Root / LSPosed API and replaceable Android adapter boundary
- Feature `api/impl` reference template
- Standalone feature host using the same feature implementation
- English + Simplified Chinese from day one
- CI-enforced architecture and localization rules

No legacy feature has been connected to the main app.

See `docs/` for the contracts.
