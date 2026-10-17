# YFloat Google CTS adaptive Hook (Native V3 branch)

- **Switch:** YFloat → Screenshot & OCR → Circle engine. Default remains Native Circle V3.
- **Google path:** ActionExecutor → GoogleCtsTrigger → VoiceInteractionManagerService with an explicit YFloat session token.
- **Hook entry:** YFloatModule.onPackageReady installs GoogleV2HookEngine in Google App targets. It does not hook ContextualSearch in system_server.
- **Isolation:** Google V2 interceptors check the active YFloat token/Remote Preferences before acting. Unmarked Home/gesture CTS passes through.
- **Version changes:** Only Google 17.60 (dsxb.y/dsws/dufw, doms/dupt) is a hardcoded profile. Other builds use the same 17.60 structural selection and stable TextSelectionView contracts, resolved per Google ClassLoader. There is no 17.64-specific adapter or version blacklist.
- **Safe failure:** a missing/ambiguous selection signature is never guessed. Google start/remote-lease failures return to Native V3. A CTS session that starts but has no usable selection may still require user cancellation and manual engine switching.
- **Search interception / menu:** Block post-selection dsxb.q(dukp) only for exact 17.60 and targeted contextual/web/Lens search intents for YFloat-owned sessions; do not guess renamed query-result hooks. The Google selection + live text callbacks send authenticated events to GoogleV2UiEngine, which opens the YFloat FloatActionMenu. An installed Hook does not prove callbacks are active; log both cases.
- **Limits:** no device runtime verification is implied by an installed hook or a loaded process; Google may change its data protocol entirely. No guarantee of compatibility for future Google App versions.
- **Diagnostics:** GOOGLE_HOOK_APK, GOOGLE_CTS_ARM, YFloat-GoogleV2 / selection confidence and source.
- **Validation:** unit tests + debug build; real devices must verify 17.60 and dynamically resolved builds separately for Hook invocation, session ownership, search suppression, YFloat menu, and native CTS isolation.
