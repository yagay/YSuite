#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
contract = json.loads(
    (ROOT / "config/architecture-contract.json")
    .read_text(encoding="utf-8")
)
framework_features = set(contract["framework_features"])
errors = []

forbidden_source_patterns = {
    "legacy yagay package": r"import\s+com\.yagay\.(?!ysuite\.)",
    "direct Material3": r"androidx\.compose\.material3\.",
    "Android widget UI": r"android\.widget\.",
    "Android View UI": r"android\.view\.",
    "RecyclerView UI": r"androidx\.recyclerview\.",
    "raw Compose color": r"androidx\.compose\.ui\.graphics\.Color",
    "raw dp sizing": r"androidx\.compose\.ui\.unit\.dp",
    "custom rounded shape": r"RoundedCornerShape",
    "direct Android Log": r"(?:android\.util\.Log|\bLog\.[vd iwe]\s*\()",
    "direct process execution": r"(?:ProcessBuilder\s*\(|Runtime\.getRuntime\(\)\.exec)",
    "direct Xposed API": r"(?:de\.robv\.android\.xposed|io\.github\.libxposed|org\.lsposed)",
    "direct permission request": r"(?:requestPermissions\s*\(|ActivityResultContracts\.RequestPermission)",
}

forbidden_gradle_dependencies = {
    ":core:platform:android",
    ":core:logging:android",
    ":core:permissions:android",
}

feature_root = ROOT / "feature"
for feature_dir in sorted(
    path for path in feature_root.iterdir()
    if path.is_dir()
):
    feature = feature_dir.name
    if feature in framework_features:
        continue

    api = feature_dir / "api"
    impl = feature_dir / "impl"
    required = [
        api / "build.gradle.kts",
        impl / "build.gradle.kts",
        impl / "src/main/AndroidManifest.xml",
        impl / "src/main/res/values/strings.xml",
        impl / "src/main/res/values-zh-rCN/strings.xml",
        ROOT / "docs/migrations" / f"{feature}.md",
    ]
    for path in required:
        if not path.exists():
            errors.append(
                f"{feature}: missing required migration artifact "
                f"{path.relative_to(ROOT)}"
            )

    api_sources = list(api.rglob("*.kt"))
    impl_sources = list(impl.rglob("*.kt"))

    if not any(
        path.name.endswith("FeatureContract.kt")
        for path in api_sources
    ):
        errors.append(
            f"{feature}: api must contain *FeatureContract.kt"
        )

    if not any(
        path.name.endswith("FeatureUiRegistration.kt")
        for path in impl_sources
    ):
        errors.append(
            f"{feature}: impl must contain "
            "*FeatureUiRegistration.kt"
        )

    if not any(
        path.name.endswith("FeatureScreen.kt")
        for path in impl_sources
    ):
        errors.append(
            f"{feature}: impl must contain *FeatureScreen.kt"
        )

    gradle = impl / "build.gradle.kts"
    if gradle.exists():
        gradle_text = gradle.read_text(
            encoding="utf-8",
            errors="ignore",
        )
        required_dependencies = {
            f":feature:{feature}:api",
            ":core:ui",
        }
        project_dependencies = set(
            re.findall(
                r'project\("([^"]+)"\)',
                gradle_text,
            )
        )
        missing_dependencies = (
            required_dependencies - project_dependencies
        )
        if missing_dependencies:
            errors.append(
                f"{feature}: missing required dependencies "
                f"{sorted(missing_dependencies)}"
            )

        bad_dependencies = (
            forbidden_gradle_dependencies & project_dependencies
        )
        if bad_dependencies:
            errors.append(
                f"{feature}: Android adapters are composition-root "
                f"only: {sorted(bad_dependencies)}"
            )

        other_feature_impl = sorted(
            dep for dep in project_dependencies
            if dep.startswith(":feature:")
            and dep.endswith(":impl")
        )
        if other_feature_impl:
            errors.append(
                f"{feature}: cannot depend on another feature impl: "
                f"{other_feature_impl}"
            )

    for source in impl_sources:
        text = source.read_text(
            encoding="utf-8",
            errors="ignore",
        )
        rel = source.relative_to(ROOT)

        for label, pattern in forbidden_source_patterns.items():
            if re.search(pattern, text):
                errors.append(
                    f"{rel}: forbidden migration pattern: {label}"
                )

    if feature == "yfiles":
        forbidden_names = (
            "Theme.kt",
            "Themes.kt",
            "Components.kt",
            "Widgets.kt",
            "DesignSystem.kt",
            "UiKit.kt",
        )
        for source in impl_sources:
            if source.name in forbidden_names:
                errors.append(
                    f"yfiles: feature-local UI framework is forbidden: "
                    f"{source.relative_to(ROOT)}"
                )

        resource_root = impl / "src/main/res"
        if resource_root.exists():
            allowed_value_files = {
                "strings.xml",
            }
            for path in resource_root.rglob("*"):
                if not path.is_file():
                    continue
                relative = path.relative_to(resource_root)
                if relative.parts[0].startswith("values"):
                    if path.name not in allowed_value_files:
                        errors.append(
                            "yfiles: feature-local UI resources are "
                            f"forbidden: {path.relative_to(ROOT)}"
                        )
                else:
                    errors.append(
                        "yfiles: drawable/layout/font UI resources must "
                        "come from shared UI: "
                        f"{path.relative_to(ROOT)}"
                    )

        yfiles_gradle = impl / "build.gradle.kts"
        if yfiles_gradle.exists():
            gradle_text = yfiles_gradle.read_text(
                encoding="utf-8",
                errors="ignore",
            )
            if 'project(":core:designsystem")' not in gradle_text:
                errors.append(
                    "yfiles: must consume shared core:designsystem"
                )

    migration_doc = ROOT / "docs/migrations" / f"{feature}.md"
    if migration_doc.exists():
        text = migration_doc.read_text(
            encoding="utf-8",
            errors="ignore",
        )
        required_sections = (
            "## Scope",
            "## Clean-room implementation",
            "## UI",
            "## Platform capabilities",
            "## Localization",
            "## Tests",
            "## Standalone",
        )
        for section in required_sections:
            if section not in text:
                errors.append(
                    f"{feature}: migration document missing "
                    f"section {section}"
                )

if errors:
    print("\n".join(sorted(set(errors))))
    sys.exit(1)

print("Feature migration audit OK")
