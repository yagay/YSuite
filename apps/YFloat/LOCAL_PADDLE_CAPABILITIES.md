# YFloat: offline Paddle capabilities (development batch)

This record differentiates genuine local inference from optional models, geometry
heuristics, and modules not implemented. Do not mark an untested model as complete.

| Capability | Source | Implementation | Validation |
|---|---|---|---|
| Text detection and recognition | PP-OCRv6 Tiny / Small / Medium | ONNX + OpenCV, existing runtime | Previously built, phone accuracy unverified |
| Mobile OCR (additional choice) | PP-OCRv5_mobile_det_onnx + PP-OCRv5_mobile_rec_onnx | Independently downloaded model pair and inference.yml; reused existing local ONNX + CTC engine | Source changes staged; not yet compiled or phone tested |
| CTC glyph quads and word grouping | Paddle SDK | CTC alignment + grouping | Previously built, geometry estimated |
| Spacing refinement | PaddleSpacingV2 | CTC-confidence/geometry heuristics | Previously built, requires real-image validation |
| Layout areas | PP-DocLayout-S | ONNX; 23 layout categories | Previously built, real-image validation pending |
| Document orientation | PP-LCNet_x1_0_doc_ori ONNX | Independent 224x224 classifier, 4 classes | **Staged; not compiled or phone-tested** |
| Table structure | SLANet-1M ONNX | 488x488 ONNX, vocabulary decoding, cells + OCR association | **Staged; not compiled or phone-tested** |
| Multicolumn reading order | Local geometrical grouping | Conservative two-column heuristic | **Staged; not model-based** |
| UVDoc dewarping | UVDoc ONNX | **Staged:** offline BGR image-to-image inference and second OCR pass | Corrected image text kept separate to avoid incorrect source-screen coordinates; not compiled or phone-tested |
| Formula to LaTeX | PP-FormulaNet_plus-S converted ONNX | **In development:** independent ONNX+tokenizer download and offline region recognition | ONNX/UniMERNet ByteLevel local decoder wired into formula layout areas; compile, phone validation and memory profiling pending |
| Chart labels and titles | PP-DocLayout-S + PP-OCRv6 | **Staged:** original-image chart regions and visible OCR labels | NOT chart-to-table, no inferred plot values |
| Chart-to-table generative model | PP-Chart2Table | **Not implemented** | 1.43 GB, 0.58B multimodal Paddle weights with no Android inference backend; not mislabelled as complete |
| Seal text detection | PP-OCRv4_mobile_seal_det converted ONNX | **Staged:** offline DB detector in PP-DocLayout-S seal regions; separate crop OCR | Curved text remains challenging; not compiled or phone-tested |

No remote recognition /layout-parsing service remains.

Model sources:
- https://huggingface.co/ningpp/PP-LCNet_x1_0_doc_ori-ONNX
- https://huggingface.co/bdatdo0601/slanet-1m-onnx
- https://huggingface.co/marsena/paddleocr-onnx-models/blob/main/UVDoc_infer.onnx
- https://huggingface.co/x3zvawq/paddleocr-js-onnx/blob/main/ppocr_v4_mobile_seal_det/PP-OCRv4_mobile_seal_det_infer.onnx
- https://huggingface.co/x3zvawq/paddleocr-js-onnx/tree/main/PP_FormulaNet_plus_S
- https://huggingface.co/PaddlePaddle/PP-Chart2Table

## CI policy

Do not run automatic CI for partial steps in this offline Paddle build-out.
Use a deliberate final ARM64 debug validation after the missing supported
runtime modules and integration tests have been completed. Any intermediate
commits must have `[skip ci]` in the commit message.

## Model distribution policy

- **No OCR/Structure/UVDoc/SLANet/seal weights are embedded in the APK.**
- PP-OCRv6 Tiny, Small and Medium each have separate download/delete controls.
- **PP-OCRv5 Mobile** is a fourth independent OCR model. It does not overwrite or change the current default or existing downloaded models.
- PP-DocLayout-S downloads and enables separately from OCR.
- PP-LCNet document orientation, SLANet table structure, UVDoc and seal ONNX
  have independent download/enable/delete controls. The table's dictionary is
  downloaded only with SLANet and removed with it.
- Inference uses only local files. It does not download automatically or call
  Google App, Lens or an HTTP processing service.
