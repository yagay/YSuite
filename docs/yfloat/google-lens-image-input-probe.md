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
