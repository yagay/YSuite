#!/usr/bin/env python3
"""Scan every main-source Kotlin, Java and XML file for YUI visual drift.

This is an inventory, not a blanket ban: gesture hitboxes, density-independent
measurements and product-specific layouts can be legitimate. Review findings
before converting functional geometry into appearance settings.
"""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PATTERNS = {
    "numeric_dp_sp": re.compile(r"(?<![\w.])(?:0|[1-9]\d*)(?:\.\d+)?\.(?:dp|sp)\b"),
    "java_fixed_dp": re.compile(
        r"\b(?:dp|px)\s*\(\s*(?:[A-Za-z_]\w*\s*,\s*)?"
        r"(?:0|[1-9]\d*)(?:\.\d+)?f?\s*\)"
    ),
    "xml_fixed_dimension": re.compile(
        r"\b(?:android:)?(?:layout_width|layout_height|padding(?:Top|Bottom|Start|End|Left|Right)?"
        r"|layout_margin(?:Top|Bottom|Start|End|Left|Right)?|textSize|elevation)"
        r'\s*=\s*"[\d.]+(?:dp|sp)"'
    ),
    "hardcoded_ui_color": re.compile(
        r"\b(?:Color\((?:0x[0-9a-fA-F]{6,8})|Color\.(?:WHITE|BLACK|RED|BLUE|White|Black|Red|Blue)"
        r"|Color\.parseColor\(\"#[0-9a-fA-F]{6,8}\"\))"
    ),
    "direct_material_control": re.compile(
        r"^\s*import\s+androidx\.compose\.material3\."
        r"(?:Button|OutlinedButton|TextButton|IconButton|Switch|Checkbox|RadioButton|"
        r"Card|OutlinedCard|AlertDialog|TextField|OutlinedTextField|Slider|"
        r"ModalBottomSheet|DropdownMenu|DropdownMenuItem|NavigationBar|"
        r"TopAppBar|Scaffold)(?:\s+as\s+\w+)?\s*$"
    ),
    "static_yui_visual_token": re.compile(
        r"\bYDimens\.(?:ScreenHorizontal|ScreenVertical|SectionGap|ControlGap|"
        r"ToolbarHeight|ButtonHeight|CardPadding|CardRadius|IconVisualSize|"
        r"IconSmallSize|ListIconSize|TouchTarget|OptionRowHeight|"
        r"SwitchSlotWidth|NavigationRailWidth|PaneGap)\b"
    ),
    "canvas_fixed_density": re.compile(
        r"\b(?:[1-9]\d*(?:\.\d+)?f?)\s*\*\s*(?:density|screenDensity)\b"
    ),
    "canvas_fixed_argb": re.compile(
        r"\b(?:setColor|drawColor|setShadowLayer)\s*\([^\n]*0x[0-9a-fA-F]{8}\b"
    ),
    "independent_visual_preference": re.compile(
        r"(?:getSharedPreferences|put(?:String|Int|Boolean)|get(?:String|Int|Boolean))"
        r".{0,130}(?:theme|dark_mode|appearance|corner_radius|ui_density)",
        re.IGNORECASE,
    ),
}
UI_PATH = re.compile(
    r"/(?:ui|view|views|screens|components|designsystem|productui|yui)/|"
    r"(?:Screen|Activity|Dialog|Menu|Panel|Overlay|Widget|Adapter|"
    r"Component|Theme|Style|Layout|Fragment|Surface|Settings|View)\.(?:kt|java)$",
    re.IGNORECASE,
)
EXCLUDE_PARTS = {"build", ".gradle", ".git", "node_modules", "generated"}

def classify(rel: str, kind: str, line: str) -> str:
    """Separate canonical YUI definitions from genuine feature review candidates."""
    name = rel.rsplit("/", 1)[-1]
    if kind == "xml_fixed_dimension" and '"0dp"' in line:
        return "structural"
    if kind == "numeric_dp_sp" and re.search(r"(?<!\w)0\.dp\b", line):
        return "structural"
    if kind == "independent_visual_preference" and "R.string." in line:
        return "localization"
    if name == "YTheme.kt" and kind == "hardcoded_ui_color":
        return "canonical"
    if name == "YSuiteLayoutTokens.kt" and kind == "numeric_dp_sp":
        return "structural"
    if rel.startswith("libs/yui/"):
        if name in {"YUiPalette.kt", "YGeneratedTokens.kt"}:
            return "canonical"
        if kind == "direct_material_control":
            return "canonical"
    if name == "GoogleCanonicalFrameLayer.java" and kind in {
        "hardcoded_ui_color", "canvas_fixed_density", "canvas_fixed_argb"
    }:
        return "hook_visual_review"
    if name == "SecureCaptureProbeActivity.java" and kind == "hardcoded_ui_color":
        return "test_marker"
    if name == "SelectionCropper.java" and kind == "hardcoded_ui_color":
        return "image_processing"
    if name == "YFloatVisualSettings.java" and "oldTheme.getInt" in line:
        return "legacy_migration"
    if name in {"FloatIconView.java", "RegionOverlay.java", "FloatService.java",
                "EditableRegionOverlay.java"} and kind == "java_fixed_dp":
        return "functional_or_visual_review"
    return "review"

