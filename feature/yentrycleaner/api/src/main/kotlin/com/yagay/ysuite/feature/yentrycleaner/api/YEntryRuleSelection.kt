package com.yagay.ysuite.feature.yentrycleaner.api

/**
 * Resolves a component's explicit, surface-wide and qualifier-specific
 * selection without mutating inherited wildcard rules.
 */
object YEntryRuleSelection {
    fun isSelected(
        exactId: String,
        candidateIds: List<String>,
        selectedRules: Set<String>,
        explicitExceptions: Set<String>,
    ): Boolean =
        exactId !in explicitExceptions && candidateIds.any { it in selectedRules }

    fun isSelected(
        exactId: String,
        wildcardId: String,
        selectedRules: Set<String>,
        explicitExceptions: Set<String>,
    ): Boolean =
        exactId !in explicitExceptions &&
            (exactId in selectedRules || wildcardId in selectedRules)
}
