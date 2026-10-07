package com.yagay.ysuite.runtime

/**
 * Process-local ownership arbitration for LSPosed modules that can be shipped both inside
 * the combined YSuite APK and as standalone APKs.
 *
 * All Xposed module class loaders in one target process share java.lang.System properties.
 * The combined host announces ownership from onModuleLoaded(); standalone modules check the
 * same process-local marker before installing hooks. No global device setting or Root write is
 * required, and a standalone module remains active when YSuite is not actually loaded in the
 * current target process.
 */
object RuntimeOwnerGate {
    private const val SUITE_PACKAGE =
        "com.yagay.ysuite"
    private const val KEY_PREFIX =
        "ysuite.runtime.owner."

    @JvmStatic
    fun announce(
        featureId: String,
        hostPackage: String?,
    ) {
        val host =
            hostPackage
                ?.trim()
                .orEmpty()
        if (host.isBlank()) return

        if (host == SUITE_PACKAGE) {
            System.setProperty(
                key(featureId),
                SUITE_PACKAGE,
            )
        }
    }

    @JvmStatic
    fun shouldRun(
        featureId: String,
        hostPackage: String?,
    ): Boolean {
        val host =
            hostPackage
                ?.trim()
                .orEmpty()
        if (host.isBlank()) return true
        if (host == SUITE_PACKAGE) return true

        return System.getProperty(
            key(featureId),
        ) != SUITE_PACKAGE
    }

    @JvmStatic
    fun isSuiteHost(
        hostPackage: String?,
    ): Boolean =
        hostPackage?.trim() ==
            SUITE_PACKAGE

    private fun key(
        featureId: String,
    ): String =
        KEY_PREFIX +
            featureId
                .trim()
                .lowercase()
                .replace(
                    Regex("[^a-z0-9_.-]"),
                    "_",
                )
}
