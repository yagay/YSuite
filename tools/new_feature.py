#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

from generate_feature_catalog import ALLOWED_CAPABILITIES, load_features

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "config/features.toml"

ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
NAME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*$")
PACKAGE_RE = re.compile(r"^[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+$")


def fail(message: str) -> None:
    print(f"new-feature: ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def q(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def parse_capabilities(raw: str) -> list[str]:
    if not raw.strip():
        return []
    values = [part.strip().upper() for part in raw.split(",") if part.strip()]
    unknown = set(values) - ALLOWED_CAPABILITIES
    if unknown:
        fail(f"unknown capabilities: {sorted(unknown)}")
    return list(dict.fromkeys(values))


def render_catalog_entry(
    feature_id: str,
    name: str,
    package_name: str,
    description: str,
    capabilities: list[str],
) -> str:
    caps = ", ".join(q(cap) for cap in capabilities)
    return (
        "\n[[feature]]\n"
        f"id = {q(feature_id)}\n"
        f"name = {q(name)}\n"
        f"description = {q(description)}\n"
        f"gradle_module = {q(':' + feature_id + '-feature')}\n"
        f"project_dir = {q('apps/' + name + '/feature')}\n"
        f"entry_activity = {q(package_name + '.MainActivity')}\n"
        f"runtime = {q(package_name + '.' + name + 'SuiteRuntime')}\n"
        f"standalone_package = {q(package_name)}\n"
        "standalone_enabled = true\n"
        f"capabilities = [{caps}]\n"
        "lifecycle = \"managed\"\n"
    )


def feature_build_gradle(package_name: str) -> str:
    return f'''plugins {{
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}}

val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {{
    namespace = "{package_name}"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {{ minSdk = libs.versions.minSdk.get().toInt() }}
    buildFeatures {{ compose = true }}
    compileOptions {{
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }}
    buildTypes {{
        release {{
            isMinifyEnabled = false
            consumerProguardFiles("consumer-rules.pro")
        }}
    }}
}}

dependencies {{
    implementation(project(":api"))
    implementation(project(":ui"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}}
'''


def feature_manifest(package_name: str) -> str:
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="{package_name}.MainActivity" android:exported="false" />
    </application>
</manifest>
'''


def main_activity(package_name: str, name: str, description: str) -> str:
    return f'''package {package_name}

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.yagay.yui.YComposeActivity
import com.yagay.yui.YFeatureCard
import com.yagay.yui.YFeatureList
import com.yagay.yui.YFeatureScaffold

class MainActivity : YComposeActivity() {{
    @Composable
    override fun YContent() {{
        YFeatureScaffold(
            title = {q(name)},
            subtitle = {q(description)},
        ) {{ padding ->
            YFeatureList(padding) {{
                item {{
                    YFeatureCard(
                        title = {q(name)},
                        subtitle = {q(description)},
                        detail = "Feature scaffold ready",
                    ) {{
                        Text("在 feature 模块中继续实现业务功能。")
                    }}
                }}
            }}
        }}
    }}
}}
'''


def runtime_source(package_name: str, name: str, feature_id: str) -> str:
    class_name = name + "SuiteRuntime"
    return f'''package {package_name}

import android.content.Context
import com.yagay.suite.api.FeatureHost
import com.yagay.suite.api.HostLogLevel
import com.yagay.suite.api.ManagedFeatureRuntime

class {class_name} private constructor(context: Context) : ManagedFeatureRuntime {{
    private val appContext = context.applicationContext
    @Volatile private var host: FeatureHost? = null

    override fun attach(host: FeatureHost) {{ this.host = host }}
    override fun enable() {{ host?.log(HostLogLevel.INFO, "{feature_id} runtime enabled") }}
    override fun disable() {{ host?.log(HostLogLevel.INFO, "{feature_id} runtime disabled") }}
    override fun destroy() {{ host = null }}

    companion object {{
        @Volatile private var instance: {class_name}? = null
        @JvmStatic
        fun get(context: Context): {class_name} = instance ?: synchronized(this) {{
            instance ?: {class_name}(context).also {{ instance = it }}
        }}
    }}
}}
'''


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"new-feature: wrote {path.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create a pure YSuite Feature. Standalone APK packaging is provided by the shared host."
    )
    parser.add_argument("--id", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--package", required=True, dest="package_name")
    parser.add_argument("--description", required=True)
    parser.add_argument("--capabilities", default="")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    feature_id = args.id.strip()
    name = args.name.strip()
    package_name = args.package_name.strip()
    description = args.description.strip()
    capabilities = parse_capabilities(args.capabilities)

    if not ID_RE.fullmatch(feature_id):
        fail("--id must match [a-z][a-z0-9_]*")
    if not NAME_RE.fullmatch(name):
        fail("--name must be a simple Java-style identifier")
    if not PACKAGE_RE.fullmatch(package_name):
        fail("--package must be a dotted Java package name")
    if not description:
        fail("--description must not be empty")

    existing = load_features()
    if any(item["id"] == feature_id for item in existing):
        fail(f"feature id already exists: {feature_id}")
    if any(item["name"] == name for item in existing):
        fail(f"feature name already exists: {name}")

    feature_root = ROOT / "apps" / name / "feature"
    if feature_root.exists():
        fail(f"target already exists: {feature_root.relative_to(ROOT)}")

    entry = render_catalog_entry(feature_id, name, package_name, description, capabilities)
    source_root = Path(*package_name.split("."))
    planned = {
        feature_root / "build.gradle.kts": feature_build_gradle(package_name),
        feature_root / "consumer-rules.pro": "# Feature consumer rules.\n",
        feature_root / "src/main/AndroidManifest.xml": feature_manifest(package_name),
        feature_root / "src/main/java" / source_root / "MainActivity.kt": main_activity(package_name, name, description),
        feature_root / "src/main/java" / source_root / f"{name}SuiteRuntime.kt": runtime_source(package_name, name, feature_id),
    }

    if args.dry_run:
        print("new-feature: dry run; no files changed")
        for path in planned:
            print(f"  {path.relative_to(ROOT)}")
        print("\nCatalog entry:")
        print(entry.strip())
        print(f"\nStandalone build: gradle buildFeatureDebug -PySuiteStandaloneFeature={feature_id}")
        return

    for path, content in planned.items():
        write(path, content)

    current_catalog = CATALOG.read_text(encoding="utf-8").rstrip() + "\n"
    CATALOG.write_text(current_catalog + entry.lstrip("\n"), encoding="utf-8")
    print(f"new-feature: updated {CATALOG.relative_to(ROOT)}")

    subprocess.run([sys.executable, str(ROOT / "tools/generate_feature_catalog.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/generate_standalone_catalog.py")], check=True)
    print(
        "new-feature: pure Feature scaffold complete. "
        f"Build standalone with: gradle buildFeatureDebug -PySuiteStandaloneFeature={feature_id}"
    )


if __name__ == "__main__":
    main()
