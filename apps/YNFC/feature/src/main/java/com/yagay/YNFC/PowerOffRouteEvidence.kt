package com.yagay.YNFC

/**
 * Pure interpretation for read-only NXP routing observations.
 *
 * No off-host configuration is written here. In particular an NXP 'switch-off'
 * routing bit describes an eligible power state, not an installed eSE card, an
 * autonomous custom UID, or proof of an actual reader response after shutdown.
 */
internal object PowerOffRouteEvidence {
    private val uidPattern = Regex("^[0-9A-Fa-f]+$")
    private val configPattern = Regex("^\\s*(DEFAULT_(?:AID|DESFIRE|MIFARE_CLT|FELICA_CLT)_(?:ROUTE|PWR_STATE))\\s*=\\s*(0[xX][0-9A-Fa-f]+|[0-9]+)\\s*$")

    fun normalizedUid(text: String): String? {
        val uid = text.replace(":", "").replace(" ", "").replace("-", "")
        return uid.uppercase().takeIf {
            it.length in setOf(8, 14, 20) && uidPattern.matches(it)
        }
    }

    fun routeFields(raw: String): Map<String, String> = raw.lineSequence().mapNotNull { line ->
        val match = configPattern.matchEntire(line) ?: return@mapNotNull null
        match.groupValues[1] to match.groupValues[2]
    }.toMap()

    /** Distinguish static ODM defaults from observable runtime hints when comparing. */
    fun comparisonFields(raw: String): Map<String, String> {
        val fields = routeFields(raw).toMutableMap()
        val keys = setOf(
            "GLOBAL_hce_access_card_mode",
            "GLOBAL_hce_access_card_sak",
            "GLOBAL_hce_access_card_atqa"
        )
        raw.lineSequence().forEach { line ->
            val key = line.substringBefore('=', "")
            if (key in keys) fields[key] = line.substringAfter('=').trim()
        }
        val lastDh = Regex("isUsingDH[=:](yes|no|true|false)", RegexOption.IGNORE_CASE)
            .findAll(raw).lastOrNull()?.groupValues?.get(1)
        if (lastDh != null) fields["RUNTIME_LAST_DH_MODE"] = lastDh.lowercase()
        return fields
    }

    fun switchOffPowerBit(value: String?): Boolean? {
        if (value.isNullOrBlank()) return null
        val number = if (value.startsWith("0x", ignoreCase = true)) {
            value.substring(2).toIntOrNull(16)
        } else value.toIntOrNull()
        return number?.let { it and 0x02 != 0 }
    }

    fun compareFields(before: Map<String, String>, after: Map<String, String>): String {
        if (before.isEmpty() || after.isEmpty()) return "NOT_COMPARABLE"
        val all = (before.keys + after.keys).toSortedSet()
        val changed = all.filter { before[it] != after[it] }
        return if (changed.isEmpty()) "UNCHANGED" else changed.joinToString(";") {
            "$it:${before[it] ?: "MISSING"}->${after[it] ?: "MISSING"}"
        }
    }
}
