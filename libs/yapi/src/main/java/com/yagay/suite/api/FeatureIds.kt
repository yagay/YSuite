package com.yagay.suite.api

import java.util.Locale

/** One normalization rule for feature IDs used by registries, settings, IPC and runtime ownership. */
object FeatureIds {
    @JvmStatic
    fun normalize(value: String?): String {
        val cleaned = value.orEmpty()
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
        return cleaned.ifEmpty { "unknown" }
    }
}
