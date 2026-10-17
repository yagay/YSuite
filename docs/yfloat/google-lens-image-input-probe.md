# Google Lens image-input experiment (YFloat)

Branch: `experiment/yfloat-google-lens-image-bridge`.

## Purpose

Test **YFloat frozen screenshot -> Google Lens native image processing -> YFloat native-selected OCR text** without replacing existing Google CTS. Chromium's Lens handoff uses an ACTION_VIEW data URI (googleapp://lens, optionally google://lens), with `LensBitmapUriKey` referencing a content:// image shared with AGSA. This is an **image-input path**, NOT a public OCR result API.

Reference: Chromium LensUtils.java, `getShareWithGoogleLensIntent`:
https://chromium.googlesource.com/chromium/src/+/5ce55c161eed982e49ceb58e0d3265ef58c14238/chrome/android/java/src/org/chromium/chrome/browser/share/LensUtils.java

## Changes

- Explicit **Google OCR（实验）** action on YFloat's native frozen Circle image popup uses the selected crop. Ordinary **识图 / Find** retains its original external app behavior; **完成 / Done** still opens the screenshot result.
- The screenshot result dialog now also has a dedicated **Google OCR** magnifying-glass icon, next to local OCR. It clones the existing captured image before closing the result dialog, then starts the same experiment. This matches the action the tester chose in the 2026-10-17 20:59 log.
- Encode crop off the main thread through the existing host FileProvider, send a specifically package-bound Lens Intent with `LensBitmapUriKey`, grant Google temporary read access.
- Exact workflow token markers permit existing YFloat Google Hook to observe Lens selection callbacks if Google preserves the extras.
- The Google image-input probe explicitly **does not block Google search or hide native Lens views**, unlike the normal CTS custom-menu path. An actual OCR result is only confirmed by a live selection callback.
- On successful callback, pair Google-selected text / optional bounds with the exact cropped image URI for that session; never log OCR text.
- On launch failure, terminal close, or 120-second timeout, revoke the URI grant, delete the temporary image, and discard in-memory OCR data.

## Test on OnePlus / Google App 17.66+

1. Install the **ARM64 debug APK** from this branch. Enable the existing YFloat LSPosed Google scope.
2. Open YFloat's **native** frozen circle, draw a closed image region, then tap **Google OCR（实验）** in its image menu, OR tap **完成 / Done** and then the new **Google OCR** magnifying-glass icon in the screenshot result dialog.
3. Verify Google Lens opens with **the selected YFloat image**, not the live screen or a blank picture. If Google Lens won't open, input contract is not supported on this version/device.
4. Inside Google Lens, use **Select text**, select recognizable text and observe whether YFloat gets a floating text menu.
5. Export the YFloat diagnostic log and look for:
   - `LENS_IMAGE_INPUT selected_from=native_crop` / `selected_from=result_dialog` -- the explicit experimental command was actually invoked.
   - `LENS_IMAGE_INPUT encode_begin` -- screenshot conversion started.
   - `LENS_IMAGE_INPUT intent_dispatched` -- YFloat handed the image URI to Android (NOT proof of Google reading it).
   - `GOOGLE_CTS_HOOK V2 LENS_IMAGE_INPUT marked_activity` -- Hook saw the tagged Activity.
   - `LENS_IMAGE_INPUT native_ocr_callback` -- **proof** Google returned selected OCR text to YFloat.
   - `LENS_IMAGE_INPUT image_released` -- exact URI and temporary-file cleanup.
6. Repeat 3 times, cold and warm, and verify native Google CTS still operates independently.

## Pass/fail gates

A launch `startActivity()` success is NOT OCR success. Google may repackage the activity and strip YFloat extras, or run a different Lens selection pipeline; both require evidence-based adaptation. Do **not** auto-hide Google, apply Hook symbol guesses, intercept network traffic, extend ownership to unrelated Google sessions, or claim full-screen OCR availability before observing a live result.

If input fails, investigate exported Google Activity/Intent contract first. If input works but OCR return fails, inspect selection output specifically with the Google 17.66 diagnostics; don't replace the working image input or re-enable speculative search blocking.

