package com.yagay.suite.core

/**
 * Host capabilities that are granted/connected once at the YSuite level and reused by features.
 *
 * [displayName] is only a locale-neutral fallback for diagnostics or non-UI callers. User-facing
 * surfaces should resolve their own localized resource from the capability enum value.
 *
 * Root and LSPosed are process-level brokers. Overlay, all-files and POST_NOTIFICATIONS are
 * package-level Android grants. Accessibility and notification-listener access are component-level
 * grants owned by one YSuite service each and fanned out through host brokers. File sharing is
 * exposed through one host FileProvider. NFC is a host-reported device capability.
 */
enum class SuiteCapability(val displayName: String) {
    ROOT("Root"),
    LSPOSED("LSPosed"),
    ACCESSIBILITY("Accessibility"),
    OVERLAY("Display over other apps"),
    NOTIFICATIONS("Notifications"),
    NOTIFICATION_LISTENER("Notification access"),
    ALL_FILES("All files access"),
    FILE_SHARE("File sharing"),
    NFC("NFC"),
}
