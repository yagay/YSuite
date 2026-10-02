package com.yagay.suite.api

/**
 * Collision-safe IDs for values that become global when several Features share one APK.
 *
 * Feature code should use these helpers instead of hard-coding YSuite package names or generic
 * strings such as "progress", "settings", "worker" or "provider".
 */
data class HostIds(
    private val hostPackageName: String,
    private val featureId: String,
) {
    private fun local(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .ifEmpty { "default" }

    fun preference(localName: String): String = "$featureId.${local(localName)}"

    fun database(localName: String): String = "${featureId}_${local(localName)}.db"

    fun work(localName: String): String = "$featureId:${local(localName)}"

    fun notificationChannel(localName: String): String = "$featureId.${local(localName)}"

    fun broadcastAction(localName: String): String =
        "$hostPackageName.$featureId.action.${local(localName).uppercase()}"

    fun providerAuthority(localName: String): String =
        "$hostPackageName.$featureId.${local(localName).lowercase()}"

    fun storageDirectory(localName: String): String = "$featureId/${local(localName)}"
}

/** IDs are derived from the actual host package, so the same Feature works in YSuite and standalone. */
val FeatureHost.ids: HostIds
    get() = HostIds(hostPackageName = hostPackageName, featureId = featureId)