## 2026-10-17 21:10 log findings and next gate

- Real native crop at 21:09:55 had 648x609 pixels; the YFloat temporary PNG was 460035 bytes.
- Legacy googleapp://lens Intent entered GoogleAppGatewayActivity, but no Google screenshot read or OCR selection was observed before diagnostic export. The earlier 21:03 input was revoked by top_package_changed at ~5 seconds.
- New candidate caller fix follows Chromium's actual startActivityForResult pattern with a private transparent YFloat Activity. It is an experimental source-attribution adjustment, NOT a confirmed AGSA 17.66 OCR result API.
- When the installed Google App exposes a separate direct Lens activity for google://lens, use it in preference to the generic gateway. This is determined by PackageManager.resolveActivity; never guess an obfuscated class.
- Diagnostic hook now reports ContentResolver opens of the **exact YFloat image authority** and whether the Java open returned successfully, without logging image bytes or OCR contents. Native/binder-level opens may not be visible, so a missing open event is inconclusive.
- If Google navigates away, leave the one-shot image URI readable for at most 2 minutes instead of immediately deleting on an accessibility top-package change.

The refreshed runtime evidence should be read in this order:

1. `selected_from`, `encode_begin`, `resolvers gateway=... direct=... chosen=...`
2. `caller_started`, `intent_dispatched caller=startActivityForResult`
3. `GOOGLE_CTS_HOOK V2 LENS_IMAGE_INPUT google_activity=...`
4. `google_image_open_attempt` / `google_image_open_result success=true`
5. `native_ocr_callback` (ONLY this confirms YFloat received Google-selected OCR text)
6. `workflow_ended_input_retained` vs `image_released` with reason.

Even if steps 1–4 succeed, Google Lens may implement OCR selection in a different process/controller than Circle to Search. Do not claim a working OCR results interface until a genuine Google text selection event is observed.

## 21:22 follow-up: verify the host-side PNG read

The latest capture is 615x742 pixels / 511140 bytes. The direct scheme resolved
to com.google.android.apps.lens.MainActivity, entered LensExportedActivity
and returned a custom result code 1 about 204 ms later; no text was returned.
A custom Activity result code is not proof of an OCR result or image read.

The current build prefers the Chromium default gateway contract, and only
falls back to direct when the gateway is unavailable. Resolver availability
alone no longer changes the protocol.

YSuite's original host .files FileProvider has a compatible concrete subclass
with the same authority, paths and URI permission policy. It records
host_file_open_attempt / host_file_open_result / host_file_open_failed only
for the YFloat_*.png images, without recording image contents or filenames.
A verified host_file_open_result success=true proves the Android provider
opened the PNG for a client; it does not prove Google completed OCR.
The old Google process ContentResolver tracing remains supplementary.

## Google 17.66.12 APK + 21:37 diagnosis (verified 2026-10-17)

The user-provided APKS bundle contains a 245.54 MiB base APK and separate
on-device Lens engine modules (feature 18.55 MiB; Play ML 67.72 MiB).
The recognition implementation is therefore not reducible to the main
Activity handoff, and none of the static inspection proves OCR results
are accessible through a public API.

The 21:37 trial captured **685 × 743** pixels and sent **638152** PNG bytes.
`resolvers ... chosen=gateway_default`, then
`GoogleAppGatewayActivity scheme=googleapp`. The caller received an
empty RESULT_CANCELED (code 0), with **no** `host_file_open_result` and
**no** `native_ocr_callback`. This indicates a gateway launch but does not
prove the PNG was ever accessed. LSPosed status reported googleStale=true;
that is a separate caveat for custom Hook validation.

In the actual 17.66.12 DEX:
- `classes6.dex Lcuqi.e` reads **LensBitmapUriKey**,
  **AccountNameUriKey**, **IncognitoUriKey**, **ImageSrc**, **PageDomain**,
  **Gid**, and **ActivityLaunchTimestampNanos**, plus
  **lens_intent_type** from a `googleapp://lens` URI.
  This proves these protocol keys exist. A separate
  **lens_data**/LensDeeplinkRequest branch checks Entry Point ID; this
  must NOT be confused with a missing mandatory query parameter for
  the legacy bitmap URI branch.
