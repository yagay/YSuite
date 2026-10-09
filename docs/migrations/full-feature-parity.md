# YSuite main -> rebuild: full-feature parity inventory

Scope: `main` at `ddd7cdf`; `rebuild/product-ui-system` audited on 2026-10-08.
This is a migration inventory, not a declaration that all features have shipped.
The unified product UI stays authoritative; standalone features and the integrated
app must execute the same underlying behavior.

## Coverage map

| Module | Original Kotlin/Java sources | Rebuild sources | Identical original blobs | Required acceptance test |
| --- | ---: | ---: | ---: | --- |
| YDiag | 20 | 12 | 0 | Live sessions, root probes, Perfetto trace, exported evidence |
| YDownload | 15 | 22 | 0 | URL capture, queues, schedule, pause/resume, system downloads |
| YEntryCleaner | 70 | 22 | 0 | Share/open/browser/tile/shortcut/widget rules, component locks, Root recovery |
| YFiles | 15 | 62 | 0 | Local, SAF, Root, archive, SMB, SFTP, FTP, WebDAV and transfers |
| YFloat | 172 | 218 | 164 | Overlay, selection, OCR, accessibility, gestures, LSPosed |
| YMiniGuard | 14 | 16 | 0 | Foreground/background playback, mini-window, lock screen, hot reload |
| YNFC | 40 | 30 | 15 | Card switching, real NFC/HCE status, Root/LSPosed hook and stop restoration |
| YNotify | 42 | 18 | 0 | Listener, Toast/popup, history repair, cross-package linking, encryption, search |
| YParam | 10 | 12 | 0 | All 27 overrides, restoration, per-app Hook changes |
| YPower | 52 | 13 | 0 | Permissions, package/file/command/property/security traces, recommendations |
| YTaskManager | 18 | 17 | 0 | Process details, CPU/GPU/network, filters, pin, kill and force-stop |

*Source counts represent file granularity, not behavioral coverage. Different
names or changed SHA values are not automatically missing functionality.*

## Closure gates (all required, for every module)

- [ ] Inventory every original interactive command, setting, persistence field,
      background service, diagnostic action, import/export format and Hook method.
- [ ] Link each item to a working rebuild implementation and UI route (not a stub).
- [ ] Compare runtime configuration serialization against the original.
- [ ] Prove independent standalone build and integrated `:app:assembleDebug`.
- [ ] Exercise first install, upgrade, missing permission/Root, and rollback.
- [ ] Run device tests for actions that require Root, OEM services, or LSPosed.
- [ ] Confirm migration is lossless for settings and stored data.
- [ ] Confirm all log/error states are accessible to the user and exportable.
- [ ] Verify localization, navigation/back stack and accessibility per screen.

## Known unresolved high-risk areas

1. YNotify: legacy FTS, exhaustive repair rules, sensitive encrypted content
   search and migration of pre-existing standalone app data require dedicated tests.
2. YEntryCleaner: installed package/component visibility varies by Android/OEM;
   verify component state changes across reboot and package reinstall on-device.
3. YPower: the original multi-provider diagnostics/trace engine is substantially
   more granular than the consolidated new runtime implementation. Match every
   original rule/provider to a new executable path before claiming parity.
4. YDiag: old long Perfetto capture/file collection vs new short command-based
   probes need binary trace comparison.
5. YNFC: reader/HCE behavior requires real hardware and vendor NFC service tests.
6. YMiniGuard and YFloat: overlay, locked-screen, vendor window policies require
   physical tests; copied source does not prove integrated launch works.
7. YDownload and YFiles: test system download / SAF / network-provider error,
   process recreation and resume after reboot.
8. YTaskManager: verify process identity and safe termination on multi-user devices.
9. YParam: compare each override's Hook result, including restoration, on-device.

`tools/verify_feature_parity_contracts.py` provides a fast structural safety
net in CI. It is not a substitute for any of the closure gates above.

## 2026-10-09: shared transport and behavioral test consolidation

- Root and Shizuku output streams now have a fixed per-stream memory budget,
  continue draining after truncation and enforce command timeout.
- Root exit codes distinguish successful execution, nonzero execution and
  timeout. Callers must inspect exit codes; a transport-level Outcome.Success
  is not equivalent to a successful shell command.
- HookConfigCoordinator publishes the original feature keys, then a stable
  `__config_revision` checksum, and finally requests scope. Partial remote
  preference writes do not advance the revision marker. Older hooks may still
  read individual keys: this is not a transactional update until their
  runtime readers adopt the marker.
- YEntryCleaner, YMiniGuard and YParam share publication semantics. Success
  means **configuration transport confirmed / target runtime unverified**.
  The LSPosed bridge cannot currently prove each target hook executed.
- YNFC card persistence and YEntryCleaner component actions claim the busy
  slot synchronously, preventing multiple clicks from queuing conflicts.
- JVM tests exercise publication ordering, error paths, revision stability,
  Root execution outcomes and representative feature-model behavior.

Still outstanding: runtime acknowledgment from every target, device/OEM tests,
database upgrade tests and exhaustive original-engine parity. Do not mark a
module as behaviorally complete based only on CI success.

## 2026-10-09: activation consistency pass

- Remote preference revisions are now keyed per app for YParam and YPower,
  preventing one app update from overwriting another app's revision marker.
- YParam holds the save/reset operation guard before launching the coroutine.
  Local desired settings are preserved if a scope request fails after a remote
  write; UI presents remote activation as pending, not necessarily verified.
- YPower no longer force-stops apps merely because Doze, AppOps or other
  Root-only settings changed. A restart is needed for a new/changed or
  previously active Hook configuration; previously successful restart state is
  recorded per package. Target Hook activation remains unverified without an
  in-process acknowledgment.
- YPower diagnostic Hook publication and restoration use the same coordinator.
- YMiniGuard reports pending system-Hook confirmation rather than claiming a
  non-reloaded configuration is already active.
- Focused JVM tests cover revision isolation and restart policy.

Known limitation: remote preference publication is not an atomic multi-key
transaction; older Hook readers may observe intermediate updates. Reusing
mature Hook engines and introducing in-process revision acknowledgment must
precede declaring complete runtime parity.
