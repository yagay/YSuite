# Module Requirements

These are preserved product requirements for the modules currently included in YSuite. Implementation may be refactored, but behavior must not be silently removed.

## YEntryCleaner

- This is the YSuite replacement for the old ListCleaner path; do not restore ListCleaner compatibility inside YSuite.
- Root + LSPosed implementation; do not move back to IFW.
- Preserve category-based management.
- Share/open-with candidate cleanup, component management, priority sorting and historical/restricted candidate handling remain first-class functions.
- Extended entry cleanup includes Quick Settings tiles, shortcuts and widgets.
- Locking must remain separate from ordinary selection: bulk select/clear must not accidentally alter locked entries; support locked filtering and system/user separation where applicable.
- Ordinary UI/rule changes should apply without restart; only Hook/runtime changes should request restart.

## YDiag

- Diagnose selected apps on demand; selecting an app for collection should not require reboot.
- Must support Root/LSPosed-aware diagnostics, but keep collection options selectable so unnecessary data is not always exported.
- Recommended diagnostic options should be visibly marked.
- Logs should be structured and readable by AI as well as humans.
- YSuite-hosted diagnostics participate in unified per-module and full-suite export.

## YNotify

- Preserve history for notification, Toast and UI-surface events such as heads-up/banner, bubble, full-screen/popup/dialog/snackbar where detectable.
- Preserve per-app history views and full notification information rather than only the truncated status-bar text.
- Historical classification/reclassification must remain possible as classifiers improve.
- Avoid splitting one logical ongoing notification into meaningless duplicate history rows where a stable identity can be maintained.
- Diagnostics must make notification-listener, accessibility, Hook and capture failures attributable.

## YPower

- Keep system-enhancement capabilities outside `/system`; do not require turning the project into a system-partition app.
- Preserve the diagnostic center with real runtime state, concise/detailed views and actionable attribution suggestions.
- Recommended Hook targets should remain visible and synchronized with the feature's actual requirements.
- Root/LSPosed integrations should share YSuite host infrastructure when combined.

## YMiniGuard

- Goal remains generic background playback / foreground survival, not a single-app Hook.
- Preserve protected-app selection and background playback behavior, including lock-screen behavior where technically supported.
- Prefer using the real OxygenOS/OPlus system mini-window path when implementing mini-window behavior rather than maintaining a visually similar fake window.
- Notification playback controls remain part of the design: play/pause and previous/next where the target media session supports them; playing notification should not be casually dismissible while paused state may be dismissible when appropriate.
- Avoid changes that fix UI but regress background playback.

## YNFC

- Preserve NFC/HCE access-card workflows and one-tap switching between configured cards.
- Door-card behavior must not break payment HCE routing.
- Root/LSPosed integration must remain compatible with the device NFC stack rather than assuming one vendor implementation.
- NFC service stability is more important than aggressive Hooking; failures must be logged before retrying or reconfiguring the service.

## YTaskManager

- Root is the primary privileged data/control layer; LSPosed API 102 is an enhancement layer.
- Do not add Shizuku or a native daemon as a required architecture dependency.
- Preserve Processes / Resources / Network functionality, process details and controls, CPU/RAM/SWAP/GPU/system information, and app traffic views.
- For rooted traffic accounting, keep the netd/eBPF path with qtaguid fallback where needed.
- High-frequency refresh success should not flood unified logs; failures/timeouts/root problems should be logged.

## YParam

- Preserve per-app parameter overrides such as DPI/resolution/language/location and future parameter simulation capabilities.
- Read and display the app/system default value so users can distinguish default vs override.
- Every override should have a clear restore-default path.
- Prefer selectors, presets and constrained controls over raw text entry wherever values have a known domain.
- Keep the architecture extensible for additional parameters without turning every parameter into a custom one-off screen.

## YFloat

- Preserve FloatLens-style text-selection overlay behavior with rounded selection frame and outside mask.
- Selection visuals should update with the selected region rather than only after selection completes where the platform permits.
- Preserve the stronger colorful border/glow direction rather than reverting to the abandoned Google-style imitation.
- Floating controls must not introduce periodic refresh/flicker merely to keep themselves visible.

## Cross-module rule

When a feature has a project-specific Application, Provider, Service, receiver, database or initialization requirement, YSuite must preserve that behavior through an explicit host contract or manifest integration. Do not rely on accidental behavior from the standalone APK's Application class.
