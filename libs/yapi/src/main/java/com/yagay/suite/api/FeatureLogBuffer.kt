package com.yagay.suite.api

import java.util.ArrayDeque

/**
 * Thread-safe, bounded in-memory diagnostic history for modules needing a live on-screen log.
 * Persisted host logs are still written via FeatureServices; this is only a view buffer.
 */
class FeatureLogBuffer @JvmOverloads constructor(private val maxLines: Int = 1000) {
    init { require(maxLines > 0) { "maxLines must be positive" } }

    private val lines = ArrayDeque<String>()

    @Synchronized
    fun append(line: String) {
        while (lines.size >= maxLines) lines.removeFirst()
        lines.addLast(line)
    }

    @Synchronized
    fun readAll(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
