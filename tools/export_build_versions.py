#!/usr/bin/env python3
from __future__ import annotations

import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle/libs.versions.toml"

REQUIRED = {
    "java": "java",
    "gradle": "gradle",
    "compile_sdk": "compileSdk",
    "compile_sdk_minor": "compileSdkMinor",
    "android_platform": "androidPlatform",
    "build_tools": "buildTools",
}


def main() -> None:
    data = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
    versions = data.get("versions") or {}
    for output, key in REQUIRED.items():
        value = str(versions.get(key, "")).strip()
        if not value:
            raise SystemExit(f"build-versions: missing [versions].{key}")
        print(f"{output}={value}")


if __name__ == "__main__":
    main()