- FormulaNet_plus-S (converted ONNX) downloads independently together with a
  version-pinned UniMERNet tokenizer. It is a large optional model, never bundled.
- Chart2Table remains unsupported; downloading its
  heavyweight raw weights without an Android runtime would be misleading.
  They must be made independent optional downloads *after* the inference
  implementation and its resource checks work.
- This development batch uses [skip ci]; no partial GitHub CI builds.

## Paddle Mobile addition

Original PaddleOCR models and optional layout/table/formula/OCR extensions remain
present in this branch. Mobile is an additional selectable model; full-screen,
circle and text correction use the same local Paddle OCR pipeline and geometry.

Official model sources (runtime files downloaded only when the user taps Download):
- https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx
- https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx

The older RapidOCR migration lives on a separate untouched branch. No weights are
committed to this repository. The next CI run is deliberately deferred.

## 2026-10-18: Paddle continuity pass (source staged; CI deferred)

- Kept **PaddleOCR** as the on-device recognition implementation on this branch;
  the separate RapidOCR experiment was not merged.
- All four downloaded base recognizers (v6 Tiny, Small, Medium, v5 Mobile)
  are now reachable from primary OCR settings. In Auto, Tiny is a final local
  option before ML Kit if larger Paddle models are absent.
- New Circle full-screen installations default to an explicit offline Paddle
  Auto mode, with the same installed model files and runtime as normal OCR.
  Existing saved engine preferences remain unchanged; ML Kit remains an
  explicitly selectable alternative and an unavailable-model fallback.
- A complete OCR document, its table cells, seals, FormulaNet output,
  unwarped text, and chart labels are snapshotted in the same serialized
  inference lane. Concurrent requests no longer assemble a document using
  unrelated subsequent request metadata.
- Optional table, seal and formula crops are intersected with original
  bitmap bounds before native inference. Invalid/tiny regions are skipped.
- Table-cell text preserves full recognizer line spacing where cell coverage
  is strong and retains line boundaries where several recognized lines share
  a cell. SLANet's HTML no longer duplicates explicit model table tags.
- CTC spacing adjustment now retains decoder text when glyph geometry is
  incomplete and requires orientation-aware gap evidence before suppressing
  spaces. These are conservative heuristics, not a language-model grammar pass.
- Paddle structured pages now persist a nonzero quality score so automatic
  Small -> Medium escalation can retain the better recognized result.
- A static source-contract guard was expanded; this is **not** a replacement
  for real model/instrumentation tests.

**Not yet validated:** ARM64 debug compilation after the recent staged
changes, native ONNX model output contracts on a real OnePlus device,
OCR accuracy/latency on real screenshots, and module memory pressure.
Chart2Table still has no local Android inference backend and remains
unsupported rather than presenting a nonfunctional download.

## One-batch local OCR feature pass (2026-10-18)

**Source implemented on `experiment/yfloat-paddle-mobile`, pending CI and device checks:**

- Added pinned **PP-LCNet_x1_0_textline_ori ONNX** as an independent user download.
  The SDK classifier runs on real detected line crops before CTC, with documented
  RGB ImageNet normalization at 160x80 and confident 180-degree correction.
  Aligned symbol boxes are transformed back into the source detector quad.
- When CTC cannot align a word or line, the selector falls back to the **real
  detected region**, never uniformly fabricates individual glyph rectangles.
  True per-glyph precision still depends on available CTC alignment.
- Strengthened conservative multi-column ordering using actual whitespace
  gutters, rather than assuming every screen has exactly two halves.
  This is geometry-driven, not model-learned reading order.
- Immutable `OcrDocument` retains the **original SLANet table HTML** alongside
  TSV, including predicted colspan/rowspan tokens. Markdown can embed that
  source HTML where pipe tables would flatten merged cells.
- Implemented on-device JSON / Markdown / HTML / CSV exporters with block,
  line, word, glyph, coordinates and coordinate provenance. JSON exports
  real selection fallback units separately from model-predicted glyphs.
- Added **PDF to Markdown / JSON / HTML / CSV** in OCR settings using Android
  `PdfRenderer`, one bitmap/page, local Paddle inference and Android's SAF
  file picker. PDF pages are processed sequentially, maximum 500 pages per
  batch; no PDF contents leave the phone.
