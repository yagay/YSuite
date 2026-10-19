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
runtime=(java / "PaddleRecognitionRuntime.java").read_text()
if "OcrModelManager.MOBILE" not in runtime or "OcrModelManager.TINY" not in runtime:
    problems.append("All installed Paddle model sizes must be selectable")
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
if "buckets[i].joinToString(\"\\n\")" not in table:
    problems.append("Table cells must preserve distinct OCR line boundaries")
if "val quality=confidence*1000.0" not in page or "confidence,quality,width,height" not in page:
    problems.append("OCR quality score is not persisted for automatic model escalation")
if "val hasOpen=tokens.any" not in slanet or "val hasClose=tokens.any" not in slanet:
    problems.append("SLANet HTML must not duplicate supplied table tags")
if "PaddleRecognitionRuntime.normalModel" not in ocr_engine:
    problems.append("Normal OCR is not delegated to the unified Paddle runtime")
if "PaddleRecognitionRuntime.circleModel" not in circle:
    problems.append("Circle OCR is not delegated to the unified Paddle runtime")
if "selected == 5 ? 3 : selected == 6 ? 4" not in selector:
    problems.append("Tiny and Mobile model picker indices must map to real Paddle ids")
if "choice==0?5:choice" not in selector:
    problems.append("Circle Paddle Auto picker mapping is missing")
if "return stored<1||stored>5?5:stored;" not in (java / "FloatSettings.java").read_text():
    problems.append("Legacy Circle ML Kit choice must migrate to Paddle Auto")

