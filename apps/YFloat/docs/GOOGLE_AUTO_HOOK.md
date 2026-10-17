# YFloat Google CTS adaptive Hook (Native V3 branch)

- **Switch:** YFloat → Screenshot & OCR → Circle engine. Default remains Native Circle V3.
- **Google path:** ActionExecutor → GoogleCtsTrigger → VoiceInteractionManagerService with an explicit YFloat session token.
- **Hook entry:** YFloatModule.onPackageReady installs GoogleV2HookEngine in Google App targets. It does not hook ContextualSearch in system_server.
- **Isolation:** Google V2 interceptors check the active YFloat token/Remote Preferences before acting. Unmarked Home/gesture CTS passes through.
- **Version changes:** GoogleHookVersionTracker observes versionName, versionCode and lastUpdateTime. Android restarts the target process after an APK replacement; the fresh Google ClassLoader is resolved on PackageReady. GoogleSelectionAdapter uses fast exact profiles (17.58/17.60/17.64), then bounded structural matching, cached only for the current ClassLoader.
- **Safe failure:** a missing/ambiguous selection signature is never guessed. Google start/remote-lease failures return to Native V3. A CTS session that starts but has no usable selection may still require user cancellation and manual engine switching.
- **Limits:** no device runtime verification is implied by an installed hook or a loaded process; Google may change its data protocol entirely. No guarantee of compatibility for future Google App versions.
- **Diagnostics:** GOOGLE_HOOK_APK, GOOGLE_CTS_ARM, YFloat-GoogleV2 / selection confidence and source.
- **Validation:** unit tests + debug build, then manually test Google 17.64 and later versions for text selection, bounds, region, no-search, and native CTS isolation.
