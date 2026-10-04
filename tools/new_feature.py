#!/usr/bin/env python3
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parents[1]

PRODUCTS = {
    "Dashboard": (
        "com.yagay.ysuite.productui.dashboard.YDashboardSurface",
        "YDashboardSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }) { _ -> body() }",
    ),
    "FileManager": (
        "com.yagay.ysuite.productui.filemanager.YFileManagerScaffold",
        "YFileManagerScaffold(title = title, navigationIcon = { YSuiteHostNavigationButton() }, breadcrumb = {}, content = { _ -> body() })",
    ),
    "Browser": (
        "com.yagay.ysuite.productui.browser.YBrowserWorkspace",
        "YBrowserWorkspace(navigationIcon = { YSuiteHostNavigationButton() }, addressBar = {}, content = { _ -> body() })",
    ),
    "Settings": (
        "com.yagay.ysuite.productui.settings.YSettingsSurface",
        "YSettingsSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }) { _ -> body() }",
    ),
    "LogViewer": (
        "com.yagay.ysuite.productui.logs.YLogViewerSurface",
        "YLogViewerSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, filters = {}, content = { _ -> body() })",
    ),
    "DownloadManager": (
        "com.yagay.ysuite.productui.manager.YDownloadManagerSurface",
        "YDownloadManagerSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, filterBar = {}, content = { _ -> body() })",
    ),
    "TaskManager": (
        "com.yagay.ysuite.productui.manager.YTaskManagerSurface",
        "YTaskManagerSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, filters = {}, content = { _ -> body() })",
    ),
    "AutomationStudio": (
        "com.yagay.ysuite.productui.automation.YAutomationStudioSurface",
        "YAutomationStudioSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, library = {}, editor = { _ -> body() })",
    ),
    "EntityManager": (
        "com.yagay.ysuite.productui.manager.YEntityManagerSurface",
        "YEntityManagerSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, content = { _ -> body() })",
    ),
    "Tool": (
        "com.yagay.ysuite.productui.tool.YToolSurface",
        "YToolSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }) { _ -> body() }",
    ),
    "Detail": (
        "com.yagay.ysuite.productui.detail.YDetailSurface",
        "YDetailSurface(title = title, navigationIcon = { YSuiteHostNavigationButton() }, content = { _ -> body() })",
    ),
    "Fullscreen": (
        "com.yagay.ysuite.productui.fullscreen.YFullscreenSurface",
        "YFullscreenSurface(content = { _ -> body() })",
    ),
}

def normalize(raw: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "", raw.lower())
    if not value:
        raise ValueError("Feature name must contain letters or numbers")
    return value

def class_name(feature: str) -> str:
    return feature[:1].upper() + feature[1:]

