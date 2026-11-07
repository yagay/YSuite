package com.yagay.YNFC

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.*
import org.junit.Test

class NfcProfileFileTest {
    private fun parse(text: String): CardModel =
        NfcProfileFile.importProfile(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)))

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
