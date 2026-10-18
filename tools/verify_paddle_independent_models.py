#!/usr/bin/env python3
"""Reject Paddle model weights embedded in the APK source tree.

Paddle YFloat should ship engine code only: every PP-OCR, layout, orientation,
table, UVDoc, seal and FormulaNet model must be installed independently by explicit action.
This static guard is run once in the architecture validation workflow.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "apps" / "YFloat"
WEIGHTS = {".onnx", ".pdiparams", ".pdmodel", ".safetensors",
           ".tflite", ".gguf", ".pth", ".pt", ".ckpt"}
problems = []
for path in FEATURE.rglob("*"):
    if path.is_file() and path.suffix.lower() in WEIGHTS:
        problems.append("Embedded model weight: " + str(path.relative_to(ROOT)))

java = (FEATURE / "feature/src/main/java/com/yagay/YFloat")
registry = (java / "PaddleLocalModelsV3.java").read_text()
capture = (java / "SettingsCapturePage.java").read_text()
local_ui = (java / "PaddleLocalModelSettingsV3.java").read_text()
manager = (java / "OcrModelManager.java").read_text()
layout_ui = (java / "PaddleStructureSettingsV2.java").read_text()
for needle in ("DOC_ORIENTATION", "TABLE_STRUCTURE", "UV_DOC", "SEAL_DETECTION", "FORMULA_NET"):
    if needle not in registry or needle not in local_ui:
        problems.append("Missing independent Paddle download option: " + needle)
if "PaddleLocalModelSettingsV3.add(" not in capture:
    problems.append("Local independent model controls not in OCR settings")
if "PaddleStructureSettingsV2.add(" not in capture:
    problems.append("Layout model control not in OCR settings")
for needle in ("OcrModelManager.TINY", "OcrModelManager.SMALL",
               "OcrModelManager.MEDIUM", "OcrModelManager.MOBILE"):
    if needle not in capture:
        problems.append("Missing independent base OCR model row: " + needle)
for needle in ("void download(", "void delete("):
    if needle not in manager:
        problems.append("Base OCR download/delete API missing: " + needle)
if "setEnabled" not in layout_ui or "PaddleStructureModelV2.download" not in layout_ui:
    problems.append("Independent layout control incomplete")
pipeline = (java / "PaddlePipelineV2.kt").read_text()
document = (java / "OcrDocument.java").read_text()
formula = (FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/structure/PPFormulaNetLocalV3.kt").read_text()
if "PaddleLocalModelsV3.formulaTokenizer(" not in pipeline:
    problems.append("FormulaNet tokenizer is not connected to offline inference")
if "PaddleChartTextV3.extract(page)" not in pipeline:
    problems.append("Chart label parser not connected to offline OCR")
if "withFormulaTexts(" not in document or "withChartTexts(" not in document:
    problems.append("Structured formula/chart OCR metadata missing")
if "OnnxTensor.createTensor(" not in formula or "decodeByteLevel(" not in formula:
    problems.append("Formula inference or LaTeX tokenizer decoder missing")
manager = (java / "OcrModelManager.java").read_text()
ocr_engine = (java / "OcrEngine.java").read_text()
selector = (java / "SettingsPageUi.java").read_text()
circle = (java / "CircleStableOcr.java").read_text()
if "PP-OCRv5_mobile_det_onnx" not in manager or "PP-OCRv5_mobile_rec_onnx" not in manager:
    problems.append("PP-OCRv5 Mobile ONNX download sources missing")
if "case MOBILE" not in manager or '"ppocrv5_mobile"' not in manager:
    problems.append("Mobile model must have a distinct private model directory")
if "OcrModelManager.MOBILE" not in ocr_engine or "OcrModelManager.MOBILE" not in circle:
    problems.append("Mobile model not connected to normal/circle OCR")
if "PP-OCRv5 Mobile" not in selector:
    problems.append("Mobile model not selectable from YFloat OCR settings")
if "PaddleStructureFullV3" in capture or (java / "PaddleStructureFullV3.java").exists():
    problems.append("Remote PP-Structure recognition unexpectedly restored")
# OCR correctness and model-selection contracts. These static checks complement
# (but never replace) Android instrumentation and image-based inference tests.
spacing = (FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/postprocess/PaddleSpacingV2.kt").read_text()
table = (java / "PaddleTableTextV3.kt").read_text()
page = (java / "PaddlePageV2.kt").read_text()
slanet = (FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/structure/SLANetLocalV3.kt").read_text()
for needle in ("safeCropRect(region.bounds,image,16)", "safeCropRect(region.bounds,image,8)"):
    if needle not in pipeline:
        problems.append("Optional Paddle crop is not source-bitmap bounded: " + needle)
if "val compat=lock.withLock" not in pipeline or "withContext(Dispatchers.Main)" not in pipeline:
    problems.append("Paddle result snapshot must be constructed inside serialized inference")
if "if(fallbackText.isNotBlank() && aligned!=fallbackText)" not in spacing:
    problems.append("CTC alignment cannot silently truncate full recognizer text")
if "visuallyTight(prev,next" not in spacing:
    problems.append("Paddle spacing correction must use geometry evidence")
if "cellLines.joinToString(\"\\n\")" not in table:
    problems.append("Table cells must preserve distinct OCR line boundaries")
if "val quality=confidence*1000.0" not in page or "confidence,quality,width,height" not in page:
    problems.append("OCR quality score is not persisted for automatic model escalation")
if "val hasOpen=tokens.any" not in slanet or "val hasClose=tokens.any" not in slanet:
    problems.append("SLANet HTML must not duplicate supplied table tags")
if "OcrModelManager.TINY, true" not in ocr_engine or "mode == 6" not in ocr_engine:
    problems.append("Tiny fallback / explicit selection missing from main OCR")
if "selected == 6 ? 5" not in selector:
    problems.append("Tiny model not reachable from primary OCR settings")
if "mode == 5" not in circle or "selected=paddle_auto" not in circle:
    problems.append("Circle full-screen OCR must expose shared offline Paddle Auto")
if 'return 5;' not in (java / "FloatSettings.java").read_text():
    problems.append("New Circle installations must default to Paddle Auto")

if problems:
    print("FAIL: Independent Paddle model policy")
    for problem in problems:
        print(" -", problem)
    sys.exit(1)
print("OK: no bundled Paddle weights; supported models are independently managed")