- Added adaptive CPU thread and recognition-batch policy for low-memory
  conditions; this does not claim an unsupported NNAPI/QNN backend.
- Retained `[skip ci]` on intermediate code commits and extended static
  source integration checks.

**Still not guaranteed/implemented:** full PP-TableMagic table classification
and independently learned cell detection; the PP-Chart2Table 1.43 GB
generative graph; exact inverse UVDoc nonrigid coordinate deformation;
learned semantic reading order; automatic linguistic post-correction.
Those capabilities are not silently replaced by heuristic outputs.
Existing SLANet, FormulaNet, UVDoc and seal native operators are present
but require a real Android model/shape/accuracy test before release.

Final ARM64 Debug build is requested once after all staged code changes. CI outcome must be checked separately; the source pass alone does not prove native inference correctness.

## Local OCR reliability and result export pass (2026-10-18)

The same structured `OcrDocument` now flows through the normal screenshot OCR
dispatcher, the single ResultSession, and inline results. Save in the shared
result dialog supports image, Markdown, JSON (including geometry),
HTML (including SLANet spans), and table CSV, all through Android's
document picker without remote processing. Formats require an actual document;
the existing screenshot save path remains available.

All independently downloaded Paddle structure models now have a user-invoked
local SHA-256 Verify action. Replacement downloads are staged and checked
before a reversible on-disk swap. On failure, an existing model and its enabled
preference are retained, including optional vocab/tokenizer files. Interrupted
backups are recovered if the active model is missing. Download and check
callbacks clear their busy states before UI refresh.

Sequential local PDF OCR now has an explicit Cancel control and keeps
rendering only one page bitmap at a time. Cancellation takes effect after the
current on-device inference callback; it is not a native ONNX cancellation
interrupt.

**Validation boundary:** static source contract checks and ARM64 Debug
compilation are planned. Accurate bounding boxes and optional ONNX output
contracts still need real screenshots and on-device comparison. Large,
unsupported generative document models are not bundled or advertised as
working offline.

## Consolidated offline capability pass (final source batch; 2026-10-18)

This batch is staged with `[skip ci]` on every intermediate commit.
Only the final source-acceptance commit should trigger the ARM64 Debug CI.

**Implemented, source-level:**
- Conservative pure-local `PaddleRuledGridV4`: detects real full-length
  horizontal/vertical pixel rules and their intersections in strongly ruled
  tables. SLANet ONNX output always takes precedence when it predicts cells.
  The fallback does **not** pretend to be PP-TableMagic or infer merged cells.
- The table linker now assigns each recognized line/word to a **single** cell,
  avoiding duplicate text in adjacent cells. Each exported table carries its
  actual source (`slanet_model` or `pixel_ruled_grid`) in structured JSON.
- Text-selection word, line and CTC glyph geometry is clipped to the original
  bitmap; missing CTC glyphs still use only real detector/word regions.
- UVDoc rectified text has an explicit `RECTIFIED_BITMAP` coordinate space
  and is embedded as a separate page in JSON. No fictional inverse non-rigid
  coordinate mapping or original-screen selection geometry is produced.
  Translation preserves table and UVDoc provenance.
- The PDF importer writes page-by-page directly to a private temporary file,
  not a retained 500-page list of OCR documents. Markdown, JSON, HTML and CSV
  exports are streamed and copied with bounded buffers to the selected SAF URI.
  Users may retry the save without rerunning OCR, discard the result, or cancel
  recognition after the current native inference finishes. Failure rendering a
  PDF page releases the bitmap.
- Continues the existing per-model private downloads and atomic rollback
  behavior. No model weights are embedded into the APK.

**Not equivalent to the complete upstream model graph:** PP-TableMagic
involves learned table classification and learned cell detection models; the
pixel fallback is a small, honest wired-grid alternative. UVDoc's exported
ONNX graph supplies a corrected image, not its pixelwise inverse warp. Without
an actual invertible deformation map, rectified coordinates cannot safely
drive original-screen text selection. PP-Chart2Table has no verified mobile
generator runtime. These capabilities remain explicitly unsupported rather
than simulated.

**Pending validation:** one final ARM64 Debug build and phone-based
recognition/memory tests. Model availability and native ONNX tensor contracts
must still be tested on a real device.
