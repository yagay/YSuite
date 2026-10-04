# YSuite Next

Clean-room rebuild of YSuite on a standard multi-module Android architecture.

This branch intentionally does **not** reuse the legacy YSuite feature/UI source tree.

## Foundation now included

- One design system
- One application/page shell
- Standard Dashboard / List / Detail / Settings page contracts
- Shared Loading / Empty / Error / Permission states
- Shared Search / Filter / Dialog primitives
- Framework-neutral navigation contracts and back stack
- Responsive Compact / Medium / Expanded content widths
- English + Simplified Chinese from day one
- CI-enforced architecture and localization boundaries
- Dependency inversion for Root / LSPosed / logging

No legacy feature has been connected yet.

See:
- `docs/ARCHITECTURE.md`
- `docs/DESIGN_SYSTEM.md`
- `docs/LOCALIZATION.md`
- `docs/PAGE_CONTRACTS.md`
- `docs/NAVIGATION.md`
