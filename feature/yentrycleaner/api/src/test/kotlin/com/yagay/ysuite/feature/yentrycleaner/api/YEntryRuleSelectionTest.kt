package com.yagay.ysuite.feature.yentrycleaner.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YEntryRuleSelectionTest {
    private val global = "Browser|*|app.example|Activity"
    private val first = "Browser|example.com|app.example|Activity"
    private val second = "Browser|other.example|app.example|Activity"

    @Test
    fun wildcardSelectionIsIndependentPerDomain() {
        val rules = setOf(global)
        val exceptions = setOf(first)
        assertFalse(YEntryRuleSelection.isSelected(first, global, rules, exceptions))
        assertTrue(YEntryRuleSelection.isSelected(second, global, rules, exceptions))
    }

    @Test
    fun exactRulesWorkWithoutWildcard() {
        assertTrue(YEntryRuleSelection.isSelected(first, global, setOf(first), emptySet()))
        assertFalse(YEntryRuleSelection.isSelected(second, global, setOf(first), emptySet()))
    }

    @Test
    fun explicitExceptionOverridesExactRule() {
        assertFalse(
            YEntryRuleSelection.isSelected(
                first, global, setOf(first, global), setOf(first),
            ),
        )
    }

    @Test
    fun selectionReturnsWhenExceptionIsRemoved() {
        assertTrue(
            YEntryRuleSelection.isSelected(first, global, setOf(global), emptySet()),
        )
    }
}
