# YFloat Google CTS adaptive Hook (Native V3 branch)

- **Switch:** YFloat → Screenshot & OCR → Circle engine. Default remains Native Circle V3.
- **Google path:** ActionExecutor → GoogleCtsTrigger → VoiceInteractionManagerService with an explicit YFloat session token.
- **Hook entry:** YFloatModule.onPackageReady installs GoogleV2HookEngine in Google App targets. It does not hook ContextualSearch in system_server.
- **Isolation:** Google V2 interceptors check the active YFloat token/Remote Preferences before acting. Unmarked Home/gesture CTS passes through.
- **Version changes:** GoogleHookVersionTracker observes versionName, versionCode and lastUpdateTime. Android restarts the target process after an APK replacement; the fresh Google ClassLoader is resolved on PackageReady. GoogleSelectionAdapter first resolves the 17.60 dsxb.y/dsws/dufw contract (using selection-specific validation). GoogleTextSelectionLiveHook prioritizes the 17.60 doms/dupt word-list and final-selection update paths before 17.58 legacy handling and stable-view structural heuristics. Google 17.58 remains a legacy fallback, 17.64 is disabled pending separate validation, and unknown builds use bounded selection-shape discovery cached for the current ClassLoader.
- **Safe failure:** a missing/ambiguous selection signature is never guessed. Google start/remote-lease failures return to Native V3. A CTS session that starts but has no usable selection may still require user cancellation and manual engine switching.
- **Known regressions:** Google 17.60 is the selected development baseline. 17.64 stays disabled; this is a policy choice, not a guarantee that all 17.60 interactions work. The trigger routes only 17.64 to YFloat Native V3.
- **Limits:** no device runtime verification is implied by an installed hook or a loaded process; Google may change its data protocol entirely. No guarantee of compatibility for future Google App versions.
- **Diagnostics:** GOOGLE_HOOK_APK, GOOGLE_CTS_ARM, YFloat-GoogleV2 / selection confidence and source.
- **Validation:** unit tests + debug build, then manually test **Google 17.60.16.ve** for text selection, bounds, region, no-search, and native CTS isolation. Legacy 17.58 remains available; 17.64 stays disabled.