def write(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

def main():
    parser = argparse.ArgumentParser(
        description="Create a YSuite feature with an explicit product UI."
    )
    parser.add_argument("name")
    parser.add_argument(
        "--product",
        required=True,
        choices=sorted(PRODUCTS),
        help="Required product-level UI contract.",
    )
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    feature = normalize(args.name)
    cls = class_name(feature)
    product = args.product
    surface_import, surface_call = PRODUCTS[product]

    base = ROOT / "feature" / feature
    if base.exists():
        raise SystemExit(f"Feature already exists: {feature}")

    api_package = (
        base / "api" / "src/main/kotlin" /
        "com/yagay/ysuite/feature" / feature / "api"
    )
    impl_package = (
        base / "impl" / "src/main/java" /
        "com/yagay/ysuite/feature" / feature
    )

    planned = [
        base / "api" / "build.gradle.kts",
        base / "impl" / "build.gradle.kts",
        api_package / f"{cls}FeatureContract.kt",
        base / "impl" / "src/main/AndroidManifest.xml",
        impl_package / f"{cls}FeatureScreen.kt",
        impl_package / f"{cls}FeatureUiRegistration.kt",
        base / "impl" / "src/main/res/values/strings.xml",
        base / "impl" / "src/main/res/values-zh-rCN/strings.xml",
        ROOT / "docs/migrations" / f"{feature}.md",
    ]

    if args.dry_run:
        print(f"product={product}")
        print("\n".join(str(path.relative_to(ROOT)) for path in planned))
        return

    api_build = """plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core:model"))
    api(project(":core:navigation"))
}
"""

    impl_build = f"""plugins {{
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}}

android {{
    namespace = "com.yagay.ysuite.feature.{feature}"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {{ minSdk = libs.versions.minSdk.get().toInt() }}
    buildFeatures {{ compose = true }}
    compileOptions {{
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }}
}}

dependencies {{
    implementation(project(":feature:{feature}:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:presentation"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
}}
"""

    contract = f"""package com.yagay.ysuite.feature.{feature}.api

import com.yagay.ysuite.model.FeatureDescriptor
import com.yagay.ysuite.navigation.FeatureRegistration
import com.yagay.ysuite.navigation.RouteId

object {cls}FeatureContract : FeatureRegistration {{
    override val descriptor = FeatureDescriptor(
        id = "{feature}",
        route = "{feature}",
        order = 100,
    )

    override val startRoute = RouteId("{feature}")
    override val routes = setOf(startRoute)
}}
"""

    screen = f"""package com.yagay.ysuite.feature.{feature}

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.designsystem.component.YSuiteListItem
import com.yagay.ysuite.designsystem.component.YSuiteSection
import {surface_import}
import com.yagay.ysuite.ui.YSuiteHostNavigationButton

@Composable
fun {cls}FeatureScreen() {{
    val title = stringResource(R.string.{feature}_title)
    {surface_call.replace("body()", f"""Column {{
        YSuiteSection(
            title = stringResource(R.string.{feature}_section),
        ) {{
            YSuiteListItem(
                title = stringResource(R.string.{feature}_ready),
            )
        }}
    }}""")}
}}
"""

    registration = f"""package com.yagay.ysuite.feature.{feature}

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yagay.ysuite.feature.{feature}.api.{cls}FeatureContract
import com.yagay.ysuite.productui.ProductSurfaceKind
import com.yagay.ysuite.ui.YSuiteFeatureUiRegistration

object {cls}FeatureUiRegistration : YSuiteFeatureUiRegistration {{
    override val contract = {cls}FeatureContract
    override val productSurface = ProductSurfaceKind.{product}

    @Composable
    override fun label(): String =
        stringResource(R.string.{feature}_title)

    @Composable
    override fun Content() {{
        {cls}FeatureScreen()
    }}
}}
"""

    strings_en = f"""<resources>
    <string name="{feature}_title">{cls}</string>
    <string name="{feature}_summary">YSuite feature module.</string>
    <string name="{feature}_section">Overview</string>
    <string name="{feature}_ready">Product surface scaffold ready</string>
</resources>
"""

    strings_zh = f"""<resources>
    <string name="{feature}_title">{cls}</string>
    <string name="{feature}_summary">YSuite 功能模块。</string>
    <string name="{feature}_section">概览</string>
    <string name="{feature}_ready">产品级页面骨架已就绪</string>
</resources>
"""

    migration_doc = f"""# Feature migration: {cls}

Product surface: {product}

## Scope

Describe the product workflow being implemented.

## Product UI

Use the required {product} surface. Do not replace it with a generic page.

## Platform capabilities

List shared Root, Hook, logging, permissions or diagnostics APIs used.

## Localization

English and Simplified Chinese resources are maintained together.

## Tests

List business/state tests and product UI regression coverage.

## Standalone

Confirm the feature builds through the generic standalone host.
"""

    contents = {
        planned[0]: api_build,
        planned[1]: impl_build,
        planned[2]: contract,
        planned[3]: "<manifest />\n",
        planned[4]: screen,
        planned[5]: registration,
        planned[6]: strings_en,
        planned[7]: strings_zh,
        planned[8]: migration_doc,
    }

    for path, content in contents.items():
        write(path, content)

    settings = ROOT / "settings.gradle.kts"
    settings_text = settings.read_text(encoding="utf-8")
    marker = 'include(":host:standalone")'
    addition = (
        f'include(":feature:{feature}:api")\n'
        f'include(":feature:{feature}:impl")\n\n'
    )
    if marker not in settings_text:
        raise SystemExit("settings.gradle.kts marker not found")
    settings.write_text(
        settings_text.replace(marker, addition + marker),
        encoding="utf-8",
    )

    standalone = ROOT / "config/standalone-features.properties"
    with standalone.open("a", encoding="utf-8") as file:
        file.write(
            f"{feature}|:feature:{feature}:impl|"
            f"com.yagay.ysuite.feature.{feature}.{cls}FeatureUiRegistration|"
            f"com.yagay.ysuite.{feature}.standalone\n"
        )

    print(
        f"Created feature:{feature} with ProductSurfaceKind.{product}"
    )

if __name__ == "__main__":
    main()