def module_of(path: Path) -> str:
    parts = path.relative_to(ROOT).parts
    if not parts:
        return "root"
    if parts[0] == "apps" and len(parts) > 1:
        return "/".join(parts[:2])
    if parts[0] == "next" and len(parts) > 3:
        return "/".join(parts[:4])
    if parts[0] in {"libs", "suite", "standalone"} and len(parts) > 1:
        return "/".join(parts[:2])
    return parts[0]

def scan() -> tuple[dict, list]:
    inventory = defaultdict(lambda: {"files": 0, "ui_files": 0, "findings": Counter()})
    findings = []
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in {".kt", ".java", ".xml"}:
            continue
        if any(part in EXCLUDE_PARTS for part in path.parts):
            continue
        rel = path.relative_to(ROOT).as_posix()
        if "/src/main/" not in rel:
            continue
        module = module_of(path)
        in_ui = bool(UI_PATH.search("/" + rel))
        entry = inventory[module]
        entry["files"] += 1
        if in_ui:
            entry["ui_files"] += 1
        try:
            source = path.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        for number, line in enumerate(source.splitlines(), 1):
            for kind, pattern in PATTERNS.items():
                if kind == "direct_material_control":
                    if path.suffix == ".xml":
                        continue
                if kind == "xml_fixed_dimension" and path.suffix != ".xml":
                    continue
                if not pattern.search(line):
                    continue
                entry["findings"][kind] += 1
                findings.append({
                    "file": rel, "line": number, "module": module,
                    "kind": kind, "ui": in_ui,
                    "classification": classify(rel, kind, line),
                    "excerpt": line.strip()[:180],
                })
    return inventory, findings

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", type=Path)
    parser.add_argument("--markdown", type=Path)
    args = parser.parse_args()
    inventory, findings = scan()
    modules = []
    for module, data in inventory.items():
        modules.append({
            "module": module, "files": data["files"],
            "ui_files": data["ui_files"],
            "findings": sum(data["findings"].values()),
            "by_kind": dict(sorted(data["findings"].items())),
        })
    modules.sort(key=lambda row: (-row["findings"], row["module"]))
    totals = Counter(f["kind"] for f in findings)
    classifications = Counter(f["classification"] for f in findings)
    review = [f for f in findings if f["classification"] == "review"]
    report = {
        "source_files_scanned": sum(row["files"] for row in modules),
        "ui_files_scanned": sum(row["ui_files"] for row in modules),
        "candidate_lines": len(findings),
        "by_kind": dict(sorted(totals.items())),
        "by_classification": dict(sorted(classifications.items())),
        "review_candidates": len(review),
        "modules": modules,
        "findings": findings,
    }
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    md = [
        "# YSuite full-source YUI appearance audit",
        "",
        "Candidate matches are not necessarily defects. UI aesthetic constants are migration",
        "candidates; hit areas, physical screen geometry and protocol constants need exemptions.",
        "",
        f"Scanned {report['source_files_scanned']} main-source files "
        f"({report['ui_files_scanned']} UI-related); {len(findings)} candidate matches.",
        "",
        "| Module | Sources | UI-related | Candidates |",
        "|---|---:|---:|---:|",
    ]
    md.extend(
        f"| {item['module']} | {item['files']} | {item['ui_files']} | {item['findings']} |"
        for item in modules
    )
    md += ["", f"Strict review candidates: {len(review)}", "",
           "## Classification", ""]
    md.extend(f"- {kind}: {amount}" for kind, amount in sorted(classifications.items()))
    md += ["", "## Candidate kinds", ""]
    md.extend(f"- {kind}: {amount}" for kind, amount in sorted(totals.items()))
    md += ["", "## Candidate locations", ""]
    md.extend(
        f"- [{item['classification']}/{item['kind']}] {item['file']}:{item['line']} — "
        f"{item['excerpt'].replace('|', chr(92) + '|')}"
        for item in findings
    )
    if args.markdown:
        args.markdown.parent.mkdir(parents=True, exist_ok=True)
        args.markdown.write_text("\n".join(md) + "\n", encoding="utf-8")
    print(
        f"yui-full-audit: scanned {report['source_files_scanned']} source files; "
        f"{report['ui_files_scanned']} UI-related; "
        f"{len(findings)} matches; {len(review)} strict review candidates"
    )
    print("  classifications: " + ", ".join(
        f"{kind}={amount}" for kind, amount in sorted(classifications.items())
    ))
    per_module = Counter(f["module"] for f in review)
    for module, count in per_module.most_common():
        print(f"  {module}: {count} strict review candidates")
    print("yui-full-audit: inventory complete; see JSON/Markdown for exact locations")

if __name__ == "__main__":
    main()
