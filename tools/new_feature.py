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
VERSION_CATALOG = ROOT / "gradle/libs.versions.toml"

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
        f"capabilities = [{caps}]\n"
        "lifecycle = \"managed\"\n"
    )


def settings_gradle(name: str) -> str:
    return f'''pluginManagement {{ repositories {{ google(); mavenCentral(); gradlePluginPortal() }} }}
dependencyResolutionManagement {{
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {{ google(); mavenCentral() }}
}}

val suiteRoot = file("../..").canonicalFile
val localApi = suiteRoot.resolve("libs/yapi")
val localUi = suiteRoot.resolve("libs/yui")
if (localApi.isDirectory && localUi.isDirectory) {{
    include(":ysuite-api", ":ysuite-ui")
    project(":ysuite-api").projectDir = localApi
    project(":ysuite-ui").projectDir = localUi
}} else {{
    sourceControl {{
        gitRepository(uri("https://github.com/yagay/YSuite.git")) {{
            producesModule("com.github.yagay.YSuite:api")
            producesModule("com.github.yagay.YSuite:ui")
        }}
    }}
}}

rootProject.name = "{name}"
include(":app", ":feature")
'''


def root_build_gradle() -> str:
    return '''plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val localSuiteShared = rootProject.findProject(":ysuite-api") != null &&
    rootProject.findProject(":ysuite-ui") != null

if (localSuiteShared) {
    subprojects {
        configurations.configureEach {
            resolutionStrategy.dependencySubstitution {
                substitute(module("com.github.yagay.YSuite:api"))
                    .using(project(":ysuite-api"))
                substitute(module("com.github.yagay.YSuite:ui"))
                    .using(project(":ysuite-ui"))
            }
        }
    }
}
'''


def app_build_gradle(package_name: str) -> str:
    return f'''plugins {{
    id("com.android.application")
}}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {{
    namespace = "{package_name}.standalone"
    compileSdk {{
        version = release(libs.versions.compileSdk.get().toInt()) {{
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }}
    }}

    defaultConfig {{
        applicationId = "{package_name}"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        if (ciArm64Only) {{
            ndk {{
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }}
        }}
    }}

    compileOptions {{
        sourceCompatibility = sharedJavaVersion
        targetCompatibility = sharedJavaVersion
    }}

    packaging.resources.merges += "META-INF/xposed/*"
    sourceSets {{
        getByName("main") {{ resources.srcDirs("src/main/resources") }}
    }}

    buildTypes {{
        release {{
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }}
    }}
}}

dependencies {{
    implementation(project(":feature"))
}}
'''


def feature_build_gradle(package_name: str) -> str:
    return f'''plugins {{
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}}

val sharedSuiteBranch = providers.gradleProperty("ySuiteSharedBranch").orElse("main")
val sharedJavaVersion = JavaVersion.toVersion(libs.versions.java.get())

android {{
    namespace = "{package_name}"
    compileSdk {{
        version = release(libs.versions.compileSdk.get().toInt()) {{
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }}
    }}

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
    implementation("com.github.yagay.YSuite:api") {{
        version {{ branch = sharedSuiteBranch.get() }}
    }}
    implementation("com.github.yagay.YSuite:ui") {{
        version {{ branch = sharedSuiteBranch.get() }}
    }}
}}
'''


def app_manifest(package_name: str) -> str:
    activity = package_name + ".MainActivity"
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name="{activity}"
            android:exported="true"
            tools:replace="android:exported">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'''


def feature_manifest(package_name: str) -> str:
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="{package_name}.MainActivity"
            android:exported="false" />
    </application>
</manifest>
'''


