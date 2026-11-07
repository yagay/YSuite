package com.yagay.YNFC

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.*
import org.junit.Test

class NfcProfileFileTest {
    private fun parse(text: String): CardModel =
        NfcProfileFile.importProfile(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))

    @Test fun savedCardAutomaticallyGeneratesValidJson() {
        val original = CardModel("Own test card", "c1:b0:bc:1b", "08", "00 04")
        val bytes = NfcProfileFile.encodeCard(original)
        val json = String(bytes, StandardCharsets.UTF_8)
        assertTrue(json.contains("\\"type\\":\\"NFC_A\\""))
        val restored = NfcProfileFile.importProfile(ByteArrayInputStream(bytes))
        assertEquals("Own test card", restored.name)
        assertEquals("C1B0BC1B", restored.uid)
        assertEquals("0004", restored.atqa)
        assertEquals("08", restored.sak)
    }

    @Test fun invalidSavedCardCannotGenerateAProfile() {
        assertThrows(IllegalArgumentException::class.java) {
            NfcProfileFile.encodeCard(CardModel("Own test", "123456", "08", "0004"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NfcProfileFile.encodeCard(CardModel("Own test", "C1B0BC1B", "0800", "0004"))
        }
    }

    @Test fun repeatedAutomaticGenerationUsesTheNewSelectedCard() {
        val first = NfcProfileFile.encodeCard(CardModel("First", "01020304", "08", "0004"))
        val second = NfcProfileFile.encodeCard(CardModel("Second", "AABBCCDD", "08", "0004"))
        assertEquals("01020304",
            NfcProfileFile.importProfile(ByteArrayInputStream(first)).uid)
        assertEquals("AABBCCDD",
            NfcProfileFile.importProfile(ByteArrayInputStream(second)).uid)
    }

    @Test fun validProfileImportsAndNormalizes() {
        val card = parse("""{"type":"NFC_A","name":"Test","uid":"c1:b0:bc:1b","atqa":"00 04","sak":"08"}""")
        assertEquals("Test", card.name)
        assertEquals("C1B0BC1B", card.uid)
        assertEquals("0004", card.atqa)
        assertEquals("08", card.sak)
    }

    @Test fun missingOrUnsupportedFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"type":"MIFARE_CLASSIC","uid":"01020304","atqa":"0004","sak":"08"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"type":"NFC_A","uid":"01020304","atqa":"0004"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"type":"NFC_A","uid":"010203","atqa":"0004","sak":"08"}""")
        }
    }

    @Test fun rejectsOversizedFile() {
        val oversized = ByteArray(4097) { 'x'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            NfcProfileFile.importProfile(ByteArrayInputStream(oversized))
        }
    }

    @Test fun supportsSevenAndTenByteUids() {
        assertEquals("04112233445566",
            parse("""{"type":"NFC_A","uid":"04112233445566","atqa":"0004","sak":"08"}""").uid)
        assertEquals("0102030405060708090A",
            parse("""{"type":"NFC_A","uid":"0102030405060708090A","atqa":"0004","sak":"08"}""").uid)
    }

    @Test fun noNameUsesDefault() {
        assertEquals("Imported NFC-A",
            parse("""{"type":"NFC_A","uid":"01020304","atqa":"0004","sak":"08"}""").name)
    }
}