# Comprehensive local OCR batch: the runtime modules must be actually wired,
# not merely advertised in settings. Compiling and running ONNX is separately required.
exporter = (java / "PaddleDocumentExport.java").read_text()
pdf = (java / "PaddlePdfImportActivity.java").read_text()
policy = (java / "PaddleRuntimePolicy.java").read_text()
orientation = (FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/structure/PPTextLineOrientationV3.kt").read_text()
sdk = (FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt").read_text()
reader = (java / "PaddleReadingOrderV3.kt").read_text()
manifest = (FEATURE / "feature/src/main/AndroidManifest.xml").read_text()
for needle in ("jsonPages(", "markdownPages(", "csvTables(", "html(",
               "coordinateSpace", "selectionUnits", "elements"):
    if needle not in exporter:
        problems.append("Paddle structured exporter missing: " + needle)
for needle in ("PdfRenderer", "PaddlePipelineV2.recognize(", "CreateDocument(",
               "OpenDocument()", "image.recycle()", "closePdf()"):
    if needle not in pdf:
        problems.append("Local PDF import/export contract missing: " + needle)
if "PaddlePdfImportActivity" not in manifest or "PaddlePdfImportActivity" not in capture:
    problems.append("PDF OCR activity not exposed from settings/manifest")
if "PaddleLocalModelsV3.TEXTLINE_ORIENTATION" not in local_ui:
    problems.append("Independent textline model download row missing")
if "PP-LCNet_x1_0_textline_ori_onnx" not in registry or "38aa97cd4be591" not in registry:
    problems.append("Textline orientation weights must be pinned to a verified ONNX SHA")
if "PPTextLineOrientationV3" not in sdk or "rotatedFlags" not in sdk:
    problems.append("Textline classifier not attached before CTC recognizer")
if "lineModel).also" not in pipeline or "PaddleLocalModelsV3.TEXTLINE_ORIENTATION" not in pipeline:
    problems.append("Paddle pipeline does not reload verified textline classifier")
if "PaddleRuntimePolicy.forModel" not in pipeline or "lowMemory" not in policy:
    problems.append("Paddle runtime memory policy not connected")
if "val options=(1..9).mapNotNull" not in reader:
    problems.append("Validated multi-column geometric reading order missing")
if "word.text,realWord" not in page or "line.text,detectorBox" not in page:
    problems.append("Selection fallback must use provenance-aware real word/line bounds")

# Fully local OCR result ownership and non-destructive downloads.
dispatcher=(java / "OcrResultDispatcher.java").read_text()
router=(java / "ResultSurfaceRouter.java").read_text()
session=(java / "ResultSession.java").read_text()
dialog=(java / "UnifiedResultDialogFragment.java").read_text()
result_host=(java / "ResultActivity.java").read_text()
model_ui=(java / "PaddleLocalModelSettingsV3.java").read_text()
for needle in ("OcrDocument document", "sink.onResult(value, safeBlocks, document)",
               "image, anchor, document"):
    if needle not in dispatcher:
        problems.append("OCR document is lost on result dispatch: " + needle)
if "ResultSession.ocr(text, blocks, image, sourceBounds,document)" not in router:
    problems.append("Result router loses Paddle document metadata")
if "applyOcr(String text, List<String> blocks,OcrDocument document)" not in session:
    problems.append("Inline result session loses structured OCR metadata")
if "applyInlineOcr(gen, text, blocks, document)" not in dialog:
    problems.append("Inline OCR result must preserve its structured document")
for needle in ("PaddleDocumentExport.markdown(selected)",
               "PaddleDocumentExport.json(selected)",
               "PaddleDocumentExport.html(selected)",
               "PaddleDocumentExport.csvTables(selected)"):
    if needle not in result_host:
        problems.append("Offline Save dialog lacks document output: " + needle)
for needle in ("modelStage=stageFile(dst)", "swapWithBackup(modelStage,dst)",
               "rollbackBackup(dst,hadPrimary)", "recoverBackup(dst)"):
    if needle not in registry:
        problems.append("Independent model replacement must preserve existing files: " + needle)
if "PaddleLocalModelsV3.check(activity,id," not in model_ui:
    problems.append("Model integrity verification not exposed in UI")
if "cancelButton.setOnClickListener" not in pdf:
    problems.append("Long offline PDF inference lacks cancellation")

# Last-batch contracts: honest geometry provenance, conservative raster cell
# detection, one-owner word-to-cell linking, and bounded PDF export memory.
ruled=(java / "PaddleRuledGridV4.kt").read_text()
ocr_doc=(java / "OcrDocument.java").read_text()
page=(java / "PaddlePageV2.kt").read_text()
for needle in ("PaddleRuledGridV4.detect(crop)", "grid.prediction to grid.source",
               "PaddleTableTextV3.link(it.first,page.lines,it.second)"):
    if needle not in pipeline:
        problems.append("Real raster-grid table fallback not wired: " + needle)
for needle in ("fun detect(bitmap:Bitmap)", "rowHits", "colHits",
               "good<all*.92f", "pixel_ruled_grid"):
    if needle not in ruled:
        problems.append("Ruled-table fallback needs conservative physical-grid evidence: " + needle)
for needle in ("winningCell(", "val buckets=Array(", "source:String="):
    if needle not in table:
        problems.append("Table OCR text must be assigned to unique cells: " + needle)
if ".withTableOrigins(linked.map{it.source})" not in pipeline:
    problems.append("Table model vs raster provenance lost in result document")
if '"tableOrigins", strings(d.tableOrigins())' not in exporter:
    problems.append("Structured export loses table provenance")
if "RECTIFIED_BITMAP" not in ocr_doc or "withRectifiedDocument" not in ocr_doc:
    problems.append("UVDoc corrected-page geometry is not isolated")
if "asRectifiedBitmapSpace()" not in pipeline or "withRectifiedDocument(latestUnwarpedDocument)" not in pipeline:
    problems.append("UVDoc document falsely exposes corrected geometry as source")
if '"rectifiedCoordinateMapping","not_available"' not in exporter:
    problems.append("UVDoc export must explicitly mark nonrigid inverse as unavailable")
if "fun withinImage(q:Quad)" not in page or "lineQuad=withinImage(" not in page:
    problems.append("Paddle text selection geometry may escape bitmap boundaries")
for needle in ("File.createTempFile(", "appendPage(pageIndex,document)",
               "exportWriter.flush()", "new FileInputStream(ready)",
               "saveDraftButton.setOnClickListener", "image=renderPage(page)"):
    if needle not in pdf:
        problems.append("Offline PDF streaming/retry/bitmap lifecycle contract missing: "+needle)
if "results.add(document)" in pdf or "ArrayList<OcrDocument> results" in pdf:
    problems.append("PDF OCR must not retain unbounded page documents in RAM")
# Paddle-native runtime must have no ML Kit execution, no old canonical glyph
# splitting, and one shared inference/cache owner.
bridge=(java / "PaddleOcrBridge.kt").read_text()
gradle=(FEATURE / "feature/build.gradle").read_text()
for source,name in ((ocr_engine,"OcrEngine"),(circle,"CircleStableOcr"),
                    (bridge,"PaddleOcrBridge")):
    if ("MlKit" in source or "TextRecognizer" in source or
            "OcrCanonicalGeometry.normalize" in source):
        problems.append(name+" retained a legacy ML Kit execution/geometry path")
if (java / "MlKitTextCore.java").exists() or (java / "OcrCanonicalGeometry.java").exists():
    problems.append("Unused ML Kit geometry/parsing sources still ship")
# ML Kit may run as an optional isolated visual geometry assistant.
# Its recognition text must NEVER reach Paddle's OCR engine or clipboard.
if ("implementation(libs.mlkit.text.recognition)" not in gradle or
        "implementation(libs.mlkit.text.recognition.chinese)" not in gradle):
    problems.append("Hybrid geometry requires on-device Latin and Chinese symbol models")
if "PaddlePipelineV2.recognize(context,bitmap,model" not in bridge:
    problems.append("Retired second Paddle bridge must only delegate to the primary pipeline")
if "PaddleRecognitionRuntime.recognize" not in ocr_engine or "PaddleRecognitionRuntime.recognize" not in circle:
    problems.append("Normal and Circle must use one model/runtime contract")
if "(Paddle offline)" not in selector:
    problems.append("User-facing Paddle Auto label must identify its offline engine")

# OCR source text is authoritative for copied Circle spacing. Group ids are
# selection geometry and must never be treated as invented word separators.
circle_selection=(java / "CircleTextSelectionModel.java").read_text()
for needle in ("OcrSelectionText.align(source.text(),glyphs,li)",
               "sourceRanges.put(units.get(i),ranges.get(i))",
               "OcrSelectionText.selected(text,sourceSpans,indexes,lines)",
               "sourceSpans=Collections.unmodifiableList(normalizedRanges)"):
    if needle not in circle_selection:
        problems.append("Circle copy must use exact OCR source text spans: "+needle)
selection_helper=(java / "OcrSelectionText.java").read_text()
for needle in ("static List<Span> align(", "static String selected(",
               "current.source,previous.end,current.end", "current.ordinal==previous.ordinal+1"):
    if needle not in selection_helper:
        problems.append("Original OCR UTF-16 text-offset selection missing: "+needle)
if "sourceSpaceBefore" in circle_selection or "originalSpacing==1" in circle_selection:
    problems.append("Old group-based whitespace reconstruction still active")
if not (FEATURE / "feature/src/test/java/com/yagay/YFloat/OcrSelectionTextTest.java").is_file():
    problems.append("Missing OCR source span selection regression test")
for needle in ("normalizeCjkSpacing(fallbackText)",
               "fun normalizeCjkSpacing(text:String)",
               "joinAroundCjk(", "countCandidateSpaces("):
    if needle not in spacing:
        problems.append("Offline CJK spacing cannot repair incomplete CTC alignments: "+needle)
spacing_test=(FEATURE / "ppocr-sdk/src/test/java/com/paddle/ocr/postprocess/PaddleSpacingV2Test.kt")
if not spacing_test.is_file():
    problems.append("CJK spacing regressions are not covered by local unit cases")

# Deep Circle/Paddle whitespace regression: no geometry-made lexical
# separators and no stale CTC whitespace geometry after normalization.
gesture_selector=(java / "CircleGestureTextSelector.java").read_text()
if "shouldInsertSpace(" in gesture_selector or "lineText.append(' ')" in gesture_selector:
    problems.append("Circle gesture path still invents spaces from group positions")
for needle in ("IdentityHashMap<OcrDocument.CharUnit,OcrSelectionText.Span>",
               "OcrSelectionText.align(", "OcrSelectionText.selected("):
    if needle not in gesture_selector:
        problems.append("Circle gesture has no source-offset text mapping: "+needle)
if "current.source.equals(previous.source)" not in selection_helper:
    problems.append("OCR selections may bridge source offsets across different lines")
if "stripCjkWhitespaceTokens(characters)" not in spacing or "for (i in cjkCleaned.indices)" not in spacing:
    problems.append("CTC output text and selectable glyphs can keep contradictory spaces")
if "PADDLE_SPACE_TRACE" not in pipeline or "rawSpaces=" not in pipeline:
    problems.append("CTC pre/post spacing stage lacks privacy-safe diagnostics")
planner=(java / "CircleSelectionPlanner.java").read_text()
if "PADDLE_SPACE_TRACE" not in planner or "selectedSpaces=" not in planner:
    problems.append("Circle selection text spacing stage lacks diagnostics")

# The new native workspace is the path actually exercised by user Circle
# gestures. It must never reintroduce spaces at group/line boundaries.
native=(java / "NativeCircleWorkspace.java").read_text()
for needle in ("rebuildTextOffsets();", "OcrSelectionText.align(",
               "OcrSelectionText.selected(tokens,offsets,indices,lineNumbers)",
               "stage=native_selection", "sourceSpans.clear();"):
    if needle not in native:
        problems.append("Active native Circle OCR text path lacks source offset handling: "+needle)
if "text.append(' ')" in native or "previous.group()!=ch.group()" in native:
    problems.append("Native Circle still fabricates whitespace from glyph groups")
if "nativeCircleThreeGroupsNeverCreateAnExtraSpace" not in (
    FEATURE / "feature/src/test/java/com/yagay/YFloat/OcrSelectionTextTest.java"
).read_text():
    problems.append("Native Circle multi-glyph whitespace case is not tested")

# Highlight strokes are text intents: never turn into rectangular crops just
# because PP-OCRv5 Mobile needs longer than four seconds.
router=(java / "NativeCircleIntentRouter.java").read_text()
for needle in ("permitsImageFallback(String kind)",
               'return !"HIGHLIGHT".equals(kind);'):
    if needle not in router:
        problems.append("Highlight image fallback policy is missing: "+needle)
for needle in ("awaitingTextPath.set(dragPath)",
               "canvas.drawPath(awaitingTextPath, outlinePaint)",
               "rejectTextGesture(gesture,token",
               "highlight?25000L:4000L",
               "highlight unresolved reason="):
    if needle not in native:
        problems.append("Native Circle drawing/OCR must preserve highlight intent: "+needle)
if 'text OCR timeout; falling back to image crop' in native:
    problems.append("Old 4s unconditional highlight-to-rectangle fallback still active")
native_test=(FEATURE / "feature/src/test/java/com/yagay/YFloat/NativeCircleIntentRouterTest.java")
if not native_test.is_file():
    problems.append("Highlight-only no-image-fallback policy has no regression test")

# Fast Circle and document OCR must run one shared Paddle/ORT detector and
# recognizer, with per-profile switches wired to REAL executable stages.
flow_options=(java / "PaddleFlowOptions.java").read_text()
flow_ui=(java / "PaddleFlowSettingsUi.java").read_text()
capture=(java / "SettingsCapturePage.java").read_text()
runtime=(java / "PaddleRecognitionRuntime.java").read_text()
circle=(java / "CircleStableOcr.java").read_text()
pipeline=(java / "PaddlePipelineV2.kt").read_text()
paddle=(FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/PaddleOCR.kt").read_text()
engine=(FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/engine/OCREngine.kt").read_text()
detector=(FEATURE / "ppocr-sdk/src/main/java/com/paddle/ocr/engine/DetectionEngine.kt").read_text()
for key in ("extended","fast_detection","line_orientation","document_orientation",
            "rotation_fallback","layout","tables","uvdoc","seals","formulas",
            "charts","spacing"):
    if ('"'+key+'"') not in flow_ui or ('"'+key+'"') not in flow_options:
        problems.append("Missing individual Paddle execution switch: "+key)
if "PaddleFlowSettingsUi.add(activity,fs,circleOcr.body,true)" not in capture or (
        "PaddleFlowSettingsUi.add(activity,fs,result.body,false)" not in capture):
    problems.append("Circle and normal-document OCR settings profiles are not surfaced")
if "PaddleFlowOptions.read(app,circle)" not in runtime or (
        "PaddleRecognitionRuntime.recognize(app,bitmap,model,true" not in circle):
    problems.append("Circle OCR is not routed to its own stage policy")
for x in ("releaseDisabled(options)", "options.lineOrientation", "options.documentOrientation",
          "options.rotationFallback", "options.layout", "options.tables",
          "options.uvdoc", "options.seals", "options.formulas", "options.charts",
          "options.spacing", "options.fastDetection", "ocr.recognizeFast("):
    if x not in pipeline:
        problems.append("Paddle stages do not respect their switches: "+x)
if "engine.runFast(bitmap,maxDetectionSide)" not in paddle or (
        "detectionEngine.detect(srcMat,maxDetectionSide)" not in engine) or (
        "maxSideOverride" not in detector):
    problems.append("Fast detector must use original bitmap text crops and coordinates")
if not (FEATURE / "feature/src/test/java/com/yagay/YFloat/PaddleFlowOptionsTest.java").is_file():
    problems.append("Missing per-profile settings unit tests")

# Current native Circle must defer OCR until a text gesture, then use the
# correct original-screen coordinate mapping for ROI and select-all.
native=(java / "NativeCircleWorkspace.java").read_text()
runtime=(java / "PaddleRecognitionRuntime.java").read_text()
pipeline=(java / "PaddlePipelineV2.kt").read_text()
stable_circle=(java / "CircleStableOcr.java").read_text()
for needle in ("startOcr(roi,false);", "startOcr(null,true);",
               "frame.transform.documentLocalToScreen(result,roi,",
               "new CircleCharSpatialIndex(cachedChars)",
               "PaddleCircleRoiPolicy.forGesture(",
               "retryWholeForHighlight(", "activeOcr.cancel()"):
    if needle not in native:
        problems.append("Active native Circle fast ROI/selection contract missing: "+needle)
if "view.startOcr();" in native or "text.append(' ')" in native:
    problems.append("Native Circle still does eager OCR or manufactures spaces")
for needle in ("new PaddleRequestToken()", "boolean circle,PaddleRequestToken request",
               "request.cancelled()", "boolean ambiguous=!circle"):
    if needle not in runtime:
        problems.append("Circle runtime lacks safe cancel or single-pass behavior: "+needle)
for needle in ("PaddleRequestToken", "request.check()", "phase=start waitMs=",
               "alternateModels.remove(model)", "session=variant_reused"):
    if needle not in pipeline:
        problems.append("Paddle inference cancellation/cache contract missing: "+needle)
if "PaddleRecognitionRuntime.recognize(app,bitmap,model,true,request," not in stable_circle:
    problems.append("Circle input bitmap is not protected by a cancellable OCR request")
ui=(java / "PaddleFlowSettingsUi.java").read_text()
for needle in ("addPresetButton(", '"fast"', '"balanced"', '"accurate"',
               "YViewLayout.switchRow(", "dependent.setEnabled(checked)"):
    if needle not in ui:
        problems.append("Missing YUI quick OCR presets or advanced setting dependencies: "+needle)
for name in ("PaddleRequestPerformanceTest.java","CircleCharSpatialIndexTest.java"):
    if not (FEATURE / "feature/src/test/java/com/yagay/YFloat" / name).is_file():
        problems.append("Missing OCR performance regression case: "+name)

# Screenshot regression: native Circle selection must not bridge unrelated
# launcher icon glyphs and app captions, and highlight must not re-render OCR
# letters with an invented app font.
native=(java / "NativeCircleWorkspace.java").read_text()
selector=(java / "CircleGestureTextSelector.java").read_text()
transform=(java / "ScreenBitmapTransform.java").read_text()
line_policy=(java / "CircleTextRangePolicy.java").read_text()
for needle in ("lineLocalRangeHits(document,touchedChars,focus)",
               "CircleTextRangePolicy.lineScore(",
               "CircleTextRangePolicy.splitsLabel("):
    if needle not in selector:
        problems.append("Gesture line-local selection regressed: "+needle)
for needle in ("selection.initialize(first,last,initialLine)",
               "CircleMlBlockSelection.build(fullDocument,mlAlignment)",
               "for(int i:visualMask.orderedIndices)",
               "CircleMlSelectionVisual.build(",
               "CircleMlSelectionVisual.draw(",
               "rebuildSelectionGeometry();",
               "WindowInsets.Type.statusBars()", "float pillTop=safeToolbarTop();",
               "float top=safeToolbarTop();"):
    if needle not in native:
        problems.append("Native Circle Paddle text / ML visual path missing: "+needle)
if "canvas.drawText(ch.text()" in native:
    problems.append("Native Circle must never re-typeset OCR text")
if "RectF screenToView(RectF screenRect" not in transform:
    problems.append("Selection highlighter lost fractional coordinate precision")
for needle in ("static float lineScore(", "static boolean splitsLabel(",
               "static boolean sameLine("):
    if needle not in line_policy:
        problems.append("Missing line-boundary selection policy: "+needle)
if not (FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleTextRangePolicyTest.java").is_file():
    problems.append("Missing icon-caption same-line selection regression tests")

# Original main/FLCircleInlineOverlay ML text style is now an independent
# renderer; do not mutate the OCR engine or paint per-character clipped frames.
native=(java / "NativeCircleWorkspace.java").read_text()
ml_visual=(java / "CircleMlSelectionVisual.java").read_text()
for marker in ("ML_BLUE_WASH = 0x884285F4",
               "ML_BLUE_HANDLE = 0xFF4285F4",
               "CORNER_DP = 2f",
               "HANDLE_STEM_DP = 7f",
               "HANDLE_RADIUS_DP = 7f",
               "canvas.drawRoundRect(view,corner,corner,selectedPaint)",
               "canvas.drawCircle(start.left,start.bottom+stem,radius,gripPaint)",
               "canvas.drawCircle(end.right,end.bottom+stem,radius,gripPaint)",
               "static Layout build(", "static Handles draw(",
               "static boolean mayMerge("):
    if marker not in ml_visual:
        problems.append("Main ML Kit visual style not preserved: "+marker)
for marker in ("CircleMlSelectionVisual.draw(", "CircleMlSelectionVisual.build(",
               "CircleMlSelectionVisual.selectedViewBox(",
               "CircleMlSelectionVisual.ML_BLUE_WASH",
               "CircleMlSelectionVisual.ML_BLUE_HANDLE",
               "CircleHybridSelectionGeometry.align(",
               "maybeStartMlGeometry();",
               "CircleMlKitGeometry.recognize(",
               'selected_groups="+selectionVisual.segments.size()',
               "startDistance=(float)Math.hypot(x-firstTextGrip.x,y-firstTextGrip.y)",
               "endDistance=(float)Math.hypot(x-lastTextGrip.x,y-lastTextGrip.y)",
               "return mlTextSelection.hit(screen.x,screen.y,18f*density)"):
    if marker not in native:
        problems.append("Main-ML UI/Paddle-OCR pipeline disconnected: "+marker)
for retired in ("canvas.clipPath(selection)", "pathForQuad(",
                "drawTextHandles(Canvas", "textGeometry=TextGeometryV3.build(",
                "CircleHybridSelectionGeometry.grouped("):
    if retired in native:
        problems.append("Retired Circle selection renderer still active: "+retired)
if "canvas.drawBitmap(frame.bitmap,null," in native:
    problems.append("Selected text must not redraw the full screenshot")
ml_test=FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleMlSelectionVisualTest.java"
if not ml_test.is_file() or (
        "oldMlKitBlueWashColorIsRestored" not in ml_test.read_text()):
    problems.append("Original ML visual style regression suite missing")
# Free text-handle expansion is NOT constrained by the initial ROI or row.
# Initial highlighting is still row-local; a deliberate handle drag unlocks
# all available lines and expands OCR asynchronously to the full screenshot.
expansion=(java / "CircleTextExpansionPolicy.java").read_text()
for needle in ("startOcr(null,false,true)",
               "selection.beginDrag(textHandle);",
               "CircleTextExpansionPolicy.remap(",
               "selection.moveTo(hit);",
               "mlTextSelection.hit(",
               "expansionDragCursorValid", "recognizedRoi=oldRoi",
               "if(expandForHandle)", "oldDocument=fullDocument",
               "paddleReference=circleGeometryMode==CircleHybridSelectionGeometry.COMPARE"):
    if needle not in native:
        problems.append("Unrestricted screenshot OCR selection path missing: "+needle)
for needle in ("new Range(last,target,END)",
               "new Range(target,first,START)", "remapScore(",
               "boxDistance(", "best<=14f?index:-1"):
    if needle not in expansion:
        problems.append("Unrestricted selection/remapping policy missing: "+needle)
for prohibited in ("if(!CircleTextRangePolicy.sameLine(unit.line(),selectedLine))continue;\n            RectF rect",
                   "if(handleDrag==1)firstChar=Math.min(candidate,lastChar)",
                   "return best<0 || bestScore>maxDistance*maxDistance?-1:best;"):
    if prohibited in native:
        problems.append("Old OCR line/radius/endpoint limiter still active")
free_test=FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleTextExpansionPolicyTest.java"
if not free_test.is_file():
    problems.append("Unrestricted selection regression test missing")
else:
    free_cases=free_test.read_text()
    for case in ("startHandleCanExpandBeyondFirstOcrLine",
                 "endHandleCanExpandAcrossManyLinesWithoutCap",
                 "bothHandlesCrossAndSwapTheirActiveRole",
                 "remapPrefersScreenPositionOverStringDuplicates",
                 "no96DpLimitOnDistanceFallback"):
        if case not in free_cases:
            problems.append("Unrestricted selection regression missing "+case)

# ML Kit block/line/word/symbol hierarchy now drives selection membership,
# while only Paddle may supply text and source whitespace.
ml_blocks=(java / "CircleMlBlockSelection.java").read_text()
for marker in ("static CircleMlBlockSelection build(",
               "CircleMlKitGeometry.Glyph g=alignment==null?null:alignment.glyph(c)",
               "static boolean connected(", "int blockOf(",
               "int[] initialRange(", "int hit(", "Mask select(",
               "Set<Integer> visited", "active.add(a);active.add(b);",
               "if(visited!=null)active.addAll(visited)",
               "visualRank[i]", "if(selectAll)",
               "return new Mask(flags,selected,rejected,active.size())",
               "new Mask(flags,all,0,components)",
               "nearby.computeIfAbsent(bucket(col,row)",
               "int compare(int a,int b)", "Move move(",
               "final List<Integer> orderedIndices"):
    if marker not in ml_blocks:
        problems.append("ML Kit block-selection contract missing: "+marker)
for marker in ("CircleMlBlockSelection.build(",
               "mlTextSelection.initialRange(", "mlTextSelection.hit(",
               "mlInitialGesture", "resolveMlGesture(",
               "selection.initialize(", "selection.beginDrag(",
               "selection.moveTo(", "selection.chooseAll(",
               "selection.rebase(", "selection.bind(",
               "CircleMlSelectionVisual.build(", "CircleMlSelectionVisual.draw(",
               "for(int i:visualMask.orderedIndices)",
               "mlTextSelection.copyLineId(i)",
               "String value=OcrSelectionText.selected(tokens,offsets,indices,lineNumbers)"):
    if marker not in native:
        problems.append("ML Kit selection / Paddle text link missing: "+marker)
if "CircleSelectionViewportPolicy.select(" in native or "CircleTextExpansionPolicy.move(" in native:
    problems.append("Retired Paddle global-index movement still controls Circle selection")
session=(java / "CircleMlSelectionSession.java").read_text()
for marker in ("void initialize(", "void chooseAll(", "void beginDrag(",
               "boolean moveTo(", "void rebase(", "void bind(",
               "void resumeDrag(", "void clearVisited(", "void reset(",
               "Mask mask(", "trace.subList(", "replayTrace()",
               "topology.move(first,last,movingHandle,target)"):
    if marker not in session:
        problems.append("Canonical ML selection session contract missing: "+marker)
session_test=FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleMlSelectionSessionTest.java"
if not session_test.is_file():
    problems.append("Canonical ML selection session tests absent")
else:
    cases=session_test.read_text()
    for marker in ("reverseDragRemovesNoLongerVisitedAppLabels",
                   "invertedPaddleOrdinalsNormalizeByMlVisualOrder",
                   "rebuildingMlBlocksReplaysThePhysicalDragPath",
                   "immutableReturnedSelectionFollowsMlReadingOrder"):
        if marker not in cases:
            problems.append("ML selection session regression missing: "+marker)
geometry_provider=(java / "CircleMlKitGeometry.java").read_text()
for marker in ('String blockId=script+":block:"+blockIndex++',
               "int currentLine=lineIndex++", "int currentWord=wordIndex++",
               "symbol.getCornerPoints()"):
    if marker not in geometry_provider:
        problems.append("ML Kit text hierarchy not captured: "+marker)
hybrid=(java / "CircleHybridSelectionGeometry.java").read_text()
for marker in ("CircleMlKitGeometry.Glyph glyph(", "out.put(c,ml.get(best))"):
    if marker not in hybrid:
        problems.append("ML Kit symbol to Paddle character link missing: "+marker)
for marker in ("!sameGroup||!consecutive", "verticalOverlap<smallerHeight*.55f",
               "horizontalGap>Math.max(3f,smallerHeight*.45f)",
               "new Layout(boxes,first,last,union,count,matched)"):
    if marker not in ml_visual:
        problems.append("ML visual-group contract missing: "+marker)
block_test=FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleMlBlockSelectionTest.java"
if not block_test.is_file():
    problems.append("ML Kit block selection regressions absent")
else:
    cases=block_test.read_text()
    for marker in ("desktopLauncherCaptionInAnotherColumnNeverAutoSelects",
                   "touchedAdditionalBlockCanJoinWithoutOthers",
                   "erroneousMlTextBlockWithTwoDistantIconsIsSplit",
                   "physicalTextLinesWithinSameMlBlockCanExtend",
                   "emptyGapDoesNotSnapToFarAwayIcon",
                   "mlTapFindsOnlyTheTouchedWordNotAdjacentLauncher",
                   "mlHighlightIgnoresOtherBlockEvenWhenStrokeTouchesIt"):
        if marker not in cases:
            problems.append("ML Kit block selection regression missing: "+marker)

# Real hybrid recognition is isolated: Paddle supplies selection text, ML Kit
# supplies only nearby, text-matched character QUADS for the renderer.
hybrid=(java / "CircleHybridSelectionGeometry.java").read_text()
geometry_provider=(java / "CircleMlKitGeometry.java").read_text()
prefs=(java / "FloatSettings.java").read_text()
ui=(java / "SettingsPageUi.java").read_text()
for source,area,needles in (
    (hybrid,"alignment",("byToken.get(expected)",
                        "matchScore(a,b)", "used[best]=true",
                        "out.put(c,ml.get(best))")),
    (geometry_provider,"ML Kit geometry",(
                        "new ChineseTextRecognizerOptions.Builder()",
                        "TextRecognizerOptions.DEFAULT_OPTIONS",
                        "symbol.getCornerPoints()",
                        "symbol.getBoundingBox()",
                        "transform.bitmapToScreen(",
                        "input.recycle()", "zh.close()", "latin.close()")),
    (native,"active hybrid selection",(
                        "circleGeometryMode=new FloatSettings(app).circleTextGeometryMode()",
                        "CircleMlKitGeometry.recognize(app,frame.bitmap,frame.transform,",
                        "if(circleGeometryMode==CircleHybridSelectionGeometry.COMPARE",
                        "CircleMlSelectionVisual.drawCompare(",
                        "mlGeometryGeneration++", "sourceSpans.get(unit)")),
    (prefs,"configuration",("K_CIRCLE_TEXT_GEOMETRY_MODE",
                        "public int circleTextGeometryMode()")),
    (ui,"YUI settings",("void circleTextGeometrySpinner(",)),
    (capture,"Circle settings placement",("ui.circleTextGeometrySpinner(circleOcr.body);",)),
):
    for needle in needles:
        if needle not in source:
            problems.append("Paddle+ML Kit "+area+" missing: "+needle)
hybrid_test=FEATURE / "feature/src/test/java/com/yagay/YFloat/CircleHybridSelectionGeometryTest.java"
if not hybrid_test.is_file():
    problems.append("Hybrid geometry missing Unicode/proximity/segmentation regressions")
else:
    text=hybrid_test.read_text()
    for needle in ("sameCharacterInAnotherIconCannotHijackHighlight",
                    "nextRowIsNeverPairedWithCurrentCharacter",
                    "ordinaryCTCWidthErrorCanBeCorrected",
                    "copiedWordsStayPaddleOwned"):
        if needle not in text:
            problems.append("Hybrid geometry test missing: "+needle)
if "MlKit" in circle or "com.google.mlkit" in circle or "com.google.mlkit" in ocr_engine:
    problems.append("ML Kit must not become a Paddle recognition/text authority")
if "new OcrDocument(" in geometry_provider or "selectedText=" in geometry_provider:
    problems.append("ML Kit geometry provider must not return user copyable text")
visual=(java / "NativeCircleVisualPolicy.java").read_text()
if "textSelectionScreenBand(" not in visual or "sharedWidth>=" not in visual:
    problems.append("Native circle has no bounded glyph/line geometry policy")

# TextGeometry V3 is the active selection source, not unused sidecar
# metadata: verify preserved OCR quads, one-coordinate-owner mapping,
# draw-time pixel invariants and lossless fallback in every selection path.
ocr_doc=(java / "OcrDocument.java").read_text()
mapper=(java / "CoordinateMapper.java").read_text()
page=(java / "PaddlePageV2.kt").read_text()
quad_engine=(java / "TextGeometryV3.java").read_text()
pixels=(java / "TextGeometryPixelLocator.java").read_text()
selection_model=(java / "CircleTextSelectionModel.java").read_text()
pixel_test=(FEATURE / "feature/src/test/java/com/yagay/YFloat/TextGeometryPixelPolicyTest.java")
for source,label,needles in (
    (ocr_doc,"OCR model",("List<PointF> quad", "public List<PointF> quad()",
                          "translatedCorners(")),
    (mapper,"screen mapping",("mapQuad(c.quad())", "mapQuad(line.quad())")),
    (page,"Paddle page",("symbol.bounds.points", "line.bounds.points")),
    (selection_model,"secondary selection",("c.quad()")),
    (quad_engine,"text geometry V3",("selected.containsKey(c)", "row.quad()",
                          "c.quad()", "pixels.refine(", "new Region(quad,corrected)",
                          "Math.abs(dy)<0.08f")),
    (pixels,"bounded pixels",("bitmap.getPixels(src,", "TextGeometryPixelPolicy.refine(",
                          "frame.bitmapRectToScreen(")),
):
    for needle in needles:
        if needle not in source:
            problems.append("TextGeometry V3 "+label+" incomplete: "+needle)
if not pixel_test.is_file() or "toutiaoWhiteGlyphsOnUniformRedIconAreTightlyLocated" not in pixel_test.read_text():
    problems.append("TextGeometry V3 lacks synthetic icon/unsafe-background regression")
if "canvas.drawText(ch.text()" in native or "selectionHighlightPaint" in native:
    problems.append("Native Circle selection must not recreate glyph fonts or use legacy overlays")



if problems:
    print("FAIL: Independent Paddle model policy")
    for problem in problems:
        print(" -", problem)
    sys.exit(1)
print("OK: no bundled Paddle weights; supported models are independently managed")
