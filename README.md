# YSuite Next

Clean-room rebuild of YSuite on a standard multi-module Android architecture.

This branch intentionally does **not** reuse the legacy YSuite feature/UI source tree.

## Foundation included

- One design system and one page shell
- Dashboard / List / Detail / Settings page contracts
- Loading / Empty / Error / Permission page states
- Search / Filter / Dialog primitives
- Compact / Medium / Expanded responsive layout
- Registry-backed adaptive navigation
- Explicit application Composition Root
- App-wide DataStore theme/language settings wired to UI
- Standard ViewModel state/effect base
- Central logging, permissions and diagnostics contracts
- Root / LSPosed API with replaceable Android adapters
- Feature `api/impl` reference template
- Framework-owned Settings feature
- Standalone feature host using the same feature implementation
- Feature create/remove scripts
- English + Simplified Chinese
- CI architecture, localization and full-build verification
- Unit tests for navigation and diagnostics

The foundation topology is now frozen by CI. New production features must pass the clean-room
migration audit before they can be integrated.

No legacy YSuite feature has been migrated.