- `classes8.dex Ldufb.a` includes a caller-package requirement for
  LensExportedActivity. Identity and successful Activity resolution are
  not sufficient evidence that image processing ran.
- The base AndroidManifest.xml declares
  **com.google.android.apps.search.lens.LensShareEntryPointActivity**
  as **exported=true**, with an **ACTION_SEND** filter for
  **image/jpeg** and **image/png**.
- `classes6.dex LensShareEntryPointActivity.onCreate` explicitly checks
  **android.intent.action.SEND**, **image/** MIME type, and
  **android.intent.extra.STREAM** before handling the shared image.
  This is a materially stronger, directly APK-confirmed image-input
  route than a generalized URI gateway.

**Changed behavior:** the explicit experimental Google OCR command now
prefers the APK's exported ACTION_SEND LensShareEntryPointActivity when
it is installed and exported. The request uses
`Intent.EXTRA_STREAM=content://<YSuite>.files/...`,
`type=image/png`, `FLAG_GRANT_READ_URI_PERMISSION`, and ClipData.
Other Google versions retain the original gateway and direct-scheme
fallback; ordinary YFloat and Google CTS paths remain unaffected.

On next run, check `resolvers share=true chosen=apk_verified_image_share`,
then `host_file_open_result success=true`, then Google activity
transitions, then a native OCR text callback. The ACTION_SEND activity
may still use its own selection implementation, so old Circle-to-Search
selection Hooks may not emit the desired callback. That must be measured
independently; image-read success is not OCR-result success.

## 22:05 trace: the image path is now proven; keep screenshot regions separate

The exact-session native crop was 675x695 pixels, PNG 543066 bytes. Google
successfully launched LensShareEntryPointActivity followed by
LensExportedActivity. The host's URI provider observed several successful
read-mode opens from Google uid 10135. The Google process decoded an identical
675x695 ARGB_8888 Bitmap from `injected_image_uri.source_uri`; the frame
subsequently reached YFloat through SharedMemory. These are runtime facts,
not a heuristic based on Activity launches.

At 22:04:53 the semantic selection hook `dwqi.z(dwpz)` was reached, but no
`LIVE_TEXT_CALLBACK` or `native_ocr_callback` was observed. A late
region/gesture event was incorrectly interpreted as a regular full-screen
Circle-to-Search region, even though Google's source bitmap was only 675x695.
YFloat displayed its own giant region confirmation Rect(0,7-1251,2772),
then delivered an extra screenshot result, released the URI and ended the
experiment at `v2_result_visible`. This host-induced transition explains
why the previous trial could not accurately test native OCR selection.

Now, only for the ACTION_SEND experiment:
- The Google Hook does not forward native image-local region rectangles to
  the host as full-screen CTS regions; it also leaves FrozenImageView
  transformations alone instead of blocking setTranslationY.
- The host ignores image-share region and region-confirm events as a
  second boundary defense. No screenshot-result overlay is synthesized.
- Google retains its own Lens region/selection UI. The existing OCR
  text-selection hooks remain active. If text genuinely arrives, YFloat
  can display its own text actions, but it must not draw a screen outline
  using untransformed Lens image-local coordinates.
- Ordinary Google CTS sessions still preserve their existing screen-region
  handling, viewport guards and screenshot result behavior.

Next run acceptance: `host_file_open_result success=true` and
`GOOGLE_FRAME_CAPTURED bitmap=675x695` should remain true; there should
be no `GOOGLE_REGION confirm overlay`, `GOOGLE_V2 screenshot delivered`,
or `v2_result_visible` from a shared-image selection. The next critical
signal is `LIVE_TEXT_CALLBACK` or `TEXT_SELECTION_READY` followed by
`native_ocr_callback`. The Google LSPosed process was still marked
STALE v56 in the supplied diagnostic, so reload before attributing
callback absence to the Google 17.66 implementation.
