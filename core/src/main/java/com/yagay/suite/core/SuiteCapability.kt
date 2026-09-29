package com.yagay.suite.core

/**
 * Host capabilities that are granted/connected once at the YSuite level and reused by features.
 *
 * Root and LSPosed are process-level brokers. Overlay and POST_NOTIFICATIONS are package-level
 * Android grants. Accessibility and notification-listener access are component-level grants owned
 * by one YSuite service each and fanned out through host brokers.
 */
enum class SuiteCapability(val displayName: String) {
    ROOT("Root"),
    LSPOSED("LSPosed"),
    ACCESSIBILITY("无障碍"),
    OVERLAY("悬浮窗"),
    NOTIFICATIONS("通知"),
    NOTIFICATION_LISTENER("通知监听"),
}
