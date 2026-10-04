# Upstream-first product policy

YSuite must not invent a product workflow when a mature open-source Android project already solves the same problem.

For every new feature or major capability:

1. Search for mature projects in the same product category before implementation.
2. Prefer MIT / Apache-2.0 projects whose real page structure can be adapted directly.
3. Preserve the upstream information architecture, navigation, selection behavior, action hierarchy, responsive layout and major component placement as closely as practical.
4. Replace only the integration layer: YSuite theme, typography, spacing tokens, icons, localization, data models, Root/Hook adapters, logging, diagnostics and permissions.
5. GPL projects may be studied for behavior but their source is not copied unless YSuite intentionally adopts a compatible license.
6. If no suitable mature project exists, document the search and reason before introducing a custom product layout.

The canonical mapping is `config/upstream-product-bases.json`. CI validates that every `ProductSurfaceKind` has an upstream base.
