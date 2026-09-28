package com.yagay.suite.core

/**
 * Stable host contract for YSuite and reusable feature modules.
 *
 * Product/architecture constraints are documented in:
 * - docs/PRODUCT_REQUIREMENTS.md
 * - docs/MODULE_REQUIREMENTS.md
 * - docs/ARCHITECTURE.md
 *
 * Keep this contract small. Feature business logic must stay in feature modules.
 */
object SuiteContract {
    /** Bump when the shared host contract changes in a behaviorally meaningful way. */
    const val REVISION = 1

    /** All unified diagnostics exported by YSuite live under Download/YSuite/. */
    const val LOG_EXPORT_SUBDIR = "YSuite"

    const val FEATURE_STATE_PREFS = "ysuite_features"
    const val CRASH_CONTEXT_PREFS = "ysuite_crash_context"
    const val ACTIVE_FEATURE_KEY = "active_feature"

    const val HOST_MODULE_ID = "suite"
    const val CRASH_MODULE_ID = "crash"
}
