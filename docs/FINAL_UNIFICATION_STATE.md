# Unified maintenance state

- Shared UI source: `ui/` in YSuite only.
- Standalone apps: Gradle `sourceControl` -> `com.github.yagay.YSuite:ui` on `main`.
- Unified app: local `:ui` plus feature git submodules.
- JitPack/AAR publication is not part of the architecture.
- Feature repositories remain independently buildable and remain the source of their business code.
