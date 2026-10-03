package com.yagay.suite.core

/**
 * Host-owned LSPosed scope guidance.
 *
 * Feature code decides what to do after it is loaded, but YSuite owns the recommendation shown to
 * the user. Keeping these rules in one place prevents every feature from maintaining a different
 * hard-coded "recommended apps" list.
 */
enum class HookScopeMode {
    /** Only the listed system packages/processes are needed. */
    FIXED,

    /** The listed system targets are needed; user apps are optional for app-side enhancements. */
    FIXED_AND_USER,

    /** Scope should mirror the target apps selected inside the feature itself. */
    FEATURE_TARGETS,

    /** No global package is required; the user chooses only apps that need the enhancement. */
    USER_SELECTED,
}

enum class HookScopeDiscovery {
    /** Installed apps that advertise themselves as HTTP/HTTPS browsers. */
    BROWSERS,
}

data class HookScopePolicy(
    val mode: HookScopeMode,
    /** Every installed package in this set should normally be in LSPosed scope. */
    val requiredPackages: Set<String> = emptySet(),
    /** At least one installed package from this group should normally be selected. */
    val requiredAnyOfPackages: Set<String> = emptySet(),
    /** Known useful targets, but never mandatory. */
    val recommendedPackages: Set<String> = emptySet(),
    /** Packages that should not be recommended even if discovery finds them. */
    val excludedPackages: Set<String> = emptySet(),
    val discovery: Set<HookScopeDiscovery> = emptySet(),
)

object HookScopePolicies {
    private val highRiskSystemPackages = setOf(
        "com.android.providers.downloads",
        "com.android.providers.media",
        "com.google.android.gms",
        "com.android.vending",
    )

    fun forFeature(featureId: String): HookScopePolicy = when (featureId) {
        // Resolver filtering happens in system_server. Third-party apps do not need scope.
        "yentrycleaner" -> HookScopePolicy(
            mode = HookScopeMode.FIXED,
            requiredPackages = setOf("android"),
        )

        // Diagnostics already has an in-feature target app list. LSPosed scope should match it.
        "ydiag" -> HookScopePolicy(
            mode = HookScopeMode.FEATURE_TARGETS,
            excludedPackages = highRiskSystemPackages,
        )

        // android = system_server notification path; SystemUI = heads-up/bubbles/full-screen UI.
        // Add user apps only when Toast/Dialog/Popup/Snackbar capture is wanted for those apps.
        "ynotify" -> HookScopePolicy(
            mode = HookScopeMode.FIXED_AND_USER,
            requiredPackages = setOf("android", "com.android.systemui"),
            excludedPackages = setOf("com.google.android.gms", "com.android.vending"),
        )

        // YPower/YParam already maintain per-app profiles. Scope should follow those profiles.
        "ypower", "yparam" -> HookScopePolicy(
            mode = HookScopeMode.FEATURE_TARGETS,
            excludedPackages = highRiskSystemPackages + "com.android.systemui",
        )

        // Keep-alive logic is implemented in system_server only.
        "yminiguard" -> HookScopePolicy(
            mode = HookScopeMode.FIXED,
            requiredPackages = setOf("android"),
        )

        // NFC controller manipulation is explicitly guarded to com.android.nfc.
        "ynfc" -> HookScopePolicy(
            mode = HookScopeMode.FIXED,
            requiredPackages = setOf("com.android.nfc"),
        )

        // Current hook layer is only a system_server integration point.
        "ytaskmanager" -> HookScopePolicy(
            mode = HookScopeMode.FIXED,
            requiredPackages = setOf("android"),
        )

        // Float actions are app-facing. Do not widen scope unless a target app needs the hook.
        "yfloat" -> HookScopePolicy(
            mode = HookScopeMode.USER_SELECTED,
            excludedPackages = highRiskSystemPackages + setOf("android", "com.android.systemui"),
        )

        // Patch callers of DownloadManager; DownloadProvider itself must stay untouched.
        "ydownload" -> HookScopePolicy(
            mode = HookScopeMode.USER_SELECTED,
            excludedPackages = highRiskSystemPackages + setOf("android", "com.android.systemui"),
            discovery = setOf(HookScopeDiscovery.BROWSERS),
        )

        // DocumentsUI receives the internal sort patch. App-side SAF request enhancement is optional.
        "yfiles" -> HookScopePolicy(
            mode = HookScopeMode.FIXED_AND_USER,
            requiredAnyOfPackages = setOf("com.android.documentsui", "com.google.android.documentsui"),
            excludedPackages = highRiskSystemPackages + setOf("android", "com.android.systemui"),
        )

        else -> HookScopePolicy(HookScopeMode.USER_SELECTED),
    }
}
