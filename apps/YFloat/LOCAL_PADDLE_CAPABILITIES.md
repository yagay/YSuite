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

## Highlight turning into rectangular crop — 2026-10-18

Diagnostic: `YFloat-diagnostic-20261018-155752.txt` records the same
intent mismatch twice. At 15:47:38 and 15:57:42,
`CIRCLE_NATIVE text OCR timeout; falling back to image crop`
fires while `kind=HIGHLIGHT`. The local PP-OCRv5 Mobile screen index
actually finishes after about 10.9s and 8.6s respectively, well beyond
the 4-second UI timeout. The image fallback switches to an editable
rectangular crop with four handles, which explains the sudden change
from a thin drawn line into a box.

The native canvas also previously replaced a gesture's real `Path`
with the bounding rectangle during `RESOLVING`. Both problems are fixed:

- Keep a copy of the drawn highlight `Path` until Paddle inference finishes.
  Draw this exact line, not the bounding box, during pending text inference.
- Route text highlights only to text, even if the installed on-device
  model is slow. Never automatically turn `HIGHLIGHT` into a screenshot.
- Wait up to 25 seconds for the recognition callback. On timeout or
  no usable text, reset to IDLE with an actionable message rather
  than producing a cropped rectangle. Late callbacks cannot change
  an abandoned state. Taps and actual image gestures retain image fallback.
- `NativeCircleIntentRouter.permitsImageFallback()` centralizes this intent.
  JUnit tests and static source contracts cover the invariant.

**Still to validate on a device:** path continuity while OCR runs; proper
glyph highlights after OCR; no rectangular crop if OCR fails. The source
and CI tests cannot emulate user touch rendering in a live Android overlay.

**Final ARM64 Debug gate:** Validate the complete highlight gesture and OCR timeout routing batch with unit tests and ABI checks after all intermediate `[skip ci]` commits.

## User-configurable Paddle execution flow — 2026-10-18

OCR preferences now expose **two independent profiles**, on the standard
YFloat Capture/OCR settings page:

- **Circle** defaults to fast detection + recognition + CJK spacing.
  It skips document layout, tables, document orientation, UVDoc, seals,
  formulas and chart metadata. Regular OCR does not inherit these settings.
- **Document / images / PDF** defaults to the existing complete local
  pipeline. The user may disable individual expensive stages or enable
  the fast detector here too.
- Each profile has an **Extended pipeline** master control that gates
  optional steps without discarding per-stage preferences. The following
  steps have separate controls: fast detection, text-line orientation,
  document orientation, fallback four-way orientation OCR, layout,
  table structure, UVDoc rectification, seal analysis, formula OCR,
  chart visible labels, and spacing refinement.
- Text detection and recognition are mandatory core stages of OCR;
  disabling both would not be an OCR request. All optional weights must
  still be downloaded individually. A switch cannot replace a missing
  model. Closing an auxiliary stage skips loading/inference and releases
  previously cached auxiliary session under the serialized inference
  mutex. Other profile preferences are not overwritten.

The fast detector caps the *detection tensor* at 1920 on Circle; the
recognizer still sees full-resolution text crops with the detector
coordinates mapped to the original bitmap by DBPostProcessor. Empty fast
scans retry at 2560. Model sessions are shared with the full pipeline,
so the app never creates another ONNX model instance just for fast OCR.
Regular document OCR defaults to the original detector configuration.
Diagnostics now include `PADDLE_FLOW` profile choices and
`PADDLE_FAST` detector/recognizer timing. Neither logs OCR text.

This version does not claim GPU acceleration: Android ONNX Runtime's
provider support and hardware speed need device-specific verification.
It also does not claim local gesture ROI-only inference: native Circle
still builds a full-screen OCR index so users can select all or drag
across the screen. The large speed gain comes from skipping optional
document stages and reducing detector tensor size without shrinking
recognition crops. Unit cases and static guards cover profile isolation
and stage-to-inference wiring.

**Configurable pipeline final CI gate:** Build ARM64 Debug and run both OCR unit-test suites, verifying per-profile switches, shared ONNX detector/recognizer and the optional stage skip/unload policy. All preceding feature commits skipped CI.

## Native Circle performance/refactoring batch — 2026-10-18

Compared with the previous immediate whole-screen OCR workflow, the active
`FLCircleController -> NativeCircleWorkspace` now:
- opens a frozen screenshot **without running Paddle at all**; an image
  CIRCLE/SCRIBBLE resolves directly to image selection.
- runs OCR after a text HIGHLIGHT/TAP on a conservative 480x240-minimum
  local bitmap ROI, preserving original pixels and mapping every resulting
  line/character through
  `ScreenBitmapTransform.documentLocalToScreen`.
- retries a missed HIGHLIGHT ROI at most once on the original full
  screenshot; a TEXT highlight never silently becomes an image rectangle.
- expands a local text selection to full-screen OCR only for Select all.
- cancels superseded/closed requests via `PaddleRequestToken`, checked
  before acquiring a neural-network session and between optional stages.
  In-flight native inference is **not force-interrupted**; callbacks still
  run to recycle their owned bitmap even when the UI request was canceled.
- caches the text-index `OcrDocument.CharUnit` list and uses
  `CircleCharSpatialIndex` for nearest-handle matching instead of
  recreating every Rect and scanning all characters on each motion.
