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

### Auto OCR behavior and export consistency

Auto OCR now uses installed Paddle models **only**. An absent model,
empty recognition, or native error shows an actionable failure; it does not
silently run ML Kit. ML Kit remains a separate explicit user-selected mode,
so existing intentional choices are preserved. Circle's Paddle Auto follows
the same no-fallback rule.

Table text, HTML and source arrays remain index-aligned, including tables
with empty OCR text. Source labels distinguish native SLANet output from
strict pixel-grid fallback. PDF output is serialized to app-private temporary
storage page-by-page and copied to SAF with bounded buffers, while OCR page
objects are no longer retained for the entire document.

**CI gate:** One consolidated ARM64 Debug compilation is required after this source batch; intermediate commits intentionally skipped Actions.

**Final validation:** The first consolidated build found two outdated static-contract strings only; both were corrected to match the refactored code. Full ARM64 Debug compilation is requested again with the final source snapshot.

## Circle OCR unwanted spaces — source-level fix (2026-10-18)

Two independently confirmed causes were corrected together:

1. `CircleTextSelectionModel.selectedText()` previously inserted spaces
   whenever the selectable glyph `group` changed. These are selection
   groups, not lexical word boundaries. It now reconstructs explicit
   whitespace from the **original OCR line text** and carries per-unit
   spacing hints through screen-coordinate ordering. Only if OCR text
   cannot be mapped to a unit does it use the legacy grouping fallback.
2. `PaddleSpacingV2.correct()` previously abandoned *all* spacing cleanup
   when the CTC aligned glyphs differed from the recognized full text.
   It now preserves complete text **and** applies script-aware CJK
   normalization on the text-only fallback. It does not invent missing
   glyph boxes, merge unrelated Latin words, or remove line breaks.

The Paddle spacing toggle remains independently controllable and
enabled by default. A previously persisted Circle OCR mode of 0 selects
ML Kit explicitly; this Paddle-specific fix will not apply in that mode.
New Circle installations default to installed Paddle Auto. Unit cases
cover Chinese, punctuation, NBSP/full-width spaces, mixed script, Latin,
newlines and incomplete CTC alignment.

**Verification required:** Kotlin/Java ARM64 Debug compile plus device
reproduction (Paddle mode selected, spacing toggle on). Latin OCR
spaces genuinely emitted by the recognizer without reliable glyph
alignment still require image-dependent evidence; do not strip them
blindly.

**Final CI gate:** Compile the consolidated Circle OCR whitespace fixes as ARM64 Debug; every preceding code commit in this batch used `[skip ci]`.

## Source-offset OCR copy semantics (2026-10-18)

Replaced the older Circle copy's group-based whitespace reconstruction
with a pure-Java **UTF-16 source span aligner**:
`OcrSelectionText.align(lineText,glyphs,lineId)` maps each OCR selection
unit (glyph, word or detector fallback) onto the exact recognized line.
`CircleTextSelectionModel` carries the original source spans through
screen-space clipping and visual sorting. Selected text slices preserve
the original OCR's whitespace and punctuation rather than reconstructing
words from geometry. Consecutive selected source ranges are joined with
their original intervening text; noncontiguous selections never silently
include unselected glyphs.

An unalignable box is explicitly marked unknown; its own literal text is
used without inventing whitespace. This avoids the prior approach's
guessed spaces and repeated inconsistent CJK/Latin special cases. Upstream
Paddle CTC character decoding remains the source for recognizer text.

The branch's ARM64 Debug workflow now executes both real Android unit-test
modules (`:yfloat-feature:testDebugUnitTest`,
`:yfloat-ppocr-sdk:testDebugUnitTest`). New tests cover Chinese,
English multiple spaces, mixed script, Unicode punctuation, partial/non-
contiguous selection, repeated characters and incomplete source mappings.

**Remaining boundary:** if the recognition model itself predicts an
incorrect Latin space, source-preserving selection cannot infer the right
word without additional reliable visual or linguistic context. That is
a separate recognition-confidence problem, not a selection join issue.

## Paddle-native OCR full core refactor (2026-10-18)

- Normal screenshot OCR and Circle OCR now share `PaddleRecognitionRuntime`
  for automatic/offline model selection, retry and confidence escalation.
- `OcrEngine` has been reduced to request token state and UI dispatch; the
  old multi-pass ML Kit Chinese/Latin fusion parser was removed.
- `CircleStableOcr` calls the same runtime with no ML Kit branch; old
  persisted ML Kit picker values migrate to installed Paddle Auto.
