#!/usr/bin/env python3
"""Verify that the combined APK contains every declared API-102 LSPosed module.

Checking the Java/Kotlin source alone is insufficient: dependencies or Android
resource packaging can produce a working UI with missing Hook module classes.
"""
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INIT = ROOT / "app/src/main/resources/META-INF/xposed/java_init.list"

if len(sys.argv) != 2:
    raise SystemExit("usage: verify_integrated_xposed_apk.py <apk>")
apk_path = Path(sys.argv[1])
if not apk_path.is_file():
    raise SystemExit("Integrated APK not found: " + str(apk_path))

expected = [
    line.strip() for line in INIT.read_text(encoding="utf-8").splitlines()
    if line.strip() and not line.lstrip().startswith("#")
]
if not expected or len(expected) != len(set(expected)):
    raise SystemExit("Missing or duplicate integrated Hook module declarations")

with zipfile.ZipFile(apk_path) as apk:
    names = set(apk.namelist())
    metadata = (
        "META-INF/xposed/java_init.list",
        "META-INF/xposed/module.prop",
        "META-INF/xposed/scope.list",
    )
    missing = set(metadata) - names
    if missing:
        raise SystemExit("Missing integrated Hook metadata: " + ", ".join(sorted(missing)))
    actual = [
        line.strip()
        for line in apk.read(metadata[0]).decode("utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if actual != expected:
        raise SystemExit("Integrated java_init.list changed or omitted declared modules")
    prop = apk.read(metadata[1]).decode("utf-8")
    if "minApiVersion=102" not in prop or "targetApiVersion=102" not in prop:
        raise SystemExit("Integrated LSPosed API-102 metadata is invalid")
    if not apk.read(metadata[2]).strip():
        raise SystemExit("Integrated LSPosed scope.list is empty")

    dex_files = sorted(x for x in names if x.startswith("classes") and x.endswith(".dex"))
    if not dex_files:
        raise SystemExit("No DEX bytecode was packaged")
    descriptors = [b"L" + name.replace(".", "/").encode() + b";" for name in expected]
    present = [False] * len(descriptors)
    for dex_file in dex_files:
        data = apk.read(dex_file)
        for i, descriptor in enumerate(descriptors):
            if not present[i] and descriptor in data:
                present[i] = True
    missing_modules = [name for name, ok in zip(expected, present) if not ok]
    if missing_modules:
        raise SystemExit("Hook module classes missing from combined APK: " +
                         ", ".join(missing_modules))
print(f"Integrated LSPosed metadata OK; {len(expected)} modules present")
