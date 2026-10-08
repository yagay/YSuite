package com.yagay.ysuite.feature.yentrycleaner.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YEntryOpenQualifiersTest {
    @Test fun mimePresetAndWildcardMatch() {
        val q = YEntryOpenQualifiers.qualifiers("application/pdf", "content")
        assertEquals(listOf("application/pdf", "preset:PDF", "application/*", "*"), q)
        val exact = "Open|application/pdf|p|A"
        assertTrue(YEntryRuleSelection.isSelected(
            exact, q.map { "Open|" + it + "|p|A" },
            setOf("Open|preset:PDF|p|A"), emptySet(),
        ))
    }

    @Test fun specialSchemesMatch() {
        assertTrue("preset:MAGNET" in YEntryOpenQualifiers.qualifiers(null, "magnet"))
        assertTrue("preset:GEO" in YEntryOpenQualifiers.qualifiers(null, "geo"))
    }

    @Test fun customExtensionMatchesOpaqueMime() {
        val q = YEntryOpenQualifiers.qualifiers(
            "application/octet-stream", "content", "primary%3ADownload%2Fmemo.xyz",
            mapOf("CUSTOM_1" to YEntryOpenQualifiers.CustomDefinition(emptySet(), setOf("xyz"))),
        )
        assertTrue("preset:CUSTOM_1" in q)
        assertFalse("preset:CUSTOM_2" in q)
    }

    @Test fun explicitExceptionOverridesInheritedPreset() {
        val exact = "Open|application/pdf|p|A"
        assertFalse(YEntryRuleSelection.isSelected(
            exact, listOf(exact, "Open|preset:PDF|p|A", "Open|*|p|A"),
            setOf("Open|preset:PDF|p|A"), setOf(exact),
        ))
    }
}
