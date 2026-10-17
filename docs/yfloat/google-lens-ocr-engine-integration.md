# YFloat native circle: selectable OCR engine (Google Lens experiment)

Scope: screenshot remains native-local. Only text-intent gestures (TAP and
HIGHLIGHT) can use the Google backend. Closed circles and scribbles always
resolve to editable image/screenshot crops; no screenshot result invokes Google
automatically.

In **YFloat > Capture/OCR > Native circle OCR > Fullscreen OCR engine**, choose
**Google Lens (experimental)**. Existing ML Kit and PaddleOCR options retain
their original indices and behaviour.

Google Lens uses the verified Android ACTION_SEND(image/png) entrypoint
`LensShareEntryPointActivity`. Android does **not** provide OCR text in
`startActivityForResult`; success is established solely by the authenticated
LSPosed live text-selection callback for this exact session token.

When Lens returns real nonblank selected text, the new OCR-engine request
debounces updates and displays a YFloat-owned OCR result containing the original
image crop and selectable text. Ordinary explicit image-menu Google OCR
continues to show the existing selection toolbar; screenshot dialogs have no
Google launch control. No local OCR content is ever relabelled "Google".

**Limit**: Google's 17.66 image share currently requires its own on-screen
text-selection action. Its internal full-image OCR word lattice is not yet
available through a verified Hook callback. Receiving selected words does not
prove that automatic recognition of every word has been implemented.
Without `native_ocr_callback`, the Google OCR request remains pending until
Google is left or the session times out, and YFloat must NOT silently substitute
local OCR or mark the request successful.

Latest 23:09 diagnostic shows `selected_from=native_text_gesture`,
`googleLoaded=true googleStale=true`, a Google activity launch and no
`hook_ready` or `native_ocr_callback`. The current code permits runtime Hook
verification even if the provider's process-generation label is stale; it does
not require a reboot as a precondition.

Acceptance:
1. Pick the Google OCR engine; tap/highlight sends a crop, circle/scribble never does.
2. Select text in Lens; `native_ocr_callback target=yfloat_ocr` is required.
3. `ocr_return_to_yfloat originalCrop=true resultVisible=true` must follow.
4. The YFloat OCR result must contain real returned Google text and the original
   YFloat crop; screenshots still open local screenshot results only.
5. When there is no selected text, show a genuine failure/pending state rather
   than generating a fabricated or local fallback OCR success.