- skips a second full-screen model solely due to short/low-confidence
  Circle text in Auto mode, while preserving the document-mode quality
  escalation and genuine empty/failure fallback.
- retains one alternate OCR session when switching between local
  text-line-orientation variants on normal-memory devices; low-memory
  devices release the displaced variant. All inference and session release
  remain serialized by the existing mutex.

YFloat OCR settings include YUI-backed **Fast / Balanced / Accurate**
per-profile presets; each profile still supports individual on/off stage
choices, with extended-only controls disabled while the master extension
switch is off. No fake GPU acceleration or extra model weights were added.
The PDF streaming/export path and downloader integrity contracts are
preserved. The legacy inline Circle classes remain as compatibility
references pending a separate source-reference audit; they are not installed
by the current native Circle controller.

New JVM cases cover cancellation signals, clip/small-screen ROIs, and
spatial character hit matching. The source validator rejects accidentally
reintroducing eager full-screen Circle OCR and old group-based fake spaces.

Limits: backend ONNX inference cannot be interrupted midway; a freshly
canceled request already executing a recognition crop finishes its native
call before releasing the lane. Live frame-time, battery and actual OCR
accuracy changes require the user's rooted Android device and representative
image set; static JVM/Debug CI cannot prove those physical properties.

**Final one-shot performance CI:** ARM64 Debug, OCR JVM tests, source-contract checks, ABI verification and artifact upload after all batch commits with `[skip ci]`.

**CI correction:** The first all-at-once build reached Kotlin compilation and revealed a Java package-visibility mismatch in the newly public cancellation API; `PaddleRequestToken` is now public. Rebuild ARM64 Debug and re-run both OCR unit-test suites.

**Final compile compatibility correction:** Kotlin API visibility and Java floating-point gesture ROI overload were both fixed after CI exposed the errors. Perform the final ARM64 Debug/JVM regression build without modifying model sources further.

## Original-font Circle highlight and launcher line locality (2026-10-18)

Source screenshot: launcher icons and app labels of different original font
sizes. The native Circle previous selection accidentally picked the first
and last OCR indices across two unrelated lines (e.g. text inside a rednote
icon and its independent launcher label). `CircleGestureTextSelector` used
to fill *every* character between those indices in the entire document.

Repair:
- For a **HIGHLIGHT** gesture, calculate hits from its actual screen path,
  prioritize the closest touched OCR line, then select only the contiguous
  run of glyphs from that source line. Large horizontal gaps between distinct
  launcher labels break runs. TAP word selection remains unchanged.
- `NativeCircleWorkspace` locks the initial selection and draggable handles
  to its source OCR line; explicit Select all intentionally removes that lock
  and can cover every line.
- The native Canvas always displays the captured screenshot pixels. Selected
  text is **never re-typeset**; instead a translucent YUI-colored horizontal
  band is generated from OCR text-line geometry, while tap/hit testing still
  uses CTC glyph boxes. The text selection handles share the band's final
  coordinates, not differently estimated glyph heights.
- Subpixel rectangles remain `RectF` through the screen→view mapping.
  A lighter selection alpha minimizes visual color changes to original
  letter shapes. Exact source font family and size are intentionally never
  guessed from OCR.
- Status/close controls respect Android status-bar insets, with a safe
  fallback for full-screen overlay windows whose inset may report zero.

The separate pure `CircleTextRangePolicy` and its JVM tests protect line
selection, gap boundaries and Select-all behavior. A static contract in
`verify_paddle_independent_models.py` also checks that the native
workspace never reintroduces glyph re-rendering or status-bar overlap.

Caveat: CTC glyph detection boxes remain estimated and are used only for
selection hit targets. This is a *visual highlight and range locality fix*,
not a claim to recognize arbitrary original fonts or recover exact glyph
outlines. Physical on-device validation remains needed for curved text,
rotated labels and extreme screenshot transformations.

**Final screenshot-selection CI:** Validate line-local text ranges, original-font background highlighting, status-inset layout, all OCR unit tests, and ARM64 Debug APK. Intermediate source commits intentionally used `[skip ci]`.

## Real screenshot follow-up: selection contrast and non-obscuring handles

The screenshot captured at 19:17 showed the corrected line selection
around the Chinese `小红书` lettering on the red app icon, without
incorrectly including the separate `rednote` caption. It also exposed
a visual regression: the very low-alpha blue selection tint was almost
invisible on saturated red, while the handle stems drew straight through
the actual white letter strokes, creating a false impression of a
different font size or typeface.

The native Circle visual path now draws a lightweight tinted background
plus a high-contrast, thin YUI-colored outline. The highlight follows
the original OCR line rectangle, never retypesets OCR symbols and does
not guess the launcher font. Handle stems begin **below** the selection
band and sit outside its left/right edges, leaving original screenshot
glyphs unobstructed. The user-selected Circle color drives both border
and fill. A pure JVM regression test checks the geometry and the
transparent/high-contrast policy; a source-contract gate rejects any
return to top-to-bottom handle strokes crossing glyphs.

Physical validation is still required for exact text pixel contours and
non-rectangular or rotated text. The model's CTC geometry remains an
estimate and is only used as the selection hit surface.

**Final screenshot-contrast CI:** Validate the real launcher icon case, transparent OCR selection with visible outline, non-obscuring handles, Java/Kotlin OCR unit tests, and ARM64 Debug APK in one final build. All earlier commits used `[skip ci]`.
