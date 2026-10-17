#!/usr/bin/env python3
"""Choose affected YSuite feature unit tests without bypassing Gradle dependencies.

:suite:assembleDebug always runs; Gradle task input fingerprints and the
cross-run build cache decide which unchanged module outputs can be reused.
"""
from __future__ import annotations

import argparse
import os
import subprocess
from pathlib import Path

FEATURE_TESTS = {
    "apps/YNFC/": ":ynfc-feature:testDebugUnitTest",
    "apps/YNotify/": ":ynotify-feature:testDebugUnitTest",
    "apps/YFloat/": ":yfloat-feature:testDebugUnitTest",
    "apps/YMiniGuard/": ":yminiguard-feature:testDebugUnitTest",
    "apps/YTaskManager/": ":ytask-feature:testDebugUnitTest",
}
ALL_TESTS = tuple(FEATURE_TESTS.values())
SHARED_PREFIXES = (
    "libs/",
    "suite/",
    "next/",
    "gradle/",
    "config/",
    "tools/",
    ".github/workflows/build-debug.yml",
)
SHARED_FILES = {
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
}


def choose_tests(changed_paths: list[str] | None) -> tuple[str, ...]:
    """Unknown diff -> run all tests (safe fallback)."""
    if changed_paths is None:
        return ALL_TESTS
    paths = {path.replace("\\\\", "/").lstrip("./") for path in changed_paths}
    if any(path in SHARED_FILES or path.startswith(SHARED_PREFIXES) for path in paths):
        return ALL_TESTS
    return tuple(
        task for prefix, task in FEATURE_TESTS.items()
        if any(path.startswith(prefix) and not (
            "/docs/" in path or path.endswith(".md")
        ) for path in paths)
    )


def git_diff_paths(base: str, head: str) -> list[str] | None:
    if not base or set(base) == {"0"} or not head:
        return None
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=ACMRTUXB", base, head],
        capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        print("Diff unavailable; running complete test set:", result.stderr.strip())
        return None
    return [line.strip() for line in result.stdout.splitlines() if line.strip()]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="")
    parser.add_argument("--head", default="")
    args = parser.parse_args()
    paths = git_diff_paths(args.base, args.head)
    selected = choose_tests(paths)
    print("Affected source paths:", len(paths) if paths is not None else "unknown")
    print("Selected tests:", ", ".join(selected) if selected else "(none)")
    if os.environ.get("GITHUB_OUTPUT"):
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as out:
            out.write("test_tasks=" + " ".join(selected) + "\n")


if __name__ == "__main__":
    main()
