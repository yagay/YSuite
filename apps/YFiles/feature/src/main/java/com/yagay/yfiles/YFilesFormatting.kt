package com.yagay.yfiles

import java.util.Locale

internal fun formatExtraBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < 1024L) return "$safeBytes B"

    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = safeBytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val pattern = when {
        value >= 100.0 -> "%.0f %s"
        value >= 10.0 -> "%.1f %s"
        else -> "%.2f %s"
    }
    return String.format(Locale.getDefault(), pattern, value, units[unitIndex])
}
