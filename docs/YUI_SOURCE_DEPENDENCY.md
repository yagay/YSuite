# YUI source dependency

Standalone projects use `YSuite/ui` directly from the YSuite `main` branch through Gradle `sourceControl`.

This keeps one UI source tree for both standalone APKs and the unified YSuite APK. Do not re-introduce a copied YUI module, JitPack publication, or a separately versioned AAR unless the architecture is intentionally changed.

A standalone checkout resolves only `:ui` because YSuite feature git submodules are not recursively present. A normal YSuite checkout with all submodules builds the full host.
