#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
violations = []

upstream_path = ROOT / "config/upstream-product-bases.json"
if not upstream_path.exists():
    print("Missing config/upstream-product-bases.json")
    sys.exit(1)
upstream_config = json.loads(upstream_path.read_text(encoding="utf-8"))
upstream_products = upstream_config.get("products", {})
required_product_kinds = {
    "Dashboard",
    "FileManager",
    "Browser",
    "Settings",
    "LogViewer",
    "DownloadManager",
    "TaskManager",
    "AutomationStudio",
    "EntityManager",
    "Tool",
    "Detail",
    "Fullscreen",
}
missing_upstream = sorted(required_product_kinds - set(upstream_products))
if missing_upstream:
    print("Missing upstream product bases: " + ", ".join(missing_upstream))
    sys.exit(1)
for kind, spec in upstream_products.items():
    if not spec.get("repo") or not spec.get("license"):
        print(f"Invalid upstream product base for {kind}")
        sys.exit(1)

legacy_roots = ["apps", "libs/yui", "suite", "standalone"]
legacy_ui_files = (
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteAdaptive.kt",
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteAppShell.kt",
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteArchitectureOverview.kt",
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuiteLazyPages.kt",
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuitePageState.kt",
    "core/ui/src/main/java/com/yagay/ysuite/ui/YSuitePages.kt",
)
for legacy_file in legacy_ui_files:
    if (ROOT / legacy_file).exists():
        violations.append(
            f"legacy generic UI file must not exist: {legacy_file}"
        )

for legacy in legacy_roots:
    if (ROOT / legacy).exists():
        violations.append(
            f"legacy source root must not exist in clean branch: {legacy}"
        )

pure_api_roots = (
    "core/platform/api/",
    "core/logging/api/",
    "core/permissions/api/",
    "feature/",
)

generic_feature_page_symbols = (
    "YSuiteDashboardPage",
    "YSuiteListPage",
    "YSuiteDetailPage",
    "YSuiteSettingsPage",
    "YSuiteLazyListPage",
)

legacy_product_surface_symbols = (
    "YFileManagerScaffold",
    "YBrowserWorkspace",
    "YSettingsSurface",
    "YDashboardSurface",
    "YLogViewerSurface",
    "YDownloadManagerSurface",
    "YTaskManagerSurface",
    "YAutomationStudioSurface",
    "YEntityManagerSurface",
    "YToolSurface",
    "YDetailSurface",
    "YFullscreenSurface",
)

product_surface_requirements = {
    "Dashboard": "NiaDashboardSurface",
    "FileManager": "FileExplorerWorkspace",
    "Browser": "YueBrowserWorkspace",
    "Settings": "ComposeSettingsSurface",
    "LogViewer": "LogcatReaderWorkspace",
    "DownloadManager": "QdmDownloadWorkspace",
    "TaskManager": "ComposeTodoTaskWorkspace",
    "AutomationStudio": "OpenTaskerWorkspace",
    "EntityManager": "LibCheckerWorkspace",
    "Tool": "NiaToolSurface",
    "Detail": "NiaDetailSurface",
    "Fullscreen": "FileExplorerPreviewSurface",
}

android_adapter_packages = (
    "com.yagay.ysuite.platform.android",
    "com.yagay.ysuite.logging.android",
    "com.yagay.ysuite.permissions.android",
)

for path in ROOT.rglob("*"):
    if not path.is_file():
        continue

    rel = path.relative_to(ROOT).as_posix()
    if "/build/" in f"/{rel}/":
        continue

    if path.suffix not in {".kt", ".java"}:
        continue

    text = path.read_text(encoding="utf-8", errors="ignore")

    if rel.startswith("feature/") and "/impl/" in rel:
        for generic_symbol in generic_feature_page_symbols:
            if generic_symbol in text:
                violations.append(
                    f"{rel}: generic feature page is forbidden: "
                    f"{generic_symbol}; use a product surface"
                )

        for legacy_surface in legacy_product_surface_symbols:
            if legacy_surface in text:
                violations.append(
                    f"{rel}: legacy YSuite-authored product surface is forbidden: "
                    f"{legacy_surface}; use the mapped upstream product layout"
                )

        if "androidx.compose.material3." in text:
            violations.append(
                f"{rel}: feature imports Material3 directly; "
                "use core design system"
            )
        if "com.google.android.material." in text:
            violations.append(
                f"{rel}: feature imports Material Views directly; "
                "use core design system"
            )
        for adapter in android_adapter_packages:
            if adapter in text:
                violations.append(
                    f"{rel}: feature must depend on API, not Android adapter: "
                    f"{adapter}"
                )
        for field in ("title", "subtitle", "label", "message", "text"):
            if re.search(
                rf'\b{field}\s*=\s*"[^"]+"',
                text,
            ):
                violations.append(
                    f"{rel}: hardcoded user-visible {field}; "
                    "use string resources"
                )

    if rel.startswith("feature/") and "/api/" in rel:
        if re.search(r"\b(android|androidx)\.", text):
            violations.append(
                f"{rel}: feature API must remain framework-neutral"
            )

    if any(rel.startswith(root) for root in pure_api_roots[:3]):
        if re.search(r"\b(android|androidx)\.", text):
            violations.append(
                f"{rel}: infrastructure API must remain framework-neutral"
            )

    if rel.startswith("app/") and "androidx.compose.material3." in text:
        violations.append(
            f"{rel}: app must render through core:ui"
        )

    if rel.startswith("app/") and "com.yagay.ysuite.designsystem" in text:
        violations.append(
            f"{rel}: app must not depend directly on design system"
        )

    if rel.startswith("app/") and "com.yagay.ysuite.resources" in text:
        violations.append(
            f"{rel}: app must not depend directly on resource implementation"
        )

    if rel.startswith("core/designsystem/") and "com.yagay.ysuite.ui" in text:
        violations.append(
            f"{rel}: design system must not depend on core:ui"
        )

    if rel.startswith("core/ui/") and "com.yagay.ysuite.feature." in text:
        violations.append(
            f"{rel}: core:ui must not depend on features"
        )

