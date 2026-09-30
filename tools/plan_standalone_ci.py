#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

from generate_feature_catalog import load_features

ROOT = Path(__file__).resolve().parents[1]


def app_names() -> list[str]:
    names = {
        Path(item["project_dir"]).parts[1]
        for item in load_features()
        if len(Path(item["project_dir"]).parts) >= 3
    }
    return sorted(names)


def changed_files(base: str, head: str) -> list[str]:
    command = ["git", "diff", "--name-only", f"{base}...{head}"]
    result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        raise SystemExit(result.returncode)
    return [line.strip().replace("\\", "/") for line in result.stdout.splitlines() if line.strip()]


def select_apps(paths: list[str], all_apps: list[str]) -> list[str]:
    selected: set[str] = set()
    global_prefixes = (
        "libs/yapi/",
        "libs/yui/",
        "gradle/",
    )
    global_files = {
        "build.gradle.kts",
        "settings.gradle.kts",
        "config/features.toml",
        "config/generated/feature-modules.tsv",
        "tools/generate_feature_catalog.py",
        "tools/sync_version_catalog.py",
        "tools/export_build_versions.py",
        "tools/plan_standalone_ci.py",
        ".github/workflows/standalone.yml",
    }

    for path in paths:
        if path in global_files or path.startswith(global_prefixes):
            return all_apps
        if path.startswith("apps/"):
            parts = path.split("/")
            if len(parts) >= 2 and parts[1] in all_apps:
                selected.add(parts[1])
    return sorted(selected)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()

    apps = app_names()
    if args.all:
        selected = apps
        paths: list[str] = []
    else:
        if not args.base:
            raise SystemExit("standalone-ci: --base is required unless --all is used")
        paths = changed_files(args.base, args.head)
        selected = select_apps(paths, apps)

    matrix = {"app": selected}
    print("matrix=" + json.dumps(matrix, separators=(",", ":")))
    print(f"count={len(selected)}")
    print("selected=" + ",".join(selected))
    diagnostic = "all" if args.all else f"changed={len(paths)}"
    print(f"standalone-ci: {diagnostic} selected={selected}", file=sys.stderr)


if __name__ == "__main__":
    main()
