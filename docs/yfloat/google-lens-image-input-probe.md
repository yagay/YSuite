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