- `PaddlePipelineV2` is the only ONNX/OpenCV session owner. The older
  `PaddleOcrBridge` is now a stateless forwarding shim; the old
  ML-styled `OcrCanonicalGeometry` and `MlKitTextCore` sources were deleted.
- Removed ML Kit recognition dependencies from YFloat and inactive
  language-family controls. Paddle recognition vocabulary is defined by
  the actually installed model, not an inert UI switch.
- `PaddlePageV2.Page.toDocument()` retains true block/line/word/symbol
  output; Circle copies exact original UTF-16 text spans through
  `OcrSelectionText` instead of inserting spaces from geometry group ids.
- Explicit modes Tiny/Small/Medium/Mobile remain supported. The old ML Kit
  and retired cloud modes map to Paddle Auto; Circle correction Off remains
  Off.
- Added real `PaddleRecognitionRuntimeTest` coverage; the Debug workflow
  executes both feature and Paddle SDK tests before merging an ARM64 APK.

See `PADDLE_NATIVE_ARCHITECTURE.md` for the maintained ownership contract.

**Validation:** one consolidated final ARM64 Debug workflow after all of
these intermediate `[skip ci]` commits. Native ONNX accuracy and end-to-end
Circle gestures still require a physical device.

## Full Circle whitespace-path audit — 2026-10-18

Root causes observed in the legacy diagnostic and current source:
- `PaddleSpacingV2.correct()` previously normalized the displayed CJK
  text but could retain CTC blank tokens in the corresponding glyph list.
  These mismatched structures generated wrong selection groups even when the
  source text itself was correct. CJK separators are now removed from the
  glyph stream in lockstep with the normalized text, without fabricating boxes.
- `CircleGestureTextSelector.documentFromGroups()` previously inferred
  whitespace from visual group distances via `shouldInsertSpace`. That path
  is removed; it now reads character ranges in the original recognized
  source line and preserves only source whitespace.
- `OcrSelectionText.selected()` additionally requires source-string
  identity by content when joining adjacent UTF-16 ranges. Two different
  source lines can never accidentally bridge positions with the same index.

Stage counters in `PADDLE_SPACE_TRACE` expose CTC raw/normalized blank
counts and gesture-derived selected blank counts **without recording OCR
content**. The contract tests reject old group-based whitespace insertion
and mismatched CTC text/glyph corrections. User-created intentional spaces
in Latin strings remain unchanged without strong visual evidence.

Limits: the official on-device recognizer can still misrecognize genuine
English word spacing, especially if CTC alignment is incomplete. The app
must not silently guess what English phrase was intended. A screenshot and
stage counters are needed to distinguish intrinsic recognition uncertainty
from any remaining app-side text mutation.

**Final consolidated CI:** Run ARM64 Debug and both OCR unit-test modules after this deep whitespace-path audit. Intermediate commits were skipped.

## NativeCircleWorkspace real-path spacing fix (2026-10-18)

The diagnostic `YFloat-diagnostic-20261018-151758.txt` showed the
**NativeCircleWorkspace** (not `FLCircleInlineOverlay`) was actually
handling the user's latest text gesture. At 15:17:50 the 55 OCR lines
contained six recognized spaces before and after normalizing
(`rawSpaces=6 normalizedSpaces=6 correctedLines=0`). The gesture
selected three OCR units and rendered five UTF-16 characters; lengths
alone do not establish how many characters were whitespace.

Deep audit found an independent, missed source in
`NativeCircleWorkspace.applyTextIndices()`: it used
`text.append(' ')` whenever the Paddle unit's line/group changed.
This automatically fabricated spaces in Latin text even after the
other Circle selection implementation had been repaired.

The real workspace now caches `OcrSelectionText.Span` per original
`OcrDocument.CharUnit` using source-recognized line text and uses
`OcrSelectionText.selected()` for first/last selections and handle
drags. It never manufactures spaces from a change of group. Mapping
failures use the exact unit text without guessed separators. Logging
records only counts and alignment coverage:
`PADDLE_SPACE_TRACE stage=native_index` and
`PADDLE_SPACE_TRACE stage=native_selection`.
A regression contract and unit test prevent the old group concatenation
from silently returning.

The six OCR-decoded spaces on the source frame may be legitimate or
mistaken Paddle predictions; the existing log alone cannot identify
which was shown on the selected text. If the later native-selection
trace shows a source-derived space still misplaced in an English word,
recognizer confidence and actual cropped pixels must be compared rather
than stripping valid language spaces blindly.

**Native-circle final CI gate:** This consolidated source fix is ready for ARM64 Debug compilation and both OCR unit-test tasks; all intermediate edits used `[skip ci]`.
