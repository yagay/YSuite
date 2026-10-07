#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config/standalone-features.properties"
errors = []
ids = set()

HOOK_FEATURES = {
    "yfiles",
    "ydownload",
    "yparam",
    "ydiag",
    "ypower",
    "ynotify",
    "ynfc",
    "yfloat",
    "yminiguard",
    "yentrycleaner",
}


def list_field(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(";") if item.strip()]


def module_path(module: str) -> Path:
    return ROOT / module.lstrip(":").replace(":", "/")


def class_exists(class_name: str, roots: list[Path]) -> bool:
    package_name, _, simple_name = class_name.rpartition(".")
    for root in roots:
        for suffix in (".kt", ".java"):
            for source in root.rglob(simple_name + suffix):
                text = source.read_text(encoding="utf-8", errors="ignore")
                if re.search(
                    rf"\bpackage\s+{re.escape(package_name)}\b",
                    text,
                ):
                    return True
    return False


if not CONFIG.exists():
    errors.append("Missing config/standalone-features.properties")
else:
    for number, raw in enumerate(
        CONFIG.read_text(encoding="utf-8").splitlines(),
        start=1,
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue

        parts = line.split("|")
        if len(parts) != 7:
            errors.append(f"line {number}: expected 7 fields")
            continue

        (
            feature_id,
            module,
            registration_class,
            application_id,
            runtime_raw,
            xposed_raw,
            scope_raw,
        ) = parts
        runtime_modules = list_field(runtime_raw)
        xposed_classes = list_field(xposed_raw)
        scopes = list_field(scope_raw)

        if feature_id in ids:
            errors.append(f"line {number}: duplicate id {feature_id}")
        ids.add(feature_id)

        if not module.startswith(":feature:") or not module.endswith(":impl"):
            errors.append(f"line {number}: invalid impl module {module}")

        impl_path = module_path(module)
        if not impl_path.exists():
            errors.append(
                f"line {number}: module path does not exist: "
                f"{impl_path.relative_to(ROOT)}"
            )
            continue

        runtime_paths = []
        for runtime_module in runtime_modules:
            if (
                not runtime_module.startswith(":feature:")
                or not runtime_module.endswith(":runtime")
            ):
                errors.append(
                    f"line {number}: invalid runtime module {runtime_module}"
                )
                continue
            path = module_path(runtime_module)
            runtime_paths.append(path)
            if not path.exists():
                errors.append(
                    f"line {number}: runtime module path does not exist: "
                    f"{path.relative_to(ROOT)}"
                )

        if not registration_class.startswith(
            "com.yagay.ysuite.feature."
        ):
            errors.append(
                f"line {number}: invalid registration class "
                f"{registration_class}"
            )
        else:
            package_parts = registration_class.split(".")
            simple_name = package_parts[-1]
            relative_package = Path(*package_parts)
            source_candidates = [
                impl_path / "src/main/java" /
                relative_package.with_suffix(".kt"),
                impl_path / "src/main/kotlin" /
                relative_package.with_suffix(".kt"),
            ]

            source = next(
                (candidate for candidate in source_candidates if candidate.exists()),
                None,
            )
            if source is None:
                errors.append(
                    f"line {number}: registration source not found: "
                    f"{registration_class}"
                )
            else:
                source_text = source.read_text(
                    encoding="utf-8",
                    errors="ignore",
                )
                if not re.search(
                    rf"\bobject\s+{re.escape(simple_name)}\b",
                    source_text,
                ):
                    errors.append(
                        f"line {number}: standalone registration must be "
                        f"a Kotlin object: {registration_class}"
                    )

        if not application_id.endswith(".standalone"):
            errors.append(
                f"line {number}: application id must end in .standalone"
            )

        if feature_id in HOOK_FEATURES and not xposed_classes:
            errors.append(
                f"line {number}: hook feature {feature_id} has no xposed init class"
            )

        search_roots = [impl_path, *runtime_paths]
        for class_name in xposed_classes:
            if not class_exists(class_name, search_roots):
                errors.append(
                    f"line {number}: xposed init class not found in selected "
                    f"modules: {class_name}"
                )

        if len(scopes) != len(set(scopes)):
            errors.append(
                f"line {number}: duplicate xposed scope entries"
            )

if errors:
    print("\n".join(errors))
    sys.exit(1)

print(f"Standalone configuration OK: {len(ids)} features")
