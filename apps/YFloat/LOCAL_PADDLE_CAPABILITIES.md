# YFloat: offline Paddle capabilities (development batch)

This record differentiates genuine local inference from optional models, geometry
heuristics, and modules not implemented. Do not mark an untested model as complete.

| Capability | Source | Implementation | Validation |
|---|---|---|---|
| Text detection and recognition | PP-OCRv6 Tiny / Small / Medium | ONNX + OpenCV, existing runtime | Previously built, phone accuracy unverified |
| CTC glyph quads and word grouping | Paddle SDK | CTC alignment + grouping | Previously built, geometry estimated |
| Spacing refinement | PaddleSpacingV2 | CTC-confidence/geometry heuristics | Previously built, requires real-image validation |
| Layout areas | PP-DocLayout-S | ONNX; 23 layout categories | Previously built, real-image validation pending |
| Document orientation | PP-LCNet_x1_0_doc_ori ONNX | Independent 224x224 classifier, 4 classes | **Staged; not compiled or phone-tested** |
| Table structure | SLANet-1M ONNX | 488x488 ONNX, vocabulary decoding, cells + OCR association | **Staged; not compiled or phone-tested** |
| Multicolumn reading order | Local geometrical grouping | Conservative two-column heuristic | **Staged; not model-based** |
| UVDoc dewarping | UVDoc_onnx | **Not implemented** | Requires exact warping output and coordinate inverse mapping |
| Formula to LaTeX | PP-FormulaNet-S | **Not implemented** | Requires model export and autoregressive decoder |
| Chart-to-table | PP-Chart2Table | **Not implemented** | Multimodal generative model with large RAM/storage cost |
| Dedicated seal OCR | PP-OCRv4_mobile_seal_det | **Not implemented** | Requires converted detector with verified pre/postprocess |

No remote recognition /layout-parsing service remains.

Model sources:
- https://huggingface.co/ningpp/PP-LCNet_x1_0_doc_ori-ONNX
- https://huggingface.co/bdatdo0601/slanet-1m-onnx
- https://huggingface.co/PaddlePaddle/UVDoc_onnx
- https://huggingface.co/PaddlePaddle/PP-FormulaNet-S
- https://huggingface.co/PaddlePaddle/PP-Chart2Table

## CI policy

Do not run automatic CI for partial steps in this offline Paddle build-out.
Use a deliberate final ARM64 debug validation after the missing supported
runtime modules and integration tests have been completed. Any intermediate
commits must have `[skip ci]` in the commit message.
