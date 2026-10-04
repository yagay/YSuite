#!/usr/bin/env python3
from pathlib import Path
import argparse
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

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
    parser = argparse.ArgumentParser(description="Create a clean YSuite feature api/impl pair.")
    parser.add_argument("name")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    feature = normalize(args.name)
    cls = class_name(feature)
    base = ROOT / "feature" / feature
    if base.exists():
        raise SystemExit(f"Feature already exists: {feature}")

    planned = [
        base / "api" / "build.gradle.kts",
        base / "impl" / "build.gradle.kts",
        base / "api" / "src/main/kotlin" / "com/yagay/ysuite/feature" / feature / "api" / f"{cls}FeatureContract.kt",
        base / "impl" / "src/main/res/values/strings.xml",
        base / "impl" / "src/main/res/values-zh-rCN/strings.xml",
    ]

    if args.dry_run:
        print("\n".join(str(p.relative_to(ROOT)) for p in planned))
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

    write(planned[0], api_build)
    write(planned[1], impl_build)
    write(planned[2], contract)
    write(planned[3], f'<resources>\n    <string name="{feature}_title">{cls}</string>\n</resources>\n')
    write(planned[4], f'<resources>\n    <string name="{feature}_title">{cls}</string>\n</resources>\n')

    settings = ROOT / "settings.gradle.kts"
    text = settings.read_text(encoding="utf-8")
    marker = 'include(":host:standalone")'
    addition = f'include(":feature:{feature}:api")\ninclude(":feature:{feature}:impl")\n\n'
    if marker not in text:
        raise SystemExit("settings.gradle.kts marker not found")
    settings.write_text(text.replace(marker, addition + marker), encoding="utf-8")

    print(f"Created feature:{feature}:api and feature:{feature}:impl")

if __name__ == "__main__":
    main()