def main_activity(package_name: str, name: str, description: str) -> str:
    return f'''package {package_name}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.yagay.yui.YPluginHeader
import com.yagay.yui.YPluginList
import com.yagay.yui.YPluginScaffold
import com.yagay.yui.YTheme
import com.yagay.yui.YView

class MainActivity : ComponentActivity() {{
    override fun onCreate(savedInstanceState: Bundle?) {{
        super.onCreate(savedInstanceState)
        YView.applyComposeWindow(this)
        setContent {{
            YTheme {{
                YPluginScaffold(
                    title = {q(name)},
                    subtitle = {q(description)},
                ) {{ padding ->
                    YPluginList(padding) {{
                        item {{
                            YPluginHeader(
                                name = {q(name)},
                                description = {q(description)},
                                detail = "Feature scaffold ready",
                            )
                        }}
                        item {{ Text("在 feature 模块中继续实现业务功能。") }}
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

    override fun attach(host: FeatureHost) {{
        this.host = host
    }}

    override fun enable() {{
        host?.log(HostLogLevel.INFO, "{feature_id} runtime enabled")
    }}

    override fun disable() {{
        host?.log(HostLogLevel.INFO, "{feature_id} runtime disabled")
    }}

    override fun destroy() {{
        host = null
    }}

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
        description="Create a minimal independently buildable YSuite feature scaffold.",
    )
    parser.add_argument("--id", required=True, help="lowercase feature id, e.g. ysample")
    parser.add_argument("--name", required=True, help="app directory/display name, e.g. YSample")
    parser.add_argument("--package", required=True, dest="package_name")
    parser.add_argument("--description", required=True)
    parser.add_argument(
        "--capabilities",
        default="",
        help="comma-separated host capabilities, e.g. ROOT,LSPOSED",
    )
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

    app_root = ROOT / "apps" / name
    if app_root.exists():
        fail(f"target already exists: {app_root.relative_to(ROOT)}")

    entry = render_catalog_entry(feature_id, name, package_name, description, capabilities)
    source_root = Path(*package_name.split("."))
    planned = {
        app_root / "settings.gradle.kts": settings_gradle(name),
        app_root / "build.gradle.kts": root_build_gradle(),
        app_root / "app/build.gradle.kts": app_build_gradle(package_name),
        app_root / "app/proguard-rules.pro": "# Standalone app rules.\n",
        app_root / "app/src/main/AndroidManifest.xml": app_manifest(package_name),
        app_root / "app/src/main/res/values/strings.xml": (
            f"<resources>\n    <string name=\"app_name\">{name}</string>\n</resources>\n"
        ),
        app_root / "feature/build.gradle.kts": feature_build_gradle(package_name),
        app_root / "feature/consumer-rules.pro": "# Feature consumer rules.\n",
        app_root / "feature/src/main/AndroidManifest.xml": feature_manifest(package_name),
        app_root / "feature/src/main/java" / source_root / "MainActivity.kt": (
            main_activity(package_name, name, description)
        ),
        app_root / "feature/src/main/java" / source_root / f"{name}SuiteRuntime.kt": (
            runtime_source(package_name, name, feature_id)
        ),
    }

    if args.dry_run:
        print("new-feature: dry run; no files changed")
        for path in planned:
            print(f"  {path.relative_to(ROOT)}")
        print("\nCatalog entry:")
        print(entry.strip())
        return

    for path, content in planned.items():
        write(path, content)

    current_catalog = CATALOG.read_text(encoding="utf-8").rstrip() + "\n"
    CATALOG.write_text(current_catalog + entry.lstrip("\n"), encoding="utf-8")
    print(f"new-feature: updated {CATALOG.relative_to(ROOT)}")

    mirror = app_root / "gradle/libs.versions.toml"
    write(mirror, VERSION_CATALOG.read_text(encoding="utf-8"))

    subprocess.run([sys.executable, str(ROOT / "tools/generate_feature_catalog.py")], check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/sync_version_catalog.py")], check=True)
    print(
        "new-feature: scaffold complete. Add business code under feature/, then run "
        "python3 tools/verify_feature_catalog.py and build the standalone app."
    )


if __name__ == "__main__":
    main()