product_ui_root = (
    ROOT
    / "core/productui/src/main/java/com/yagay/ysuite/productui"
)
product_chrome = product_ui_root / "ProductChrome.kt"
if not product_chrome.exists():
    violations.append(
        "core:productui must provide ProductChrome.kt"
    )
else:
    for product_file in product_ui_root.rglob("*.kt"):
        if product_file == product_chrome:
            continue
        product_text = product_file.read_text(
            encoding="utf-8",
            errors="ignore",
        )
        rel = product_file.relative_to(ROOT).as_posix()
        if re.search(r"\\bTopAppBar\\s*\\(", product_text):
            violations.append(
                f"{rel}: product workspace owns TopAppBar directly; "
                "use YSuiteProductTopBar"
            )
        if re.search(r"\\bScaffold\\s*\\(", product_text):
            violations.append(
                f"{rel}: product workspace owns Scaffold directly; "
                "use YSuiteProductScaffold"
            )

for gradle in ROOT.glob("feature/*/impl/build.gradle.kts"):
    rel = gradle.relative_to(ROOT).as_posix()
    text = gradle.read_text(encoding="utf-8", errors="ignore")

    forbidden_projects = (
        ":core:platform:android",
        ":core:logging:android",
        ":core:permissions:android",
    )
    for forbidden in forbidden_projects:
        if f'project("{forbidden}")' in text:
            violations.append(
                f"{rel}: feature cannot depend on Android adapter "
                f"{forbidden}"
            )

    own_feature = gradle.parts[-3]
    for match in re.finditer(
        r'project\("(:feature:[^"]+:impl)"\)',
        text,
    ):
        dependency = match.group(1)
        if dependency != f":feature:{own_feature}:impl":
            violations.append(
                f"{rel}: feature implementation dependency is forbidden: "
                f"{dependency}"
            )

for registration in ROOT.glob(
    "feature/*/impl/src/main/java/**/*FeatureUiRegistration.kt"
):
    rel = registration.relative_to(ROOT).as_posix()
    text = registration.read_text(encoding="utf-8", errors="ignore")

    match = re.search(
        r"override\s+val\s+productSurface\s*=\s*"
        r"ProductSurfaceKind\.([A-Za-z]+)",
        text,
    )
    if match is None:
        violations.append(
            f"{rel}: feature must explicitly declare productSurface"
        )
        continue

    kind = match.group(1)
    required = product_surface_requirements.get(kind)
    if required is None:
        continue

    feature_name = rel.split("/")[1]
    feature_root = ROOT / "feature" / feature_name / "impl"
    feature_sources = "\n".join(
        source.read_text(encoding="utf-8", errors="ignore")
        for source in feature_root.rglob("*.kt")
    )

    product_forbidden_symbols = {
        "FileManager": (
            "YSuiteFilterBar",
            "YFileSectionSwitcher",
            "YFileManagerScaffold",
        ),
        "Settings": (
            "YSuiteFilterBar",
            "YSettingsSurface",
        ),
    }
    for forbidden_symbol in product_forbidden_symbols.get(kind, ()):
        if forbidden_symbol in feature_sources:
            violations.append(
                f"{rel}: ProductSurfaceKind.{kind} cannot use "
                f"{forbidden_symbol}; preserve the upstream product hierarchy"
            )

    if required not in feature_sources:
        violations.append(
            f"{rel}: ProductSurfaceKind.{kind} requires "
            f"{required} in the feature implementation"
        )

app_container = ROOT / "app/src/main/java/com/yagay/ysuite/YSuiteAppContainer.kt"
if app_container.exists():
    app_container_text = app_container.read_text(encoding="utf-8", errors="ignore")
    if "TemplateFeatureUiRegistration" in app_container_text:
        violations.append(
            "app/YSuiteAppContainer.kt: template feature must not ship in the product registry"
        )

if violations:
    print("\n".join(sorted(set(violations))))
    sys.exit(1)

print("Architecture boundaries OK")
