# YSuite Next

Clean-room rebuild of YSuite on a standard multi-module Android architecture.

This branch intentionally does **not** reuse the legacy YSuite feature/UI source tree.

## Principles

- One design system
- One page shell
- One localization system
- Feature isolation
- Dependency inversion for Root / LSPosed / logging
- No feature-owned themes or UI primitives
- English + Simplified Chinese from day one
- CI-enforced architecture boundaries

See `docs/ARCHITECTURE.md`, `docs/DESIGN_SYSTEM.md`, and `docs/LOCALIZATION.md`.
